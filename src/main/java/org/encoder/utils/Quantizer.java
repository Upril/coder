package org.encoder.utils;

public class Quantizer {
    public static int[][] quantize(double[][] block, int[][] quantMatrix) {
        int size = block.length;
        int[][] quantized = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                quantized[i][j] = (int) Math.round(block[i][j] / quantMatrix[i][j]);
            }
        }
        return quantized;
    }

    public static double[][] inverseQuantize(int[][] quantizedBlock, int[][] quantMatrix) {
        int size = quantizedBlock.length;
        double[][] block = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                block[i][j] = quantizedBlock[i][j] * quantMatrix[i][j];
            }
        }
        return block;
    }
}