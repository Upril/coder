package core;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static core.YUVUtils.extractLumaBlock;
import static core.YUVUtils.getLuma;

public final class MotionVector implements java.io.Serializable {
    public final int dx, dy; // w pikselach (całopiks.)
    public MotionVector(int dx, int dy) { this.dx = dx; this.dy = dy; }
    // ---- ME: pełny SAD na Y (16x16), całopiksel ----
//    public static MotionVector searchMV_Luma_SAD(BufferedImage cur, BufferedImage ref, int x, int y, int R) {
//        int bestDx = 0, bestDy = 0;
//        long bestCost = Long.MAX_VALUE;
//
//        int[] curY = extractLumaBlock(cur, x, y, 16, 16); // prefetch
//
//        for (int dy = -R; dy <= R; dy++) {
//            int ry = y + dy;
//            if (ry < 0 || ry + 15 >= ref.getHeight()) continue;
//            for (int dx = -R; dx <= R; dx++) {
//                int rx = x + dx;
//                if (rx < 0 || rx + 15 >= ref.getWidth()) continue;
//
//                long sad = 0;
//                int[] refY = extractLumaBlock(ref, rx, ry, 16, 16);
//
//                for (int i = 0; i < 256; i++) {
//                    int d = curY[i] - refY[i];
//                    sad += (d < 0 ? -d : d);
//                    if (sad >= bestCost) break; // early break
//                }
//                if (sad < bestCost) {
//                    bestCost = sad;
//                    bestDx = dx;
//                    bestDy = dy;
//                }
//            }
//        }
//        return new MotionVector(bestDx, bestDy);
//    }
/**
 * Wersja 2 - małe optymalizacje
 * **/
//    public static MotionVector searchMV_Luma_SAD(int[][] curY, int[][] refY, int blockX, int blockY, int R) {
//        int height = curY.length;
//        int width = curY[0].length;
//
//        // Prefetch: bezpieczne pobranie bloku bieżącego (z obsługą krawędzi)
//        int[] currentBlockFlat = new int[256];
//
//        for (int i = 0; i < 16; i++) {
//            // Clamp Y: nie czytaj poniżej ostatniego wiersza
//            int srcY = Math.min(blockY + i, height - 1);
//
//            // Optymalizacja: jeśli cały wiersz mieści się w szerokości, użyj arraycopy
//            if (blockX + 16 <= width) {
//                System.arraycopy(curY[srcY], blockX, currentBlockFlat, i * 16, 16);
//            } else {
//                // Fallback dla prawej krawędzi: kopiowanie po pikselu z clampowaniem
//                for (int j = 0; j < 16; j++) {
//                    int srcX = Math.min(blockX + j, width - 1);
//                    currentBlockFlat[i * 16 + j] = curY[srcY][srcX];
//                }
//            }
//        }
//
//        long bestCost = Long.MAX_VALUE;
//        int bestDx = 0;
//        int bestDy = 0;
//
//
//        // Sprawdzamy, czy blok pasuje w miejscu (0,0).
//        if (blockY < height && blockX < width) {
//            long sad00 = calculateSAD_Safe(currentBlockFlat, refY, blockX, blockY, width, height, Long.MAX_VALUE);
//
//            // Próg akceptacji
//            if (sad00 < 400) {
//                return new MotionVector(0, 0);
//            }
//            bestCost = sad00;
//        }
//
//        for (int dy = -R; dy <= R; dy++) {
//            int ry = blockY + dy;
//            if (ry < -15 || ry >= height) continue;
//
//            for (int dx = -R; dx <= R; dx++) {
//                if (dx == 0 && dy == 0) continue;
//
//                int rx = blockX + dx;
//                if (rx < -15 || rx >= width) continue;
//
//                long sad = calculateSAD_Safe(currentBlockFlat, refY, rx, ry, width, height, bestCost);
//
//                if (sad < bestCost) {
//                    bestCost = sad;
//                    bestDx = dx;
//                    bestDy = dy;
//                }
//            }
//        }
//
//        return new MotionVector(bestDx, bestDy);
//    }
    private static long calculateSAD_Safe(int[] curBlock, int[][] refY, int refX, int refYStart, int w, int h, long limit) {
        long sad = 0;
        int idx = 0;

        for (int y = 0; y < 16; y++) {
            // Clamp Y dla referencji
            int ry = refYStart + y;
            if (ry < 0) ry = 0;
            else if (ry >= h) ry = h - 1;

            int[] refRow = refY[ry];

            for (int x = 0; x < 16; x++) {
                // Clamp X dla referencji
                int rx = refX + x;
                if (rx < 0) rx = 0;
                else if (rx >= w) rx = w - 1;

                int diff = curBlock[idx++] - refRow[rx];
                sad += (diff < 0) ? -diff : diff;
            }

            if (sad >= limit) return Long.MAX_VALUE;
        }
        return sad;
    }
/**
 * Wersja 3 - Step Search
 * **/
    /**
     * SZYBKIE wyszukiwanie ruchu (Fast Motion Estimation).
     * Zamiast Full Search (289 sprawdzeń), używa strategii Diamond/Step.
     * Złożoność spada z O(R^2) do O(log R).
     */
    public static MotionVector searchMV_Luma_SAD(int[][] curY, int[][] refY, int blockX, int blockY, int R) {
        int height = curY.length;
        int width = curY[0].length;

        // 1. Prefetch bloku (tak jak wcześniej - to jest super ważne)
        int[] currentBlockFlat = new int[256];
        for (int i = 0; i < 16; i++) {
            int srcY = Math.min(blockY + i, height - 1);
            if (blockX + 16 <= width) {
                System.arraycopy(curY[srcY], blockX, currentBlockFlat, i * 16, 16);
            } else {
                for (int j = 0; j < 16; j++) {
                    int srcX = Math.min(blockX + j, width - 1);
                    currentBlockFlat[i * 16 + j] = curY[srcY][srcX];
                }
            }
        }

        // --- KROK 1: START (0,0) ---
        // Zawsze sprawdzamy środek.
        long bestCost = Long.MAX_VALUE;
        int bestDx = 0;
        int bestDy = 0;

        if (blockY < height && blockX < width) {
            bestCost = calculateSAD_Safe(currentBlockFlat, refY, blockX, blockY, width, height, Long.MAX_VALUE);
            // Agresywny próg dla tła (Early Exit)(400)
            if (bestCost < 1000) return new MotionVector(0, 0);
        }

        // --- KROK 2: FAST SEARCH (Zredukowana złożoność) ---
        // Zamiast pętli for(-R do R), robimy "skoki".
        // Krok początkowy to połowa zasięgu (np. 4), potem 2, potem 1.

        int step = R / 2;
        if (step < 1) step = 1;

        int centerX = 0;
        int centerY = 0;

        while (step >= 1) {
            boolean foundBetter = false;

            // Sprawdzamy 8 punktów wokół obecnego centrum w odległości 'step'
            // (-1,-1) (0,-1) (1,-1)
            // (-1, 0)   C    (1, 0)
            // (-1, 1) (0, 1) (1, 1)

            int startDx = bestDx;
            int startDy = bestDy;

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue; // Środek już mamy

                    int checkDx = startDx + (dx * step);
                    int checkDy = startDy + (dy * step);

                    // Sprawdzenie czy mieścimy się w zadeklarowanym zasięgu R (opcjonalne, ale bezpieczne)
                    if (checkDx < -R || checkDx > R || checkDy < -R || checkDy > R) continue;

                    // Współrzędne w obrazie
                    int rx = blockX + checkDx;
                    int ry = blockY + checkDy;

                    // Szybki check granic
                    if (rx < -15 || rx >= width || ry < -15 || ry >= height) continue;

                    long sad = calculateSAD_Safe(currentBlockFlat, refY, rx, ry, width, height, bestCost);

                    if (sad < bestCost) {
                        bestCost = sad;
                        bestDx = checkDx;
                        bestDy = checkDy;
                        foundBetter = true;
                    }
                }
            }

            // Jeśli znaleźliśmy lepszy punkt, w następnej iteracji szukamy wokół niego.
            // Zmniejszamy krok tylko jeśli w tej iteracji nic nie poprawiliśmy
            // LUB (wersja uproszczona TSS) zmniejszamy krok zawsze.
            // Tutaj wersja "Three Step Search": Zmniejszamy krok zawsze.
            step /= 2;
        }

        // --- KROK 3: REFINEMENT (Ostatnie szlifowanie) ---
        // Sprawdzamy jeszcze najbliższych sąsiadów (krok 1) wokół zwycięzcy,
        // żeby upewnić się, że nie ominęliśmy lokalnego minimum.
        // (W algorytmie powyżej step kończy się na 1, więc to jest już zrobione).

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

