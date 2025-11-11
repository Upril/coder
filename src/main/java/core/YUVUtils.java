package core;

import transforms.DCT;
import transforms.Quantizer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class YUVUtils {
    public static int clamp(int v,int lo,int hi){ return v<lo?lo:(v>hi?hi:v); }
    public static int getLuma(BufferedImage img, int x, int y) {
        x = Math.max(0, Math.min(x, img.getWidth() - 1));
        y = Math.max(0, Math.min(y, img.getHeight() - 1));
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int)(0.299*r + 0.587*g + 0.114*b);
    }

    public static int[] extractLumaBlock(BufferedImage img, int x0, int y0, int w, int h) {
        int[] out = new int[w*h];
        int i = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[i++] = getLuma(img, x0 + x, y0 + y);
        return out;
    }
//public static int[] extractLumaBlock(BufferedImage img, int startX, int startY, int w, int h) {
//    int width = img.getWidth();
//    int height = img.getHeight();
//    int[] rgb = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
//    int[] out = new int[w * h];
//
//    int idx = 0;
//    for (int y = 0; y < h; y++) {
//        int imgY = startY + y;
//        if (imgY >= height) break;  // nie wychodź poza dół
//
//        int base = imgY * width + startX;
//        for (int x = 0; x < w; x++) {
//            int imgX = startX + x;
//            if (imgX >= width) break; // nie wychodź poza prawo
//
//            int val = rgb[base + x];
//            int r = (val >> 16) & 0xFF;
//            int g = (val >> 8) & 0xFF;
//            int b = val & 0xFF;
//            out[idx++] = (r * 299 + g * 587 + b * 114) / 1000;
//        }
//    }
//    return out;
//}

    // U/V 4:2:0: prosty downsample z RGB (jak u Ciebie). Użyj tych samych współczynników co w Macroblock.
    public static int[] extractChromaBlock420_U(BufferedImage img, int x0, int y0) {
        int[] out = new int[64];
        int i = 0;
        for (int y = 0; y < 16; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                int xx = Math.max(0, Math.min(x0 + x, img.getWidth() - 1));
                int yy = Math.max(0, Math.min(y0 + y, img.getHeight() - 1));
                int rgb = img.getRGB(xx, yy);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                int u = (-43 * r - 85 * g + 128 * b + (128 << 8)) >> 8;
                out[i++] = clamp(u, 0, 255);
            }
        }
        return out;
    }

    public static int[] extractChromaBlock420_V(BufferedImage img, int x0, int y0) {
        int[] out = new int[64];
        int i = 0;
        for (int y = 0; y < 16; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                int xx = Math.max(0, Math.min(x0 + x, img.getWidth() - 1));
                int yy = Math.max(0, Math.min(y0 + y, img.getHeight() - 1));
                int rgb = img.getRGB(xx, yy);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                int v = (128 * r - 107 * g - 21 * b + (128 << 8)) >> 8;
                out[i++] = clamp(v, 0, 255);
            }
        }
        return out;
    }
    // -------- IQ + IDCT + flatten --------
    public static int[] inverseTransform(int[] qFlat, int blockWidth, int blockHeight) {
        if (blockWidth == 8 && blockHeight == 8) {
            return inverseTransform8x8(qFlat, 0, 0, 8);
        } else if (blockWidth == 16 && blockHeight == 16) {
            int[] out = new int[256];
            int[] tl = inverseTransform8x8(qFlat, 0, 0, 16);
            copyBlockTo(out, tl, 0, 0, 16);
            int[] tr = inverseTransform8x8(qFlat, 8, 0, 16);
            copyBlockTo(out, tr, 8, 0, 16);
            int[] bl = inverseTransform8x8(qFlat, 0, 8, 16);
            copyBlockTo(out, bl, 0, 8, 16);
            int[] br = inverseTransform8x8(qFlat, 8, 8, 16);
            copyBlockTo(out, br, 8, 8, 16);
            return out;
        } else {
            throw new IllegalArgumentException("Unsupported block size " + blockWidth + "x" + blockHeight);
        }
    }
    public static void copyBlockTo(int[] dest, int[] src, int destStartX, int destStartY, int stride) {
        for (int by = 0; by < 8; by++) {
            for (int bx = 0; bx < 8; bx++) {
                dest[(destStartY+by)*stride + (destStartX+bx)] = src[by*8+bx];
            }
        }
    }
    public static int[] inverseTransform8x8(int[] qFlat, int startX, int startY, int stride) {
        int[][] q2d = new int[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                q2d[y][x] = qFlat[(startY + y) * stride + (startX + x)];
            }
        }

        // IQ + IDCT
        double[][] deq = Quantizer.inverseQuantize(q2d, DEFAULT_QUANTIZATION_MATRIX);
        double[][] idct = DCT.applyIDCT(deq);

        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                out[y*8 + x] = (int)Math.round(idct[y][x]); // UWAGA: bez clamp!
            }
        }
        return out;
    }
    private static final int[][] DEFAULT_QUANTIZATION_MATRIX = {
            {16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}
    };
    // ---- Ekstrakcja płaszczyzn (bez tworzenia Color) ----






    // ---- YUV420 → RGB blit 16x16 do obrazu wynikowego ----
