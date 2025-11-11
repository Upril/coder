package transforms;

import core.Bitstream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class RLEHuffmanEncoder {

    private RLEHuffmanEncoder() {}

    // -------- Public entry point (drop-in) --------
    public static void encode(Bitstream bs, int[] data, int offset) throws IOException {
        if (data.length == 64) {
            // single 8x8 block (chrominance)
            encodeBlock8x8(bs, data, 0, 8, 0);
        } else if (data.length == 256) {
            // 16x16 luminance => 4x (8x8) in raster order: (0,0),(8,0),(0,8),(8,8)
            int prevDC = 0;
            prevDC = encodeBlock8x8(bs, data, /*base*/0, 16, prevDC);       // top-left
            prevDC = encodeBlock8x8(bs, data, /*base*/8, 16, prevDC);       // top-right
            prevDC = encodeBlock8x8(bs, data, /*base*/8*16, 16, prevDC);    // bottom-left
            encodeBlock8x8(bs, data, /*base*/8*16 + 8, 16, prevDC);         // bottom-right
        } else {
            throw new IllegalArgumentException("Unexpected block length: " + data.length);
        }
    }

    // -------- Encoding of a single 8x8 block --------
    // data: flattened parent array; base: starting index in that array; stride: parent row stride
    private static int encodeBlock8x8(Bitstream bs, int[] data, int base, int stride, int prevDC) throws IOException {
        int[] zz = ZIGZAG_INDEX;
        int dc = data[base + (0)*stride + 0]; // [0,0] before zigzag
        // DC diff (like JPEG) to improve stats
        int diff = dc - prevDC;
        int dcCat = category(diff);
        // Huffman code for DC category
        Codeword dcCw = DC_TABLE.get(dcCat);
        writeCode(bs, dcCw);
        // amplitude bits for DC
        if (dcCat > 0) {
            int amp = amplitudeBits(diff, dcCat);
            writeBitsMSBFirst(bs, dcCat, amp);
        }

        // AC coefficients with zero-RLE + Huffman
        int run = 0;
        for (int k = 1; k < 64; k++) {
            int pos = zz[k];
            int y = pos >> 3;
            int x = pos & 7;
            int v = data[base + y*stride + x];

            if (v == 0) {
                run++;
                continue;
            }

            // Zero Run Length: emit ZRL for each 16 zeros chunk
            while (run >= 16) {
                writeCode(bs, AC_ZRL);
                run -= 16;
            }

            int sz = category(v);
            Codeword acCw = AC_TABLE.get(key(run, sz));
            if (acCw == null) acCw = AC_FALLBACK; // should be rare
            writeCode(bs, acCw);

            int amp = amplitudeBits(v, sz);
            writeBitsMSBFirst(bs, sz, amp);
            run = 0;
        }

        // End-of-block if any trailing zeros
        if (run > 0) {
            writeCode(bs, AC_EOB);
        }
        return dc;
    }

    // -------- Helper: category & amplitude (JPEG-like) --------
    private static int category(int v) {
        int a = Math.abs(v);
        int s = 0;
        while (a != 0) { a >>= 1; s++; }
        return s; // 0 if v==0
    }

    // JPEG amplitude coding: negatives mapped to (2^s - 1) + v
    private static int amplitudeBits(int v, int s) {
        if (s == 0) return 0;
        if (v >= 0) return v;
        return ((1 << s) - 1) + v; // v is negative
    }

    // -------- Bit writing (your Bitstream is LSB-first) --------
    // We reverse bit order so logical codes are MSB-first.
    private static void writeCode(Bitstream bs, Codeword cw) throws IOException {
        writeBitsMSBFirst(bs, cw.len, cw.bits);
    }

    private static void writeBitsMSBFirst(Bitstream bs, int n, int value) throws IOException {
        int rev = 0;
        for (int i = 0; i < n; i++) {
            // take MSB-first from 'value'
            int bit = (value >> (n - 1 - i)) & 1;
            rev |= (bit << i); // place as LSB-first for Bitstream
        }
        bs.writeBits(n, rev);
    }

    // -------- ZigZag order for 8x8 --------
    private static final int[] ZIGZAG_INDEX = {
            0,  1,  8, 16,  9,  2,  3, 10,
            17, 24, 32, 25, 18, 11,  4,  5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13,  6,  7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63
    };

    // -------- Codeword + keys --------
    private static final class Codeword {
        final int bits; // MSB-first logical code (we reverse on write)
        final int len;  // number of bits
        Codeword(int bits, int len) { this.bits = bits; this.len = len; }
    }

    private static final class ACKey {
        final int run, size; // EOB:(0,0), ZRL:(15,0)
        ACKey(int run, int size) { this.run = run; this.size = size; }
        @Override public boolean equals(Object o){ if(!(o instanceof ACKey k)) return false; return run==k.run && size==k.size; }
        @Override public int hashCode(){ return (run<<5) ^ size; }
    }
    private static ACKey key(int run, int size){ return new ACKey(run, size); }

    // -------- Static Huffman tables (built from heuristic frequencies) --------
    private static final Map<Integer, Codeword> DC_TABLE = new HashMap<>();
    private static final Map<ACKey, Codeword> AC_TABLE = new HashMap<>();
    private static Codeword AC_EOB;
    private static Codeword AC_ZRL;
    private static Codeword AC_FALLBACK;

    static {
        buildHuffmanTables();
    }

    private static void buildHuffmanTables() {
        // --- DC: category 0..11 ---
        Map<Integer,Integer> dcFreq = new HashMap<>();
        int[] dcBase = { 5000, 3000, 1500, 800, 400, 200, 100, 50, 25, 12, 6, 3 };
        for (int s=0; s<dcBase.length; s++) dcFreq.put(s, dcBase[s]);
        Map<Integer,Codeword> dcCodes = buildCodesDC(dcFreq);
        DC_TABLE.putAll(dcCodes);

        // --- AC: (run 0..15, size 0..10); (0,0)=>EOB, (15,0)=>ZRL ---
        Map<ACKey,Integer> acFreq = new HashMap<>();
        // Base frequency per size (falls with size)
        int[] base = { 8000, 4000, 2000, 1000, 500, 250, 120, 60, 30, 15, 8 }; // index 0 used for EOB/ZRL
        // EOB very frequent
        acFreq.put(key(0,0), 8000);
        // ZRL occasionally
        acFreq.put(key(15,0), 80);

        // Fill typical pairs — higher run penalized
        for (int run=0; run<=15; run++) {
            for (int sz=1; sz<=10; sz++) {
                int f = Math.max(1, base[sz] / (1 + run));
                acFreq.put(key(run, sz), f);
            }
        }
        // Give a boost to very common pairs
        bump(acFreq, key(0,1), 6000);
        bump(acFreq, key(0,2), 3000);
        bump(acFreq, key(1,1), 2500);
        bump(acFreq, key(2,1), 1400);
        bump(acFreq, key(0,3), 1500);

        Map<ACKey,Codeword> acCodes = buildCodesAC(acFreq);
        AC_TABLE.putAll(acCodes);
        AC_EOB = AC_TABLE.get(key(0,0));
        AC_ZRL = AC_TABLE.get(key(15,0));
        // Fallback (shouldn’t be used, but keep safe)
        AC_FALLBACK = new Codeword(0b11111111, 8);
    }

    private static void bump(Map<ACKey,Integer> m, ACKey k, int add) {
        m.put(k, m.getOrDefault(k, 1) + add);
    }

    // -------- Huffman builders --------
    private static Map<Integer,Codeword> buildCodesDC(Map<Integer,Integer> freq) {
        // standard Huffman
        PriorityQueue<Node<Integer>> pq = new PriorityQueue<>();
        for (var e: freq.entrySet()) pq.add(new Node<>(e.getKey(), e.getValue(), null, null));
        if (pq.size()==1) pq.add(new Node<>(null, 1, null, null)); // edge case
        while (pq.size() > 1) {
            Node<Integer> a = pq.poll(), b = pq.poll();
            pq.add(new Node<>(null, a.freq + b.freq, a, b));
        }
        Node<Integer> root = pq.poll();
        Map<Integer,Codeword> map = new HashMap<>();
        buildCodeMap(root, 0, 0, map);
        return map;
    }

    private static Map<ACKey,Codeword> buildCodesAC(Map<ACKey,Integer> freq) {
        PriorityQueue<Node<ACKey>> pq = new PriorityQueue<>();
        for (var e: freq.entrySet()) pq.add(new Node<>(e.getKey(), e.getValue(), null, null));
        if (pq.size()==1) pq.add(new Node<>(null, 1, null, null));
        while (pq.size() > 1) {
            Node<ACKey> a = pq.poll(), b = pq.poll();
            pq.add(new Node<>(null, a.freq + b.freq, a, b));
        }
        Node<ACKey> root = pq.poll();
        Map<ACKey,Codeword> map = new HashMap<>();
        buildCodeMap(root, 0, 0, map);
        return map;
    }

    private static <T> void buildCodeMap(Node<T> n, int bits, int len, Map<T,Codeword> out) {
        if (n == null) return;
        if (n.leaf()) {
            // Guarantee at least 1 bit
            out.put(n.sym, new Codeword(len == 0 ? 0 : bits, Math.max(1, len)));
            return;
        }
        // left = append 0
        buildCodeMap(n.l, bits << 1, len + 1, out);
        // right = append 1
        buildCodeMap(n.r, (bits << 1) | 1, len + 1, out);
    }

    private static final class Node<T> implements Comparable<Node<T>> {
        final T sym;
        final int freq;
        final Node<T> l, r;
        Node(T sym, int freq, Node<T> l, Node<T> r){ this.sym=sym; this.freq=freq; this.l=l; this.r=r; }
        boolean leaf(){ return l==null && r==null; }
        @Override public int compareTo(Node<T> o){ return Integer.compare(this.freq, o.freq); }
    }
}
