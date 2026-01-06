import core.*;
import core.Metrics;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Encoder {

    // Klasa konfiguracyjna
    static class Config {
        String inputPath;
        String outputPath = "output.bin";
        int width = 1920;
        int height = 1080;
        int frames = 100;
        boolean measureQuality = false; // Czy liczyć PSNR/SSIM (wolne!)

        // Można dodać opcje np. algorytm ME, half-pel on/off itp.
    }
    static class Stats {
        long totalTimeNs;
        long totalBytes;
        double sumPSNR;
        double sumSSIM;
        int processedFrames;
    }
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

        final int GOP_SIZE = 30;    // Maksymalny odstęp między klatkami I
        final int SEARCH_RANGE = 8; // Zakres dla predykcji ruchu

        int frameSize = width * height * 3 / 2; // YUV 4:2:0

        try (RandomAccessFile raf = new RandomAccessFile(yuvFilePath, "r");
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {

            byte[] frameData = new byte[frameSize];
            BufferedImage refRecon = null; // referencja dla P-klatek

            int framesSinceLastIntra = GOP_SIZE;

            for (int i = 0; i < frameCount; i++) {
                long offset = (long) i * frameSize;
                if (offset + frameSize > raf.length()) break; // EOF

                raf.seek(offset);
                raf.readFully(frameData);

                BufferedImage frame = yuvToBufferedImage(frameData, 0, width, height);
                Bitstream localStream = new Bitstream();

                // --- ZMIANA 2: Detekcja zmiany sceny ---
                boolean isSceneChange = false;

                if (refRecon != null) {
                    if (SceneChangeDetector.detect(frame, refRecon)) {
                        isSceneChange = true;
                        // Opcjonalnie: logowanie dla celów testowych
                        System.out.println("\n[Frame " + i + "] Wykryto zmianę sceny! Wymuszam I-VOP.");
                    }
                }

                // Kodujemy jako I-VOP, jeśli:
                // 1. Minął czas GOP_SIZE (wymuszenie okresowe)
                // 2. Nie mamy jeszcze referencji (pierwsza klatka)
                // 3. Wykryto zmianę sceny (wymuszenie adaptacyjne)
                if (framesSinceLastIntra >= GOP_SIZE || refRecon == null || isSceneChange) {

                    // 🔹 I-VOP (pełna klatka intra)
                    I_VOP iVop = new I_VOP(frame);
                    iVop.encode(localStream);
                    refRecon = iVop.getReconOut();

                    framesSinceLastIntra = 0; // Resetujemy licznik po klatce I

                } else {

                    // 🔹 P-VOP (predykcja + kompensacja)
                    P_VOP pVop = new P_VOP(frame, refRecon, SEARCH_RANGE);
                    pVop.encode(localStream);
                    refRecon = pVop.getReconOut();

                    framesSinceLastIntra++; // Inkrementujemy licznik
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


//public static void main(String[] args) {
//    Encoder encoder = new Encoder();
//    long start = System.currentTimeMillis();
//    try {
//        encoder.encodeToBinaryFile(
//                "D:/sample.yuv",   // input
//                1920,              // width
//                1080,              // height
//                2000,               // frame count
//                "output.bin");     // output
//    } catch (IOException e) {
//        throw new RuntimeException(e);
//    }
//    long end = System.currentTimeMillis();
//
//    System.out.println("\nExecution time: " + (end - start) + " ms");
//}

    public void encode(Config config) throws IOException {
        final int GOP_SIZE = 30;
        final int SEARCH_RANGE = 8;
        int frameSize = config.width * config.height * 3 / 2;

        Stats stats = new Stats();

        try (RandomAccessFile raf = new RandomAccessFile(config.inputPath, "r");
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(config.outputPath))) {

            byte[] frameData = new byte[frameSize];
            BufferedImage refRecon = null;
            int framesSinceLastIntra = GOP_SIZE;

            System.out.println("Starting encoding: " + config.frames + " frames from " + config.inputPath);
            if (config.measureQuality) System.out.println("Mode: QUALITY (PSNR/SSIM enabled - encoding will be slower)");
            else System.out.println("Mode: SPEED (Performance measurement only)");

            // --- START POMIARU CZASU ---
            long startTime = System.nanoTime();

            for (int i = 0; i < config.frames; i++) {
                long offset = (long) i * frameSize;
                if (offset + frameSize > raf.length()) break;

                raf.seek(offset);
                raf.readFully(frameData);

                // Konwersja YUV -> RGB
                BufferedImage frame = yuvToBufferedImage(frameData, 0, config.width, config.height);
                Bitstream localStream = new Bitstream();

                boolean isSceneChange = false;
                if (refRecon != null) {
                    if (SceneChangeDetector.detect(frame, refRecon)) {
                        isSceneChange = true;
                    }
                }

                VideoObjectPlane currentVop; // Interfejs lub klasa bazowa

                // Decyzja I-VOP czy P-VOP
                if (framesSinceLastIntra >= GOP_SIZE || refRecon == null || isSceneChange) {
                    I_VOP iVop = new I_VOP(frame);
                    iVop.encode(localStream);
                    refRecon = iVop.getReconOut();
                    framesSinceLastIntra = 0;
                    currentVop = iVop;
                } else {
                    P_VOP pVop = new P_VOP(frame, refRecon, SEARCH_RANGE);
                    pVop.encode(localStream);
                    refRecon = pVop.getReconOut();
                    framesSinceLastIntra++;
                    currentVop = pVop;
                }

                // Zapis
                byte[] encodedBytes = localStream.toByteArray();
                bos.write(encodedBytes);
                stats.totalBytes += encodedBytes.length;

                // --- METRYKI JAKOŚCI ---
                // Liczymy TYLKO, jeśli flaga jest włączona, żeby nie psuć testów wydajności
                if (config.measureQuality) {
                    // refRecon to obraz, który zobaczy dekoder. Porównujemy go z oryginałem (frame)
                    double psnr = Metrics.calculatePSNR(frame, refRecon);
                    double ssim = Metrics.calculateSSIM(frame, refRecon);
                    stats.sumPSNR += psnr;
                    stats.sumSSIM += ssim;
                }

                stats.processedFrames++;
                printProgress(i, config.frames);
            }

            long endTime = System.nanoTime();
            stats.totalTimeNs = endTime - startTime;
            // --- KONIEC POMIARU ---

            printReport(stats, config);

        } catch (IOException e) {
            throw new IOException("Critical encoding error", e);
        }
    }

    public static void main(String[] args) {
        Config config = new Config();

        // Prosty parser argumentów
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i": case "--input":
                    config.inputPath = args[++i];
                    break;
                case "-o": case "--output":
                    config.outputPath = args[++i];
                    break;
                case "-w": case "--width":
                    config.width = Integer.parseInt(args[++i]);
                    break;
                case "-h": case "--height":
                    config.height = Integer.parseInt(args[++i]);
                    break;
                case "-f": case "--frames":
                    config.frames = Integer.parseInt(args[++i]);
                    break;
                case "-q": case "--quality":
                    config.measureQuality = true; // Włącza powolne liczenie PSNR
                    break;
            }
        }

        if (config.inputPath == null) {
            System.out.println("Usage: java Encoder -i input.yuv [options]");
            System.out.println("Options:");
            System.out.println("  -w <width>      (default: 1920)");
            System.out.println("  -h <height>     (default: 1080)");
            System.out.println("  -f <frames>     (default: 100)");
            System.out.println("  -o <output>     (default: output.bin)");
            System.out.println("  --quality       Enable PSNR/SSIM calculation");
            return;
        }

        Encoder encoder = new Encoder();
        try {
            encoder.encode(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void printProgress(int current, int total) {
        int percent = (int) ((current + 1) / (double) total * 100);
        System.out.print("\rProgress: " + percent + "% (" + (current + 1) + "/" + total + ")");
    }

    private void printReport(Stats stats, Config config) {
        double timeSeconds = stats.totalTimeNs / 1_000_000_000.0;
        double fps = stats.processedFrames / timeSeconds;
        double bitrateKbps = (stats.totalBytes * 8 / 1000.0) / timeSeconds; // Bitrate realny (zależny od FPS kodowania? Nie, lepiej od trwania wideo)

        // Bitrate zazwyczaj liczy się względem czasu trwania wideo (np. 25fps), a nie czasu kodowania
        // Załóżmy 30 FPS dla kalkulacji bitrate
        double videoDuration = stats.processedFrames / 30.0;
        double videoBitrateKbps = (stats.totalBytes * 8 / 1000.0) / videoDuration;

        System.out.println("\n\n=== TEST RESULTS ===");
        System.out.printf("Total Frames:   %d%n", stats.processedFrames);
        System.out.printf("Total Time:     %.3f s%n", timeSeconds);
        System.out.printf("Encoding Speed: %.2f FPS%n", fps);
        System.out.printf("Output Size:    %.2f MB%n", stats.totalBytes / (1024.0 * 1024.0));
        System.out.printf("Bitrate (30fps):%.2f kbps%n", videoBitrateKbps);

        if (config.measureQuality) {
            System.out.printf("Avg PSNR (Y):   %.2f dB%n", stats.sumPSNR / stats.processedFrames);
            System.out.printf("Avg SSIM:       %.4f%n", stats.sumSSIM / stats.processedFrames);
        } else {
            System.out.println("Quality metrics skipped (run with --quality to measure)");
        }
        System.out.println("====================");
    }



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
