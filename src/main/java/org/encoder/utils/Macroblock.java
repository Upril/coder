package org.encoder.utils;

import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Macroblock implements Serializable {
    private int startX, startY;
    private int[] luminance;
    private int[] chrominanceU;
    private int[] chrominanceV;
    private MotionVector motionVector;
    private static final int QUANTIZATION_SCALE = 20;

    public MotionVector getMotionVector() {
        return motionVector;
    }

    public void setMotionVector(MotionVector motionVector) {
        this.motionVector = motionVector;
    }

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public int[] getLuminance() {
        return luminance;
    }

    public int[] getChrominanceU() {
        return chrominanceU;
    }

    public int[] getChrominanceV() {
        return chrominanceV;
    }

    public Macroblock(BufferedImage frame, int startX, int startY) {
        this.startX = startX;
        this.startY = startY;
        this.luminance = new int[256];
        this.chrominanceU = new int[64];
        this.chrominanceV = new int[64];
        extractBlock(frame, startX, startY);
    }
    private void extractBlock(BufferedImage image, int startX, int startY){
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixelX = startX + x;
                int pixelY = startY + y;
                if (pixelX < image.getWidth() && pixelY < image.getHeight()) {
                    Color color = new Color(image.getRGB(pixelX, pixelY));
                    // Convert RGB to YUV and store in arrays
                    int[] yuv = rgbToYuv(color.getRed(), color.getGreen(), color.getBlue());
                    luminance[y * 16 + x] = yuv[0];

                    // For chrominance, subsample and store every other pixel
                    if (x % 2 == 0 && y % 2 == 0) {
                        int chromaIndex = (y / 2) * 8 + (x / 2);
                        chrominanceU[chromaIndex] = yuv[1];
                        chrominanceV[chromaIndex] = yuv[2];
                    }
                }
            }
        }

    }
    private int[] rgbToYuv(int r, int g, int b) {
        int y = (int)(0.299 * r + 0.587 * g + 0.114 * b);
        int u = (int)(-0.169 * r - 0.331 * g + 0.5 * b + 128);
        int v = (int)(0.5 * r - 0.419 * g - 0.081 * b + 128);
        return new int[] {y, u, v};
    }

    public void setLuminance(int[] luminance) {
        if (luminance.length == this.luminance.length) {
            this.luminance = luminance;
        } else {
            throw new IllegalArgumentException("Luminance array size mismatch: this.luminance: "+this.luminance.length+" luminance: "+luminance.length);
        }
    }

    // Set the chrominance U (Cb) values for the macroblock
    public void setChrominanceU(int[] chrominanceU) {
        if (chrominanceU.length == this.chrominanceU.length) {
            this.chrominanceU = chrominanceU;
        } else {
            throw new IllegalArgumentException("Chrominance U array size mismatch");
        }
    }

    // Set the chrominance V (Cr) values for the macroblock
    public void setChrominanceV(int[] chrominanceV) {
        if (chrominanceV.length == this.chrominanceV.length) {
            this.chrominanceV = chrominanceV;
        } else {
            throw new IllegalArgumentException("Chrominance V array size mismatch");
        }
    }
    public static int[] addResidual(int[] predicted, int[] residual) {
        int[] result = new int[predicted.length];
        for (int i = 0; i < predicted.length; i++) {
            result[i] = predicted[i] + residual[i];
        }
        return result;
    }
    private static final int BLOCK_SIZE = 8;
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
    public void applyDCTAndQuantization() {
        // Process luminance (16x16 block, 256 values)
        luminance = applyDCTAndQuantizationToBlock(this.luminance, 16, 16);

        // Process chrominance U (8x8 block, 64 values)
        chrominanceU = applyDCTAndQuantizationToBlock(this.chrominanceU, 8, 8);

        // Process chrominance V (8x8 block, 64 values)
        chrominanceV = applyDCTAndQuantizationToBlock(this.chrominanceV, 8, 8);
    }
    private int[] applyDCTAndQuantizationToBlock(int[] block, int blockWidth, int blockHeight) {
        int numBlocksX = blockWidth / BLOCK_SIZE;
        int numBlocksY = blockHeight / BLOCK_SIZE;
        int[] transformedBlock = new int[block.length];

        for (int by = 0; by < numBlocksY; by++) {
            for (int bx = 0; bx < numBlocksX; bx++) {
                // Extract the 8x8 sub-block
                double[][] subBlock = new double[BLOCK_SIZE][BLOCK_SIZE];
                for (int y = 0; y < BLOCK_SIZE; y++) {
                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int index = (by * BLOCK_SIZE + y) * blockWidth + (bx * BLOCK_SIZE + x);
                        subBlock[y][x] = block[index];
                    }
                }

                // Apply DCT and quantization
                double[][] dctCoefficients = DCT.applyDCT(subBlock);
                int[][] quantizedCoefficients = Quantizer.quantize(dctCoefficients, DEFAULT_QUANTIZATION_MATRIX);

                // Place transformed coefficients back into the result
                for (int y = 0; y < BLOCK_SIZE; y++) {
                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int index = (by * BLOCK_SIZE + y) * blockWidth + (bx * BLOCK_SIZE + x);
                        transformedBlock[index] = quantizedCoefficients[y][x];
                    }
                }
            }
        }
        return transformedBlock;
    }
    private double[][] reshapeTo2D(int[] block, int size) {
        double[][] result = new double[size][size];
        for (int i = 0; i < block.length; i++) {
            result[i / size][i % size] = block[i];
        }
        return result;
    }

    private int[] flatten2D(int[][] block2D) {
        int[] result = new int[block2D.length * block2D[0].length];
        for (int i = 0; i < block2D.length; i++) {
            for (int j = 0; j < block2D[i].length; j++) {
                result[i * block2D.length + j] = block2D[i][j];
            }
        }
        return result;
    }
}
