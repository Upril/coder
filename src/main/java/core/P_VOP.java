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

            // --- NOWOŚĆ: Ekstrakcja płaszczyzn Chroma (U i V) do tablic przed uruchomieniem wątków ---
            // Dzięki temu w wątkach nie dotykamy wolnego BufferedImage
            int w = curr.getWidth();
            int h = curr.getHeight();
            int[][] uCur = new int[h / 2][w / 2];
            int[][] vCur = new int[h / 2][w / 2];
            int[][] uRef = new int[h / 2][w / 2];
            int[][] vRef = new int[h / 2][w / 2];

            // Szybka ekstrakcja Chroma (zakładamy 4:2:0 subsampling)
            // Robimy to w głównym wątku, bo to szybka liniowa operacja
            int[] rgbCur = ((java.awt.image.DataBufferInt) curr.getRaster().getDataBuffer()).getData();
            int[] rgbRef = ((java.awt.image.DataBufferInt) refRecon.getRaster().getDataBuffer()).getData();

            for (int y = 0; y < h; y += 2) {
                for (int x = 0; x < w; x += 2) {
                    // Current Frame Chroma
                    int valC = rgbCur[y * w + x];
                    int rC = (valC >> 16) & 0xFF;
                    int gC = (valC >> 8) & 0xFF;
                    int bC = valC & 0xFF;
                    uCur[y / 2][x / 2] = clamp((-43 * rC - 85 * gC + 128 * bC + 32768) >> 8, 0, 255);
                    vCur[y / 2][x / 2] = clamp((128 * rC - 107 * gC - 21 * bC + 32768) >> 8, 0, 255);

                    // Reference Frame Chroma
                    int valR = rgbRef[y * w + x];
                    int rR = (valR >> 16) & 0xFF;
                    int gR = (valR >> 8) & 0xFF;
                    int bR = valR & 0xFF;
                    uRef[y / 2][x / 2] = clamp((-43 * rR - 85 * gR + 128 * bR + 32768) >> 8, 0, 255);
                    vRef[y / 2][x / 2] = clamp((128 * rR - 107 * gR - 21 * bR + 32768) >> 8, 0, 255);
                }
            }
            // -----------------------------------------------------------------------------------------

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
                    // Bufory lokalne dla wątku (unikanie alokacji w pętli)
                    int[] tempCurrY = new int[256];
                    int[] tempPredU = new int[64];
                    int[] tempPredV = new int[64];
                    int[] tempCurrU = new int[64];
                    int[] tempCurrV = new int[64];

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

                        int curH = lumaCur.length;
                        int curW = lumaCur[0].length;
                        for (int y = 0; y < 16; y++) {
                            int srcY = clamp(mbY + y, 0, curH - 1);
                            // Kopiujemy wiersz (z clampowaniem X po elementach, lub arraycopy jeśli bezpiecznie)
                            for (int x = 0; x < 16; x++) {
                                tempCurrY[y * 16 + x] = lumaCur[srcY][clamp(mbX + x, 0, curW - 1)];
                            }
                        }
                        int[] resY = sub(tempCurrY, predY);


                        // --- ZMIANA: Chroma MC i Current z tablic (nie z BufferedImage) ---
                        // Chroma (Nearest Neighbor dla HalfPel -> powrót do pełnych pikseli / 2 dla 4:2:0)
                        int chromaRefX = (mbX + (mv.dx / 2)) / 2;
                        int chromaRefY = (mbY + (mv.dy / 2)) / 2;

                        int chromaCurX = mbX / 2;
                        int chromaCurY = mbY / 2;

                        int chromaH = uRef.length;
                        int chromaW = uRef[0].length;

                        // Pobieramy bloki 8x8 z tablic uRef/vRef oraz uCur/vCur
                        for (int y = 0; y < 8; y++) {
                            int rY = clamp(chromaRefY + y, 0, chromaH - 1);
                            int cY = clamp(chromaCurY + y, 0, chromaH - 1);

                            for (int x = 0; x < 8; x++) {
                                int rX = clamp(chromaRefX + x, 0, chromaW - 1);
                                int cX = clamp(chromaCurX + x, 0, chromaW - 1);

                                tempPredU[y * 8 + x] = uRef[rY][rX];
                                tempPredV[y * 8 + x] = vRef[rY][rX];

                                tempCurrU[y * 8 + x] = uCur[cY][cX];
                                tempCurrV[y * 8 + x] = vCur[cY][cX];
                            }
                        }

                        int[] predU = tempPredU; // Referencje do lokalnych buforów
                        int[] predV = tempPredV;
                        // -------------------------------------------------------------

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
                            // Musimy sklonować tablice, bo tempPredU są współdzielone w pętli
                            out.recY = predY;
                            out.recU = predU.clone();
                            out.recV = predV.clone();
                        } else {
                            // Stary kod używał extractChromaBlock420_U(curr...), my mamy już tempCurrU
                            int[] resU = sub(tempCurrU, predU);
                            int[] resV = sub(tempCurrV, predV);

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
