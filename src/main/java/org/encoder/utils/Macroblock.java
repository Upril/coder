package org.encoder.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Macroblock {
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

    public void encodeIntra(Bitstream bitstream) throws IOException {
        // DCT
        int[] dctLuminance = applyDct(luminance, true);
        int[] dctChrominanceU = applyDct(chrominanceU, false);
        int[] dctChrominanceV = applyDct(chrominanceV, false);

        // Quantization
        int[] quantizedLuminance = quantize(dctLuminance);
        int[] quantizedChrominanceU = quantize(dctChrominanceU);
        int[] quantizedChrominanceV = quantize(dctChrominanceV);

        // Bitstream encoding of processed MB
        encodeYComponent(bitstream, quantizedLuminance);
        encodeUComponent(bitstream, quantizedChrominanceU);
        encodeVComponent(bitstream, quantizedChrominanceV);
    }

    // Encode the macroblock for a P-VOP (Inter-coded)
    public void encodeInter(Bitstream bitstream) throws IOException {
        // Motion compensation
        MotionVector motionVector = estimateMotion(); // Placeholder for motion vector estimation
        encodeMotionVector(bitstream, motionVector);

        // Residuals
        int[] residualLuminance = getResidualLuminance(motionVector);
        int[] residualChrominanceU = getResidualChrominanceU(motionVector);
        int[] residualChrominanceV = getResidualChrominanceV(motionVector);

        // DCT
        int[] dctLuminance = applyDct(residualLuminance, true);
        int[] dctChrominanceU = applyDct(residualChrominanceU, false);
        int[] dctChrominanceV = applyDct(residualChrominanceV, false);

        // Quantize the DCT coefficients
        int[] quantizedLuminance = quantize(dctLuminance);
        int[] quantizedChrominanceU = quantize(dctChrominanceU);
        int[] quantizedChrominanceV = quantize(dctChrominanceV);

        //Encode
        encodeYComponent(bitstream, quantizedLuminance);
        encodeUComponent(bitstream, quantizedChrominanceU);
        encodeVComponent(bitstream, quantizedChrominanceV);
    }
    private MotionVector estimateMotion() {
        // Implement motion estimation based on the reference frame
        return new MotionVector(0, 0); // Example placeholder
    }
    private int[] applyMotionCompensation(int[] block, MotionVector motionVector) {
        // Implement motion compensation logic here
        // For now, return the original block as a placeholder
        return block;
    }
    private void encodeMotionVector(Bitstream bitstream, MotionVector motionVector) throws IOException {
        bitstream.writeBits(16, motionVector.getX()); // Encode X component of motion vector
        bitstream.writeBits(16, motionVector.getY()); // Encode Y component of motion vector
    }

    private int[] applyDct(int[] inputBlock, boolean isLuminance) {
        int[] dctResult = new int[inputBlock.length];

        if (isLuminance) {
            for (int blockNr = 0; blockNr < 4; blockNr++) {
                double[][] block = DCT.convertTo2D(extract8x8Block(inputBlock, blockNr));
                double[][] dctBlock = DCT.applyDCT(block);
                int[] dct1D = DCT.convertTo1D(dctBlock);
                insertDctBlock(dctResult, dct1D, blockNr);
            }
        } else {
            double[][] chromaBlock = DCT.convertTo2D(inputBlock);
            double[][] dctChromaBlock = DCT.applyDCT(chromaBlock);
            int[] dct1DChroma = DCT.convertTo1D(dctChromaBlock);
            System.arraycopy(dct1DChroma, 0, dctResult, 0, dct1DChroma.length);
        }

        return dctResult;
    }

    public int[] quantize(int[] dctCoefficients) {
        int[] quantizedValues = new int[dctCoefficients.length];
        for (int i = 0; i < dctCoefficients.length; i++) {
            // Example quantization, ensure no negative values are directly encoded
            quantizedValues[i] = Math.max(0, Math.min(255, dctCoefficients[i] / QUANTIZATION_SCALE));
        }
        return quantizedValues;
    }

    private void encodeYComponent(Bitstream bitstream, int[] quantizedLuminance) throws IOException {
        for (int value : quantizedLuminance) {
            int encodedValue = encodeSignedValue(value);
            bitstream.writeBits(9, encodedValue);
        }
    }
    private int encodeSignedValue(int value) {
        return (value >= 0) ? (value << 1) : ((-value << 1) - 1);
    }

    private void encodeUComponent(Bitstream bitstream, int[] quantizedChrominanceU) throws IOException {
        for (int value : quantizedChrominanceU) {
            bitstream.writeBits(8, value);
        }
    }

    private void encodeVComponent(Bitstream bitstream, int[] quantizedChrominanceV) throws IOException {
        for (int value : quantizedChrominanceV) {
            bitstream.writeBits(8, value);
        }
    }
    private int[] extract8x8Block(int[] input, int blockIndex) {
        int[] block = new int[64];
        int blockOffsetX = (blockIndex % 2) * 8;
        int blockOffsetY = (blockIndex / 2) * 8;

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int sourceIndex = (blockOffsetY + y) * 16 + (blockOffsetX + x);
                block[y * 8 + x] = input[sourceIndex];
            }
        }
        return block;
    }
    private void insertDctBlock(int[] result, int[] dctBlock, int blockIndex) {
        int blockOffsetX = (blockIndex % 2) * 8;
        int blockOffsetY = (blockIndex / 2) * 8;

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int targetIndex = (blockOffsetY + y) * 16 + (blockOffsetX + x);
                result[targetIndex] = dctBlock[y * 8 + x];
            }
        }
    }
    // Get residual luminance after motion compensation
    private int[] getResidualLuminance(MotionVector motionVector) {
        // Compute residuals based on motion compensation and reference frame
        // Placeholder logic; should calculate the difference from the reference frame
        return luminance; // Replace with actual residual computation
    }

    // Get residual chrominance U after motion compensation
    private int[] getResidualChrominanceU(MotionVector motionVector) {
        // Compute residuals based on motion compensation and reference frame
        // Placeholder logic; should calculate the difference from the reference frame
        return chrominanceU; // Replace with actual residual computation
    }

    // Get residual chrominance V after motion compensation
    private int[] getResidualChrominanceV(MotionVector motionVector) {
        // Compute residuals based on motion compensation and reference frame
        // Placeholder logic; should calculate the difference from the reference frame
        return chrominanceV; // Replace with actual residual computation
    }
}