//    public static void blitYUV420BlockToRGB(BufferedImage dst, int x0, int y0,
//                                     int[] Y, int[] U, int[] V) {
//        int width = dst.getWidth();
//        int height = dst.getHeight();
//
//        for (int by = 0; by < 16; by++) {
//            int yPos = y0 + by;
//            if (yPos >= height) break; // poza dolną granicą
//
//            for (int bx = 0; bx < 16; bx++) {
//                int xPos = x0 + bx;
//                if (xPos >= width) break; // poza prawą granicą
//
//                int y = Y[by * 16 + bx];
//                int u = U[(by / 2) * 8 + (bx / 2)];
//                int v = V[(by / 2) * 8 + (bx / 2)];
//
//                int r = clamp((int)(y + 1.402 * (v - 128)), 0, 255);
//                int g = clamp((int)(y - 0.344136 * (u - 128) - 0.714136 * (v - 128)), 0, 255);
//                int b = clamp((int)(y + 1.772 * (u - 128)), 0, 255);
//
//                dst.setRGB(xPos, yPos, (r << 16) | (g << 8) | b);
//            }
//        }
//    }
    public static void blitYUV420BlockToRGB(BufferedImage dst, int startX, int startY,
                                            int[] y, int[] u, int[] v) {
        int width = dst.getWidth();
        int height = dst.getHeight();
        int[] rgb = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();

        for (int by = 0; by < 16; by++) {
            int dstY = startY + by;
            if (dstY >= height) break; // nie wychodź poza dół

            int dstLine = dstY * width + startX;
            int uvLine = (by / 2) * 8; // subsampling 4:2:0

            for (int bx = 0; bx < 16; bx++) {
                int dstX = startX + bx;
                if (dstX >= width) break; // nie wychodź poza prawo

                int Y = y[by * 16 + bx];
                int U = u[uvLine + (bx / 2)];
                int V = v[uvLine + (bx / 2)];

                int c = Y - 16;
                int d = U - 128;
                int e = V - 128;

                int R = clamp((298 * c + 409 * e + 128) >> 8);
                int G = clamp((298 * c - 100 * d - 208 * e + 128) >> 8);
                int B = clamp((298 * c + 516 * d + 128) >> 8);

                rgb[dstLine + bx] = (0xFF << 24) | (R << 16) | (G << 8) | B;
            }
        }
    }


    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }




    // ---- drobne utilsy ----

    public static int[] sub(int[] a, int[] b){ int[] o=new int[a.length]; for(int i=0;i<a.length;i++) o[i]=a[i]-b[i]; return o; }
    public static int[] add(int[] a, int[] b){ int[] o=new int[a.length]; for(int i=0;i<a.length;i++) o[i]=a[i]+b[i]; return o; }
}
