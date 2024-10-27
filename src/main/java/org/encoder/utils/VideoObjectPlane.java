package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class VideoObjectPlane implements Serializable {
    protected List<Macroblock> macroblocks;
    protected VOPHeader header;
    private int width;
    private int height;

    public List<Macroblock> getMacroblocks() {
        return macroblocks;
    }
    public Macroblock getMacroblockAt(int x, int y) {
        for (Macroblock mb : macroblocks) {
            if (mb.getStartX() == x && mb.getStartY() == y) {
                return mb;
            }
        }
        return null;
    }
    public int getWidth() {
        return width;
    }

    // Get the height of the VOP
    public int getHeight() {
        return height;
    }

    public VideoObjectPlane(BufferedImage image) {
        this.macroblocks = new ArrayList<>();
        splitIntoMacroblocks(image);
        this.width = image.getWidth();
        this.height = image.getHeight();
    }
    private void splitIntoMacroblocks(BufferedImage image){
        int width = image.getWidth();
        int height = image.getHeight();

        for(int y = 0; y<height; y+=16){
            for(int x = 0; x<width; x+=16){
                macroblocks.add(new Macroblock(image, x, y));
            }
        }
    }
    int[] applyDCTAndQuantize(int[] component, boolean isLuminance) {
        // Convert to 2D block, apply DCT
        List<int[]> quantizedBlocks = new ArrayList<>();

        // Process 8x8 blocks
        for (int blockY = 0; blockY < 16; blockY += 8) {
            for (int blockX = 0; blockX < 16; blockX += 8) {
                // Extract the 8x8 block
                int[] block = new int[64];
                for (int y = 0; y < 8; y++) {
                    System.arraycopy(component, (blockY + y) * 16 + blockX, block, y * 8, 8);
                }

                // Convert to 2D block and apply DCT
                int[][] block2D = DCT.convertTo2D(block);
                double[][] dctCoefficients = DCT.applyDCT(block2D);

                // Quantize DCT coefficients
                int quantizationScale = isLuminance ? 10 : 15; // Example: Luminance and Chrominance scales can be different
                int[][] quantizedCoefficients = Quantizer.quantize(dctCoefficients, quantizationScale);

                // Convert 2D quantized coefficients back to 1D array and store
                quantizedBlocks.add(DCT.convertTo1D(quantizedCoefficients));
            }
        }

        // Flatten the list of quantized blocks into a single array
        return quantizedBlocks.stream().flatMapToInt(Arrays::stream).toArray();
    }
    void encodeRLEData(Bitstream bitstream, List<int[]> rleData) throws IOException {
        for (int[] run : rleData) {
            bitstream.writeBits(8, run[0]);  // Run length of zeros
            // Check for non-zero coefficient
            int nonZeroCoefficient = run[1];
            if (nonZeroCoefficient < 0) {
                // Ensure we handle negative values correctly
                nonZeroCoefficient = Math.abs(nonZeroCoefficient); // Convert to positive for encoding
            }

            // Ensure we write valid values (0-255)
            if (nonZeroCoefficient > 255) {
                // Handle overflow if necessary (could happen based on quantization)
                nonZeroCoefficient = 255;
            }

            bitstream.writeBits(8, nonZeroCoefficient);  // Encode non-zero coefficient
        }
    }
    public abstract void encode(Bitstream bitstream);
}
