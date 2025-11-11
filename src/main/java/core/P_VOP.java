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
        BufferedImage reconOut = new BufferedImage(curr.getWidth(), curr.getHeight(), BufferedImage.TYPE_INT_RGB);

        int[][] lumaCur = extractLumaPlane(curr);
        int[][] lumaRef = extractLumaPlane(refRecon);

        // Executor lokalny dla makrobloków tej klatki
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        // Zachowamy futures w tej samej kolejności co macroblocks list
        List<Future<EncodedMacroblock>> futures = new ArrayList<>(macroblocks.size());

        // Submit: każdy task wylicza MV, predykcję, residual, DCT+Q, entropię do lokalnego Bitstreamu,
        // oraz zwraca zrekonstruowany blok (recY/recU/recV) do złożenia reconOut.
        for (Macroblock mb : macroblocks) {
            final int mbX = mb.getStartX();
            final int mbY = mb.getStartY();

            futures.add(executor.submit(() -> {
                // 1) ME

                MotionVector mv = searchMV_Luma_SAD(lumaCur, lumaRef, mbX, mbY, searchRange);
                mb.setMotionVector(mv);

                // 2) MC: predykcja z referencji
                int[] predY = extractLumaBlock(refRecon, mbX + mv.dx, mbY + mv.dy, 16, 16);
                int[] predU = extractChromaBlock420_U(refRecon, mbX + mv.dx, mbY + mv.dy);
                int[] predV = extractChromaBlock420_V(refRecon, mbX + mv.dx, mbY + mv.dy);

                // 3) Curr bloki
                int[] currY = extractLumaBlock(curr, mbX, mbY, 16, 16);
                int[] currU = extractChromaBlock420_U(curr, mbX, mbY);
                int[] currV = extractChromaBlock420_V(curr, mbX, mbY);

                // 4) Residua
                int[] resY = sub(currY, predY);
                int[] resU = sub(currU, predU);
                int[] resV = sub(currV, predV);

                // 5) DCT + Q (zwraca spłaszczone kwantowane współczynniki)
                int[] qY = mb.applyDCTAndQuantizationToBlock(resY, 16, 16);
                int[] qU = mb.applyDCTAndQuantizationToBlock(resU, 8, 8);
                int[] qV = mb.applyDCTAndQuantizationToBlock(resV, 8, 8);

                // 6) Zapis MV + entropia do lokalnego bitstreamu
                Bitstream localBs = new Bitstream();
                // write MV (stałobitowo)
                writeMV(localBs, mv, 7);
                // entropia
                RLEHuffmanEncoder.encode(localBs, qY, OFFSET);
                RLEHuffmanEncoder.encode(localBs, qU, OFFSET);
                RLEHuffmanEncoder.encode(localBs, qV, OFFSET);

                // 7) REKONSTRUKCJA bloku (IQ + IDCT + add pred)
                int[] recY = inverseTransform(qY, 16, 16);
                int[] recU = inverseTransform(qU, 8, 8);
                int[] recV = inverseTransform(qV, 8, 8);

                recY = add(recY, predY);
                recU = add(recU, predU);
                recV = add(recV, predV);

                for (int i = 0; i < recY.length; i++) recY[i] = clamp(recY[i], 0, 255);
                for (int i = 0; i < recU.length; i++) recU[i] = clamp(recU[i], 0, 255);
                for (int i = 0; i < recV.length; i++) recV[i] = clamp(recV[i], 0, 255);

                // Zwracamy wynik: bajty zakodowanego makrobloku + rekonstrukcja bloków
                EncodedMacroblock out = new EncodedMacroblock();
                out.startX = mbX;
                out.startY = mbY;
                out.encoded = localBs.toByteArray();
                out.recY = recY;
                out.recU = recU;
                out.recV = recV;
                return out;
            }));
        }

        // Po zakończeniu wszystkich zadań: zapisz w kolejności makrobloków i złóż reconOut
        for (int i = 0; i < futures.size(); i++) {
            EncodedMacroblock em;
            try {
                em = futures.get(i).get(); // czekamy na i-ty makroblok (w kolejności)
            } catch (InterruptedException | ExecutionException e) {
                executor.shutdownNow();
                throw new IOException("Error encoding macroblock " + i, e);
            }

            // Zapis zakodowanych bajtów do globalnego bitstream — ujednolicone i bezpieczne
            synchronized (bs) {
                bs.writeBytes(em.encoded); // załóżmy, że Bitstream ma writeBytes(byte[])
            }

            // Złóż rekonstrukcję do reconOut (blok po bloku)
            blitYUV420BlockToRGB(reconOut, em.startX, em.startY, em.recY, em.recU, em.recV);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        // Ustaw pole reconOut, by mogło być użyte na zewnątrz
        this.reconOut = reconOut;
        // zapis do pliku w katalogu głównym projektu
//        try {
//            File outFile = new File("recon_PVOP.png");
//            ImageIO.write(reconOut, "png", outFile);
//            System.out.println("Zapisano zrekonstruowany obraz: " + outFile.getAbsolutePath());
//        } catch (IOException e) {
//            System.err.println("Nie udało się zapisać reconOut: " + e.getMessage());
//        }

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
        byte[] encoded;
        int[] recY, recU, recV;
        MotionVector mv;
    }



}
