package org.encoder.utils;

public class DCT {
    private static final int BLOCK_SIZE = 8;

    public static double[][] applyDCT(double[][] input) {
        double[][] output = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int u = 0; u < BLOCK_SIZE; u++) {
            for (int v = 0; v < BLOCK_SIZE; v++) {
                double sum = 0.0;
                for (int x = 0; x < BLOCK_SIZE; x++) {
                    for (int y = 0; y < BLOCK_SIZE; y++) {
                        sum += input[x][y] *
                                Math.cos((2 * x + 1) * u * Math.PI / (2 * BLOCK_SIZE)) *
                                Math.cos((2 * y + 1) * v * Math.PI / (2 * BLOCK_SIZE));
                    }
                }
                double cu = (u == 0) ? 1 / Math.sqrt(2) : 1;
                double cv = (v == 0) ? 1 / Math.sqrt(2) : 1;
                output[u][v] = 0.25 * cu * cv * sum;
            }
        }
        return output;
    }
    public static double[][] convertTo2D(int[] input) {
        double[][] output = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[i][j] = input[i * BLOCK_SIZE + j];
            }
        }
        return output;
    }
    public static int[] convertTo1D(double[][] input) {
        int[] output = new int[BLOCK_SIZE * BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[i * BLOCK_SIZE + j] = (int) Math.round(input[i][j]);
            }
        }
        return output;
    }
}
