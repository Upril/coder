package core;

import transforms.RLEHuffmanEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static core.MotionVector.*;
import static core.YUVUtils.*;

public class P_VOP extends VideoObjectPlane implements Serializable {
    private final BufferedImage curr;        // bieżąca klatka (RGB jak u Ciebie)
    private final BufferedImage refRecon;    // ZREKONSTRUOWANA referencja (RGB)
    private final int searchRange;           // np. 32 piksele
    private BufferedImage reconOut;

    public P_VOP(BufferedImage curr, BufferedImage refRecon, int searchRange) {
        super(curr);
        this.curr = curr;
        this.refRecon = refRecon;
        this.searchRange = searchRange;
        header = new VOPHeader(/*typ P*/1, /*time*/1, /*isIntra?*/false, /*qp*/10,
                curr.getWidth(), curr.getHeight());
    }

//    @Override
//    public void encode(Bitstream bitstream) {
//        try {
//            header.encodeHeader(bitstream);
//
//            for (Macroblock macroblock : macroblocks) {
//                macroblock.applyDCTAndQuantization();
//
//                int[] luminance = macroblock.getLuminance();
//                int[] chrominanceU = macroblock.getChrominanceU();
//                int[] chrominanceV = macroblock.getChrominanceV();
//
//                final int OFFSET = 128;
//                encodeWithRLE(bitstream, luminance, OFFSET);
//                encodeWithRLE(bitstream, chrominanceU, OFFSET);
//                encodeWithRLE(bitstream, chrominanceV, OFFSET);
//            }
//
//        } catch (IOException e) {
//            throw new RuntimeException("Error encoding P-VOP", e);
//        }
//    }
//public void encode(Bitstream bitstream) {
//    try {
//        header.encodeHeader(bitstream);
//
//        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//        List<Future<int[][]>> results = new ArrayList<>(macroblocks.size());
//
//        // Submit tasks for parallel DCT and Quantization
//        for (Macroblock macroblock : macroblocks) {
//            results.add(executor.submit(() -> {
//                macroblock.applyDCTAndQuantization();
//                return new int[][]{
//                        macroblock.getLuminance(),
//                        macroblock.getChrominanceU(),
//                        macroblock.getChrominanceV()
//                };
//            }));
//        }
//        final int OFFSET = 128;
//        // Collect results and write to bitstream sequentially
//        for (int i = 0; i < macroblocks.size(); i++) {
//            int[][] blockData = results.get(i).get();
//            encodeWithRLE(bitstream, blockData[0], OFFSET);
//            encodeWithRLE(bitstream, blockData[1], OFFSET);
//            encodeWithRLE(bitstream, blockData[2], OFFSET);
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(20, TimeUnit.MINUTES);
//
//    } catch (IOException | InterruptedException | ExecutionException e) {
//        throw new RuntimeException("Error encoding I-VOP", e);
//    }
//}
    // ME proba 1
//    @Override
//    public void encode(Bitstream bs) {
//        try {
//            header.encodeHeader(bs);
//
//            // Rekonstrukcja P-klatki do następnego refa:
//            BufferedImage reconOut = new BufferedImage(curr.getWidth(), curr.getHeight(), BufferedImage.TYPE_INT_RGB);
//
//            for (Macroblock mb : macroblocks) {
//                int mbX = mb.getStartX();
//                int mbY = mb.getStartY();
//
//                // 1) ME: znajdź MV na Y (SAD, pełne przeszukiwanie, całopiksel)
//                MotionVector mv = searchMV_Luma_SAD(curr, refRecon, mbX, mbY, searchRange);
//
//                // 2) MC: wytnij pred 16x16 z referencji
//                int[] predY   = extractLumaBlock(refRecon, mbX + mv.dx, mbY + mv.dy, 16, 16);
//                int[] predU   = extractChromaBlock420_U(refRecon, mbX + mv.dx, mbY + mv.dy); // 8x8
//                int[] predV   = extractChromaBlock420_V(refRecon, mbX + mv.dx, mbY + mv.dy);
//
//                // 3) Curr bloki (tak jak w Macroblock.extractBlock) — bez tworzenia Color:
//                int[] currY   = extractLumaBlock(curr, mbX, mbY, 16, 16);
//                int[] currU   = extractChromaBlock420_U(curr, mbX, mbY);
//                int[] currV   = extractChromaBlock420_V(curr, mbX, mbY);
//
//                // 4) Residua
//                int[] resY = sub(currY, predY);
//                int[] resU = sub(currU, predU);
//                int[] resV = sub(currV, predV);
//
//                // 5) DCT + Q
//                resY = mb.applyDCTAndQuantizationToBlock(resY, 16, 16);   // udostępnij tę metodę jako public/protected
//                resU = mb.applyDCTAndQuantizationToBlock(resU, 8, 8);
//                resV = mb.applyDCTAndQuantizationToBlock(resV, 8, 8);
//
//                // 6) Zapisz MV (stałobitowo, np. 7+7 z offsetem 64 dla zakresu ±64)
//                writeMV(bs, mv, /*bits*/7);
//
//                // 7) Entropia (jak w I-VOP)
//                final int OFFSET = 128;
//                RLEHuffmanEncoder.encode(bs, resY, OFFSET);
//                RLEHuffmanEncoder.encode(bs, resU, OFFSET);
//                RLEHuffmanEncoder.encode(bs, resV, OFFSET);
//
//                // 8) REKONSTRUKCJA do refa: IQ + IDCT + add pred
//                int[] recY = inverseTransform(resY, 16, 16);
//                int[] recU = inverseTransform(resU, 8, 8);
//                int[] recV = inverseTransform(resV, 8, 8);
//                recY = add(recY, predY);
//                recU = add(recU, predU);
//                recV = add(recV, predV);
//
//                for (int i = 0; i < recY.length; i++) recY[i] = clamp(recY[i], 0, 255);
//                for (int i = 0; i < recU.length; i++) recU[i] = clamp(recU[i], 0, 255);
//                for (int i = 0; i < recV.length; i++) recV[i] = clamp(recV[i], 0, 255);
//
//                // 9) Złóż do reconOut (YUV→RGB; tu na szybko prosty przelicznik)
//                blitYUV420BlockToRGB(reconOut, mbX, mbY, recY, recU, recV);
//            }
//
//            // (opcjonalnie) zwróć/udostępnij reconOut na zewnątrz, by stał się refem dla następnej klatki
//
//        } catch (IOException e) {
//            throw new RuntimeException("Error encoding P-VOP", e);
//        }
//    }
@Override
public void encode(Bitstream bs) {
    try {
        header.encodeHeader(bs);

        final int OFFSET = 128;
        final long SKIP_THRESHOLD = 1500;

        // 1. Konwersja całych klatek na tablice int[][] (Tylko raz!)
        // To jest klucz do wydajności.
        int[][] lumaCur = extractLumaPlane(curr);
        int[][] lumaRef = extractLumaPlane(refRecon);

        int mbCols = (curr.getWidth() + 15) / 16;
        int mbRows = (curr.getHeight() + 15) / 16;
        int totalMacroblocks = mbCols * mbRows;

        EncodedMacroblock[] resultsArray = new EncodedMacroblock[totalMacroblocks];
        java.util.concurrent.atomic.AtomicInteger nextMbIndex = new java.util.concurrent.atomic.AtomicInteger(0);

        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        List<Callable<Void>> workers = new ArrayList<>(cores);

        for (int i = 0; i < cores; i++) {
            workers.add(() -> {
                while (true) {
                    int idx = nextMbIndex.getAndIncrement();
                    if (idx >= totalMacroblocks) break;

                    int mbX = (idx % mbCols) * 16;
                    int mbY = (idx / mbCols) * 16;

                    Macroblock mb = macroblocks.get(idx);

                    // --- 1. ME: Używamy tablic int[][] ---
                    // searchRange w pikselach (np. 8). Funkcja zwraca jednostki x2.
                    MotionVector mv = MotionVector.searchMV_HalfPel(lumaCur, lumaRef, mbX, mbY, searchRange);
                    mb.setMotionVector(mv);

                    // --- 2. MC: Pobieranie predykcji ---
                    // refX2, refY2 -> współrzędne absolutne w systemie półpikselowym
                    int refX2 = (mbX * 2) + mv.dx;
                    int refY2 = (mbY * 2) + mv.dy;

                    // Predykcja Luma (Interpolowana z tablicy ref)
                    int[] predY = extractLumaBlockHalfPel(lumaRef, refX2, refY2, 16, 16);

                    int[] currY = new int[256];
                    for(int y=0; y<16; y++)
                        for(int x=0; x<16; x++)
                            currY[y*16+x] = lumaCur[clamp(mbY+y,0,curr.getHeight()-1)][clamp(mbX+x,0,curr.getWidth()-1)];

                    int[] resY = sub(currY, predY);

                    // Chroma (Nearest Neighbor)
                    int intMvX = mv.dx / 2; // powrót do pełnych pikseli
                    int intMvY = mv.dy / 2;
                    int[] predU = extractChromaBlock420_U(refRecon, mbX + intMvX, mbY + intMvY);
                    int[] predV = extractChromaBlock420_V(refRecon, mbX + intMvX, mbY + intMvY);

                    // --- 3. SKIP Check ---
                    long energy = 0;
                    for (int val : resY) energy += Math.abs(val);
                    boolean isSkip = (mv.dx == 0 && mv.dy == 0 && energy < SKIP_THRESHOLD);

                    EncodedMacroblock out = new EncodedMacroblock();
                    out.startX = mbX;
                    out.startY = mbY;
                    out.mv = mv;
                    out.isSkip = isSkip;

                    if (isSkip) {
                        out.recY = predY;
                        out.recU = predU;
                        out.recV = predV;
                    } else {
                        // Pobieramy chroma z oryginału
                        int[] currU = extractChromaBlock420_U(curr, mbX, mbY);
                        int[] currV = extractChromaBlock420_V(curr, mbX, mbY);
                        int[] resU = sub(currU, predU);
                        int[] resV = sub(currV, predV);

                        mb.setQuantizationMatrix(Macroblock.FLAT_QUANTIZATION_MATRIX);
                        out.qY = mb.applyDCTAndQuantizationToBlock(resY, 16, 16);
                        out.qU = mb.applyDCTAndQuantizationToBlock(resU, 8, 8);
                        out.qV = mb.applyDCTAndQuantizationToBlock(resV, 8, 8);

                        int[] iqY = inverseTransform(out.qY, 16, 16, Macroblock.FLAT_QUANTIZATION_MATRIX);
                        int[] iqU = inverseTransform(out.qU, 8, 8, Macroblock.FLAT_QUANTIZATION_MATRIX);
                        int[] iqV = inverseTransform(out.qV, 8, 8, Macroblock.FLAT_QUANTIZATION_MATRIX);

                        out.recY = add(iqY, predY);
                        out.recU = add(iqU, predU);
                        out.recV = add(iqV, predV);
                    }

                    // Clamp
                    for(int k=0; k<256; k++) out.recY[k] = clamp(out.recY[k], 0, 255);
                    for(int k=0; k<64; k++) {
                        out.recU[k] = clamp(out.recU[k], 0, 255);
                        out.recV[k] = clamp(out.recV[k], 0, 255);
                    }

                    resultsArray[idx] = out;
                }
                return null;
            });
        }

        executor.invokeAll(workers);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        // --- ZAPIS ---
        BufferedImage reconOut = new BufferedImage(curr.getWidth(), curr.getHeight(), BufferedImage.TYPE_INT_RGB);
        MotionVector prevMV = new MotionVector(0,0);

        for (int i = 0; i < totalMacroblocks; i++) {
            EncodedMacroblock em = resultsArray[i];
            if (i % mbCols == 0) prevMV = new MotionVector(0,0);

            if (em.isSkip) {
                bs.writeBits(1, 1);
                prevMV = new MotionVector(0,0);
            } else {
                bs.writeBits(1, 0);

                int diffX = em.mv.dx - prevMV.dx;
                int diffY = em.mv.dy - prevMV.dy;
                writeMV(bs, new MotionVector(diffX, diffY), 9);

                prevMV = em.mv;

                RLEHuffmanEncoder.encode(bs, em.qY, OFFSET);
                RLEHuffmanEncoder.encode(bs, em.qU, OFFSET);
                RLEHuffmanEncoder.encode(bs, em.qV, OFFSET);
            }
            blitYUV420BlockToRGB(reconOut, em.startX, em.startY, em.recY, em.recU, em.recV);
        }

        this.reconOut = reconOut;

    } catch (Exception e) {
        throw new RuntimeException("Error encoding P-VOP", e);
    }
}

    public BufferedImage getReconOut() {
        return reconOut;
    }

    private static final class GroupResult {
        final int startIndex;
        final byte[] encodedBytes;
        final List<EncodedMacroblock> reconBlocks;
        GroupResult(int startIndex, byte[] encodedBytes, List<EncodedMacroblock> reconBlocks) {
            this.startIndex = startIndex;
            this.encodedBytes = encodedBytes;
            this.reconBlocks = reconBlocks;
        }
    }
    // pomocnicza klasa wewnętrzna
    private static final class EncodedMacroblock {
        int startX, startY;
        int[] qY, qU, qV;
        MotionVector mv;
        boolean isSkip;
        int[] recY, recU, recV;
    }



}
