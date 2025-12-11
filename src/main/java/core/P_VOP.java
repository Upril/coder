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

import static core.MotionVector.searchMV_Luma_SAD;
import static core.MotionVector.writeMV;
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

        // 1. Przygotowanie danych (bezpieczne dla wątków - tylko odczyt)
        int[][] lumaCur = extractLumaPlane(curr);
        int[][] lumaRef = extractLumaPlane(refRecon);

        int mbCols = (curr.getWidth() + 15) / 16;
        int mbRows = (curr.getHeight() + 15) / 16;
        int totalMacroblocks = mbCols * mbRows;

        // 2. Tablica na wyniki - prealokowana.
        // Dzięki temu wątki mogą pisać w losowej kolejności,
        // a na końcu i tak odczytamy je po kolei (zachowując strukturę bitstreamu).
        EncodedMacroblock[] resultsArray = new EncodedMacroblock[totalMacroblocks];

        // 3. Atomowy licznik do rozdawania zadań
        java.util.concurrent.atomic.AtomicInteger nextMbIndex = new java.util.concurrent.atomic.AtomicInteger(0);

        // 4. Pula wątków
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        // Lista zadań dla wątków (Workerów)
        // Tworzymy tyle workerów ile rdzeni. Każdy worker działa w pętli aż wyczerpią się bloki.
        List<Callable<Void>> workers = new ArrayList<>(cores);

        for (int i = 0; i < cores; i++) {
            workers.add(() -> {

                while (true) {
                    // Pobierz indeks do przetworzenia
                    int idx = nextMbIndex.getAndIncrement();
                    if (idx >= totalMacroblocks) break; // Koniec pracy

                    // Przelicz indeks liniowy na X, Y
                    int mbX = (idx % mbCols) * 16;
                    int mbY = (idx / mbCols) * 16;

                    Macroblock mb = macroblocks.get(idx);

                    // 1. ME
                    MotionVector mv = searchMV_Luma_SAD(lumaCur, lumaRef, mbX, mbY, searchRange);
                    mb.setMotionVector(mv);

                    // 2. MC & Residuum
                    int[] predY = extractLumaBlock(refRecon, mbX + mv.dx, mbY + mv.dy, 16, 16);
                    int[] currY = extractLumaBlock(curr, mbX, mbY, 16, 16);
                    int[] resY = sub(currY, predY);
                    int[] predU = extractChromaBlock420_U(refRecon, mbX + mv.dx, mbY + mv.dy);
                    int[] predV = extractChromaBlock420_V(refRecon, mbX + mv.dx, mbY + mv.dy);

                    // 3. SKIP Check
                    long energy = 0;
                    for (int val : resY) energy += Math.abs(val);
                    boolean isSkip = (mv.dx == 0 && mv.dy == 0 && energy < SKIP_THRESHOLD);

                    // Kontener na wynik
                    EncodedMacroblock out = new EncodedMacroblock();
                    out.startX = mbX;
                    out.startY = mbY;
                    out.mv = mv; // Zapisujemy surowy MV, różnicę policzymy przy zapisie sekwencyjnym!
                    out.isSkip = isSkip;

                    if (isSkip) {
                        out.recY = predY;
                        out.recU = predU;
                        out.recV = predV;
                        // Nie generujemy bitstreamu tutaj, zrobimy to później, żeby obsłużyć DiffMV poprawnie
                    } else {
                        int[] currU = extractChromaBlock420_U(curr, mbX, mbY);
                        int[] currV = extractChromaBlock420_V(curr, mbX, mbY);
                        int[] resU = sub(currU, predU);
                        int[] resV = sub(currV, predV);

                        mb.setQuantizationMatrix(Macroblock.FLAT_QUANTIZATION_MATRIX);

                        out.qY = mb.applyDCTAndQuantizationToBlock(resY, 16, 16);
                        out.qU = mb.applyDCTAndQuantizationToBlock(resU, 8, 8);
                        out.qV = mb.applyDCTAndQuantizationToBlock(resV, 8, 8);

                        // Rekonstrukcja
                        int[] iqY = inverseTransform(out.qY, 16, 16, Macroblock.FLAT_QUANTIZATION_MATRIX);
                        int[] iqU = inverseTransform(out.qU, 8, 8, Macroblock.FLAT_QUANTIZATION_MATRIX);
                        int[] iqV = inverseTransform(out.qV, 8, 8, Macroblock.FLAT_QUANTIZATION_MATRIX);

                        out.recY = add(iqY, predY);
                        out.recU = add(iqU, predU);
                        out.recV = add(iqV, predV);
                    }

                    // Clampowanie
                    for (int k=0; k<out.recY.length; k++) out.recY[k] = clamp(out.recY[k], 0, 255);
                    for (int k=0; k<out.recU.length; k++) out.recU[k] = clamp(out.recU[k], 0, 255);
                    for (int k=0; k<out.recV.length; k++) out.recV[k] = clamp(out.recV[k], 0, 255);

                    // Zapisz wynik w odpowiednim "slocie" tablicy
                    // To jest bezpieczne wielowątkowo, bo każdy wątek pisze pod unikalny indeks
                    resultsArray[idx] = out;
                }
                return null;
            });
        }

        // 5. Uruchomienie workerów i czekanie na koniec
        executor.invokeAll(workers);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        // 6. SEKWENCYJNY ZAPIS DO STRUMIENIA (Main Thread)
        // Tutaj robimy kodowanie entropijne i DiffMV.

        BufferedImage reconOut = new BufferedImage(curr.getWidth(), curr.getHeight(), BufferedImage.TYPE_INT_RGB);
        MotionVector prevMV = new MotionVector(0,0);

        for (int i = 0; i < totalMacroblocks; i++) {
            EncodedMacroblock em = resultsArray[i];

            // Reset predyktora MV na początku każdego wiersza (zgodnie ze sztuką)
            if (i % mbCols == 0) prevMV = new MotionVector(0,0);

            if (em.isSkip) {
                bs.writeBits(1, 1); // Flag SKIP = 1
                prevMV = new MotionVector(0,0); // Reset predyktora w skipie
            } else {
                bs.writeBits(1, 0); // Flag SKIP = 0

                // DiffMV
                int diffX = em.mv.dx - prevMV.dx;
                int diffY = em.mv.dy - prevMV.dy;
                writeMV(bs, new MotionVector(diffX, diffY), 7);
                prevMV = em.mv;

                // Huffman (robimy to tutaj, bo trwa ułamki mikrosekund)
                RLEHuffmanEncoder.encode(bs, em.qY, OFFSET);
                RLEHuffmanEncoder.encode(bs, em.qU, OFFSET);
                RLEHuffmanEncoder.encode(bs, em.qV, OFFSET);
            }

            // Blit do obrazu wynikowego
            blitYUV420BlockToRGB(reconOut, em.startX, em.startY, em.recY, em.recU, em.recV);
        }

        this.reconOut = reconOut;
        // ... zapis pliku png (opcjonalnie) ...

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
