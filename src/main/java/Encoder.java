import core.Bitstream;
import core.I_VOP;
import core.P_VOP;
import core.VideoObjectPlane;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Encoder {
    private List<VideoObjectPlane> vops;

    public Encoder() {
        vops = new ArrayList<>();
    }

//public void encodeToBinaryFile(List<BufferedImage> frames, String outputPath) throws IOException {
//    ExecutorService vopExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//    try (FileOutputStream fos = new FileOutputStream(outputPath)) {
//        Bitstream finalBitstream = new Bitstream();
//
//        List<Future<Bitstream>> futures = new ArrayList<>();
//        for (BufferedImage frame : frames) {
//            futures.add(vopExecutor.submit(() -> {
//                I_VOP vop = new I_VOP(frame);
//                Bitstream localStream = new Bitstream();
//                vop.encode(localStream);
//                return localStream;
//            }));
//        }
//        for (int i = 0; i < frames.size(); i++) {
//            Bitstream vopStream;
//            try {
//                vopStream = futures.get(i).get();
//            } catch (InterruptedException | ExecutionException e) {
//                Thread.currentThread().interrupt();
//                throw new IOException("Error encoding frame " + i, e);
//            }
//
//            finalBitstream.writeBytes(vopStream.toByteArray());
//        }
//        finalBitstream.writeToFile(outputPath);
//    } finally {
//        vopExecutor.shutdown();
//    }
//}

    //ROWNOLEGLE po klatkach
//    public void encodeToBinaryFile(
//        String yuvFilePath, int width, int height, int frameCount, String outputPath) throws IOException {
//
//    ExecutorService vopExecutor =
//            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//
//    int frameSize = width * height * 3 / 2; // YUV 4:2:0
//
//    try (RandomAccessFile raf = new RandomAccessFile(yuvFilePath, "r");
//         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {
//
//        byte[] frameData = new byte[frameSize];
//        List<Future<Bitstream>> futures = new ArrayList<>();
//
//        // 🔹 read + submit frames one by one
//        for (int i = 0; i < frameCount; i++) {
//            long offset = (long) i * frameSize;
//            if (offset + frameSize > raf.length()) {
//                break; // EOF
//            }
//
//            raf.seek(offset);
//            raf.readFully(frameData);
//
//            // Copy frameData for thread safety (each task must own its data)
//            byte[] frameCopy = frameData.clone();
//
//            Future<Bitstream> f = vopExecutor.submit(() -> {
//                BufferedImage frame = yuvToBufferedImage(frameCopy, 0, width, height);
//                I_VOP vop = new I_VOP(frame);
//                Bitstream localStream = new Bitstream();
//                vop.encode(localStream);
//                return localStream;
//            });
//            futures.add(f);
//
//            int progress = (int) ((i + 1) / (double) frameCount * 100);
//            System.out.print("\rŁadowanie + kodowanie: ["
//                    + "=".repeat(progress / 2)
//                    + " ".repeat(50 - progress / 2)
//                    + "] " + progress + "%");
//        }
//
//        // 🔹 collect results in order and write
//        for (int i = 0; i < futures.size(); i++) {
//            try {
//                Bitstream vopStream = futures.get(i).get(); // wait for i-th frame
//                bos.write(vopStream.toByteArray());        // flush to file
//            } catch (InterruptedException | ExecutionException e) {
//                Thread.currentThread().interrupt();
//                throw new IOException("Error encoding frame " + i, e);
//            }
//        }
//
//    } finally {
//        vopExecutor.shutdown();
//    }
//}

    public void encodeToBinaryFile(
            String yuvFilePath, int width, int height, int frameCount, String outputPath) throws IOException {

        final int GOP_SIZE = 30; // co 30 klatek wymuszaj I-VOP
        final int SEARCH_RANGE = 8; // zakres dla predykcji ruchu

        int frameSize = width * height * 3 / 2; // YUV 4:2:0

        try (RandomAccessFile raf = new RandomAccessFile(yuvFilePath, "r");
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {

            byte[] frameData = new byte[frameSize];
            BufferedImage refRecon = null; // referencja dla P-klatek

            for (int i = 0; i < frameCount; i++) {
                long offset = (long) i * frameSize;
                if (offset + frameSize > raf.length()) break; // EOF

                raf.seek(offset);
                raf.readFully(frameData);

                BufferedImage frame = yuvToBufferedImage(frameData, 0, width, height);
                Bitstream localStream = new Bitstream();

                if (i % GOP_SIZE == 0 || refRecon == null) {
                    // 🔹 I-VOP (pełna klatka intra)
                    I_VOP iVop = new I_VOP(frame);
                    iVop.encode(localStream);
                    refRecon = iVop.getReconOut(); // zapisz jako referencję
                } else {
                    // 🔹 P-VOP (predykcja + kompensacja)
                    P_VOP pVop = new P_VOP(frame, refRecon, SEARCH_RANGE);
                    pVop.encode(localStream);
                    refRecon = pVop.getReconOut();
                }

                // 🔹 zapis wynikowego bitstreamu do pliku
                bos.write(localStream.toByteArray());

                // 🔹 pasek postępu
                int progress = (int) ((i + 1) / (double) frameCount * 100);
                System.out.print("\rKodowanie w toku: ["
                        + "=".repeat(progress / 2)
                        + " ".repeat(50 - progress / 2)
                        + "] " + progress + "%");
            }

            System.out.println("\n✅ Zakończono kodowanie wideo.");

        } catch (IOException e) {
            throw new IOException("Błąd podczas kodowania wideo", e);
        }
    }

    //    public static void main(String[] args) {
//        List<BufferedImage> frames = loadYUVFrames("D:/sample.yuv", 1920, 1080, 750); ///storage2/home/mokon/sample.yuv   D:/sample.yuv
//        Encoder encoder = new Encoder();
//        long start = System.currentTimeMillis();
//        try{
//            encoder.encodeToBinaryFile(frames,"output.bin");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        long end = System.currentTimeMillis();
//
//        System.out.println("Execution time: " + (end - start) + " ms");
//    }
public static void main(String[] args) {
    Encoder encoder = new Encoder();
    long start = System.currentTimeMillis();
    try {
        encoder.encodeToBinaryFile(
                "D:/sample.yuv",   // input
                1920,              // width
                1080,              // height
                2000,               // frame count
                "output.bin");     // output
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    long end = System.currentTimeMillis();

    System.out.println("\nExecution time: " + (end - start) + " ms");
}

//    private static List<BufferedImage> loadYUVFrames(String yuvFilePath, int width, int height, int frameCount) {
//        List<BufferedImage> frames = new ArrayList<>();
//        try {
//            byte[] yuvData = Files.readAllBytes(new File(yuvFilePath).toPath());
//            int frameSize = width * height * 3 / 2; // YUV 4:2:0
//
//            for (int i = 0; i < frameCount; i++) {
//                int offset = i * frameSize;
//                if (offset + frameSize <= yuvData.length) {
//                    BufferedImage frame = yuvToBufferedImage(yuvData, offset, width, height);
//                    frames.add(frame);
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error reading YUV file: " + e.getMessage());
//        }
//        return frames;
//    }
private static List<BufferedImage> loadYUVFrames(String yuvFilePath, int width, int height, int frameCount) {
    List<BufferedImage> frames = new ArrayList<>();
    int frameSize = width * height * 3 / 2; // YUV 4:2:0

    try (RandomAccessFile raf = new RandomAccessFile(yuvFilePath, "r")) {
        byte[] frameData = new byte[frameSize];

        for (int i = 0; i < frameCount; i++) {
            long offset = (long) i * frameSize;

            if (offset + frameSize > raf.length()) {
                break; // Avoid reading past EOF
            }

            raf.seek(offset);
            raf.readFully(frameData);
            BufferedImage frame = yuvToBufferedImage(frameData, 0, width, height);
            frames.add(frame);
            int progress = (int) ((i + 1) / (double) frameCount * 100);
            System.out.print("\rŁadowanie: ["
                    + "=".repeat(progress / 2)
                    + " ".repeat(50 - progress / 2)
                    + "] " + progress + "%");
        }
    } catch (IOException e) {
        System.err.println("Error reading YUV file: " + e.getMessage());
    }

    return frames;
}

    private static BufferedImage yuvToBufferedImage(byte[] yuvData, int offset, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int frameSize = width * height;
        int chromaSize = frameSize / 4;

        int yIndex = offset;
        int uIndex = offset + frameSize;
        int vIndex = uIndex + chromaSize;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yValue = yuvData[yIndex + y * width + x] & 0xFF;
                int uValue = yuvData[uIndex + (y / 2) * (width / 2) + (x / 2)] & 0xFF;
                int vValue = yuvData[vIndex + (y / 2) * (width / 2) + (x / 2)] & 0xFF;

                int r = (int) (yValue + 1.402 * (vValue - 128));
                int g = (int) (yValue - 0.344136 * (uValue - 128) - 0.714136 * (vValue - 128));
                int b = (int) (yValue + 1.772 * (uValue - 128));

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
