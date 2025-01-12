package org.encoder.utils;

import org.encoder.utils.VideoObjectPlane;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.encoder.utils.DCT.applyIDCT;
import static org.encoder.utils.Macroblock.BLOCK_SIZE;
import static org.encoder.utils.Quantizer.inverseQuantize;

public class Decoder {
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
    public static VOPHeader decodeHeader(Bitstream bitstream) throws IOException {
        bitstream.readBits(32); // Skip start code (0x000001B6)
        int vopType = bitstream.readBits(2);
        int vopTimeIncrement = bitstream.readBits(16);
        boolean vopCoded = bitstream.readBits(1) == 1;
        int vopQuant = bitstream.readBits(5);
        int vopWidth = bitstream.readBits(13);
        int vopHeight = bitstream.readBits(13);

        return new VOPHeader(vopType, vopTimeIncrement, vopCoded, vopQuant, vopWidth, vopHeight);
    }
    public void decode(Bitstream bitstream) throws IOException {
        List<VOPHeader> vops = new ArrayList<>();
        while (bitstream.hasRemaining()) {
            // Step 1: Decode VOP Header
            VOPHeader header = decodeHeader(bitstream);
            vops.add(header);

            // Step 2: Decode Macroblocks based on VOP header
            decodeMacroblocks(bitstream, header);
        }
    }

    private void decodeMacroblocks(Bitstream bitstream, VOPHeader header) throws IOException {
        int macroblockCount = (header.getVopWidth() / 16) * (header.getVopHeight() / 16);
        for (int i = 0; i < macroblockCount; i++) {
            decodeDCTMacroblock(bitstream, header);
        }
    }

    private void decodeDCTMacroblock(Bitstream bitstream, VOPHeader header) throws IOException {
        final int OFFSET = 128; // The offset used during encoding (to be reversed)

        // Decode luminance (Y)
        int[] luminance = decodeWithRLE(bitstream, header.getVopWidth() * header.getVopHeight() / 256); // Assuming 16x16 blocks for macroblocks

        // Decode chrominance U
        int[] chrominanceU = decodeWithRLE(bitstream, header.getVopWidth() * header.getVopHeight() / 256);

        // Decode chrominance V
        int[] chrominanceV = decodeWithRLE(bitstream, header.getVopWidth() * header.getVopHeight() / 256);


        int[] dctLuminance = inverseQuantizeBlocks(luminance, DEFAULT_QUANTIZATION_MATRIX);
        int[] dctChrominanceU = inverseQuantizeBlocks(chrominanceU, DEFAULT_QUANTIZATION_MATRIX);
        int[] dctChrominanceV = inverseQuantizeBlocks(chrominanceV, DEFAULT_QUANTIZATION_MATRIX);

        int[] decodedLuminance = applyIDCT(dctLuminance);
        int[] decodedChrominanceU = applyIDCT(dctChrominanceU);
        int[] decodedChrominanceV = applyIDCT(dctChrominanceV);


    }
    private int[] decodeWithRLE(Bitstream bitstream, int dataSize) throws IOException {
        int[] data = new int[dataSize];
        int index = 0;

        while (index < dataSize) {
            // Read the pixel value (8 bits)
            int pixelValue = bitstream.readBits(8) - 128; // Reverse the offset applied during encoding

            // Read the run length (8 bits)
            int runLength = bitstream.readBits(8);

            // Fill the array with the pixel value for the run length
            for (int i = 0; i < runLength && index < dataSize; i++) {
                data[index++] = pixelValue;
            }
        }
        return data;
    }
    private int[] applyIDCTBlocks(int[] data) {
        int numBlocks = data.length / (BLOCK_SIZE * BLOCK_SIZE);
        int[] result = new int[data.length];

        for (int i = 0; i < numBlocks; i++) {
            int[] block = new int[BLOCK_SIZE * BLOCK_SIZE];
            System.arraycopy(data, i * BLOCK_SIZE * BLOCK_SIZE, block, 0, BLOCK_SIZE * BLOCK_SIZE);
            // Apply inverse DCT to each 8x8 block
            int[] idctBlock = applyIDCT(block);
            System.arraycopy(idctBlock, 0, result, i * BLOCK_SIZE * BLOCK_SIZE, BLOCK_SIZE * BLOCK_SIZE);
        }
        return result;
    }
    private int[] applyIDCT(int[] block) {
        // Convert 1D array back to 2D for IDCT
        double[][] block2D = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                block2D[i][j] = block[i * BLOCK_SIZE + j];
            }
        }

        // Apply the inverse DCT
        double[][] idctBlock2D = DCT.applyIDCT(block2D);

        // Convert back to 1D array
        int[] result = new int[BLOCK_SIZE * BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                result[i * BLOCK_SIZE + j] = (int) Math.round(idctBlock2D[i][j]);
            }
        }
        return result;
    }
    private int[] inverseQuantizeBlocks(int[] data, int[][] quantMatrix) {
        int numBlocks = data.length / (BLOCK_SIZE * BLOCK_SIZE);
        int[] result = new int[data.length];
        for (int i = 0; i < numBlocks; i++) {
            int[] block = new int[BLOCK_SIZE * BLOCK_SIZE];
            System.arraycopy(data, i * BLOCK_SIZE * BLOCK_SIZE, block, 0, BLOCK_SIZE * BLOCK_SIZE);
            // Apply inverse quantization to each block
            int[] dequantizedBlock = inverseQuantize(block, quantMatrix);
            System.arraycopy(dequantizedBlock, 0, result, i * BLOCK_SIZE * BLOCK_SIZE, BLOCK_SIZE * BLOCK_SIZE);
        }
        return result;
    }
    private int[] inverseQuantize(int[] quantizedBlock, int[][] quantMatrix) {
        int[] block = new int[quantizedBlock.length];
        for (int i = 0; i < quantizedBlock.length; i++) {
            int row = i / BLOCK_SIZE;
            int col = i % BLOCK_SIZE;
            block[i] = quantizedBlock[i] * quantMatrix[row][col]; // Reversing quantization
        }
        return block;
    }
    public static void main(String[] args) throws IOException {
        // Read the bitstream from the encoded video file
        FileInputStream fileInputStream = new FileInputStream("output.bin");
        byte[] data = fileInputStream.readAllBytes();
        Bitstream bitstream = new Bitstream(data);

        // Initialize the VideoDecoder
        Decoder decoder = new Decoder();
        decoder.decode(bitstream);

        // Additional processing: After decoding, you'd typically reconstruct the frame images here.
    }
}
