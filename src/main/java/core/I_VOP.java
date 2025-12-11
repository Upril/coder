package core;

import transforms.RLEHuffmanEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static core.YUVUtils.blitYUV420BlockToRGB;
import static core.YUVUtils.inverseTransform;
import static java.lang.Math.clamp;

public class I_VOP extends VideoObjectPlane implements Serializable {

    private BufferedImage frame;

    public I_VOP(BufferedImage image) {
        super(image);
        this.frame = image;
        header = new VOPHeader(0,1,true,10, image.getWidth(), image.getHeight());
    }

    public Bitstream encodeToLocalBitstream(){
        Bitstream bitstream = new Bitstream();
        encode(bitstream);
        return bitstream;
    }

    //WERSJA DZIAŁAJACA Z ROWNOLEGLOSCIA PO KLATKACH
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
////                encodeWithRLE(bitstream, luminance, OFFSET);
////                encodeWithRLE(bitstream, chrominanceU, OFFSET);
////                encodeWithRLE(bitstream, chrominanceV, OFFSET);
//                RLEHuffmanEncoder.encode(bitstream, luminance, OFFSET);
//                RLEHuffmanEncoder.encode(bitstream, chrominanceU, OFFSET);
//                RLEHuffmanEncoder.encode(bitstream, chrominanceV, OFFSET);
//            }
//        } catch (IOException e) {
//            throw new RuntimeException("Error encoding I-VOP", e);
//        }
//    }

@Override
public void encode(Bitstream bs) {
    try {
        header.encodeHeader(bs);

        final int OFFSET = 128;
        BufferedImage reconOut = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<EncodedMacroblock>> futures = new ArrayList<>(macroblocks.size());

        for (Macroblock mb : macroblocks) {
            final int mbX = mb.getStartX();
            final int mbY = mb.getStartY();

            futures.add(executor.submit(() -> {
                // DCT + kwantyzacja dla danych z frame
                mb.applyDCTAndQuantization();

                int[] qY = mb.getLuminance();
                int[] qU = mb.getChrominanceU();
                int[] qV = mb.getChrominanceV();

                Bitstream localBs = new Bitstream();
                RLEHuffmanEncoder.encode(localBs, qY, OFFSET);
                RLEHuffmanEncoder.encode(localBs, qU, OFFSET);
                RLEHuffmanEncoder.encode(localBs, qV, OFFSET);

                int[] recY = inverseTransform(qY, 16, 16,Macroblock.DEFAULT_QUANTIZATION_MATRIX);
                int[] recU = inverseTransform(qU, 8, 8, Macroblock.DEFAULT_QUANTIZATION_MATRIX);
                int[] recV = inverseTransform(qV, 8, 8, Macroblock.DEFAULT_QUANTIZATION_MATRIX);

                for (int i = 0; i < recY.length; i++) recY[i] = clamp(recY[i], 0, 255);
                for (int i = 0; i < recU.length; i++) recU[i] = clamp(recU[i], 0, 255);
                for (int i = 0; i < recV.length; i++) recV[i] = clamp(recV[i], 0, 255);

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

        for (int i = 0; i < futures.size(); i++) {
            EncodedMacroblock em = futures.get(i).get();
            synchronized (bs) {
                bs.writeBytes(em.encoded);
            }
            blitYUV420BlockToRGB(reconOut, em.startX, em.startY, em.recY, em.recU, em.recV);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        this.frame = reconOut;

    } catch (IOException | InterruptedException | ExecutionException e) {
        throw new RuntimeException("Error encoding I-VOP", e);
    }
}

    public BufferedImage getReconOut() {
        return frame;
    }

    private static final class EncodedMacroblock {
        int startX, startY;
        byte[] encoded;
        int[] recY, recU, recV;
    }

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
    private void encodeWithRLE(Bitstream bitstream, int[] data, int offset) throws IOException {
        int currentValue = data[0] + offset;
        int runLength = 1;

        for (int i = 1; i < data.length; i++) {
            int nextValue = data[i] + offset;

            if (nextValue == currentValue) {
                runLength++;
            } else {
                bitstream.writeBits(8, currentValue);
                bitstream.writeBits(8, runLength);

                currentValue = nextValue;
                runLength = 1;
            }
        }

        bitstream.writeBits(8, currentValue);
        bitstream.writeBits(8, runLength);
    }
}
