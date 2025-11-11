package core;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static core.YUVUtils.extractLumaBlock;
import static core.YUVUtils.getLuma;

public final class MotionVector implements java.io.Serializable {
    public final int dx, dy; // w pikselach (całopiks.)
    public MotionVector(int dx, int dy) { this.dx = dx; this.dy = dy; }
    // ---- ME: pełny SAD na Y (16x16), całopiksel ----
    public static MotionVector searchMV_Luma_SAD(BufferedImage cur, BufferedImage ref, int x, int y, int R) {
        int bestDx = 0, bestDy = 0;
        long bestCost = Long.MAX_VALUE;

        int[] curY = extractLumaBlock(cur, x, y, 16, 16); // prefetch

        for (int dy = -R; dy <= R; dy++) {
            int ry = y + dy;
            if (ry < 0 || ry + 15 >= ref.getHeight()) continue;
            for (int dx = -R; dx <= R; dx++) {
                int rx = x + dx;
                if (rx < 0 || rx + 15 >= ref.getWidth()) continue;

                long sad = 0;
                int[] refY = extractLumaBlock(ref, rx, ry, 16, 16);

                for (int i = 0; i < 256; i++) {
                    int d = curY[i] - refY[i];
                    sad += (d < 0 ? -d : d);
                    if (sad >= bestCost) break; // early break
                }
                if (sad < bestCost) {
                    bestCost = sad;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        return new MotionVector(bestDx, bestDy);
    }

    // ---- Zapis wektora ruchu: stałobitowy z offsetem ----
    public static void writeMV(Bitstream bs, MotionVector mv, int bits) throws IOException {
        int range = 1 << (bits - 1);   // np. 64 dla 7 bitów
        int ox = mv.dx + range;
        int oy = mv.dy + range;
        bs.writeBits(bits, ox);
        bs.writeBits(bits, oy);
    }
}

