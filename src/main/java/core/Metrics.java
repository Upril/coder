package core;

import java.awt.image.BufferedImage;

public class Metrics {

    // --- PSNR (Peak Signal-to-Noise Ratio) ---
    public static double calculatePSNR(BufferedImage original, BufferedImage reconstructed) {
        int width = original.getWidth();
        int height = original.getHeight();
        long mseSum = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = original.getRGB(x, y);
                int rgb2 = reconstructed.getRGB(x, y);

                // Liczymy błąd tylko dla kanałów RGB (uproszczone)
                int r1 = (rgb1 >> 16) & 0xFF; int g1 = (rgb1 >> 8) & 0xFF; int b1 = rgb1 & 0xFF;
                int r2 = (rgb2 >> 16) & 0xFF; int g2 = (rgb2 >> 8) & 0xFF; int b2 = rgb2 & 0xFF;

                mseSum += (r1 - r2) * (r1 - r2);
                mseSum += (g1 - g2) * (g1 - g2);
                mseSum += (b1 - b2) * (b1 - b2);
            }
        }

        double mse = (double) mseSum / (width * height * 3);
        if (mse == 0) return 100.0; // Obrazy identyczne

        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }

    // --- SSIM (Structural Similarity) - Wersja uproszczona (Luma) ---
    // Pełna implementacja jest długa, ta wersja wystarczy do celów akademickich
    public static double calculateSSIM(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();

        // Parametry algorytmu SSIM
        double C1 = 6.5025;  // (0.01 * 255)^2
        double C2 = 58.5225; // (0.03 * 255)^2

        // Pobieramy płaszczyzny jasności (Luma)
        int[][] y1 = YUVUtils.extractLumaPlane(img1);
        int[][] y2 = YUVUtils.extractLumaPlane(img2);

        double ssimSum = 0.0;
        int windows = 0;

        // Przesuwamy okno 8x8
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                if (x + 8 > width || y + 8 > height) continue;
                ssimSum += getWindowSSIM(y1, y2, x, y, C1, C2);
                windows++;
            }
        }
        return ssimSum / windows;
    }

    private static double getWindowSSIM(int[][] y1, int[][] y2, int x, int y, double C1, double C2) {
        double mu1 = 0, mu2 = 0;

        // Średnia (Mean)
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                mu1 += y1[y+i][x+j];
                mu2 += y2[y+i][x+j];
            }
        }
        mu1 /= 64.0; mu2 /= 64.0;

        double sigma1_sq = 0, sigma2_sq = 0, sigma12 = 0;

        // Wariancja i kowariancja
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                double val1 = y1[y+i][x+j] - mu1;
                double val2 = y2[y+i][x+j] - mu2;
                sigma1_sq += val1 * val1;
                sigma2_sq += val2 * val2;
                sigma12 += val1 * val2;
            }
        }
        sigma1_sq /= 63.0; sigma2_sq /= 63.0; sigma12 /= 63.0;

        // Wzór SSIM
        return ((2 * mu1 * mu2 + C1) * (2 * sigma12 + C2)) /
                ((mu1 * mu1 + mu2 * mu2 + C1) * (sigma1_sq + sigma2_sq + C2));
    }
}
