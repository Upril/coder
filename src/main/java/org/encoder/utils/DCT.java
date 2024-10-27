package org.encoder.utils;

public class DCT {
    public static final int BLOCK_SIZE = 8;
    static int[][] zigZagOrder = {
            {0, 1, 5, 6, 14, 15, 27, 28},
            {2, 4, 7, 13, 16, 26, 29, 42},
            {3, 8, 12, 17, 25, 30, 41, 43},
            {9, 11, 18, 24, 31, 40, 44, 53},
            {10, 19, 23, 32, 39, 45, 52, 54},
            {20, 22, 33, 38, 46, 51, 55, 60},
            {21, 34, 37, 47, 50, 56, 59, 61},
            {35, 36, 48, 49, 57, 58, 62, 63}
    };
    public static double[][] applyDCT(int[][] input) {
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
    public static int[][] inverseDCT(double[][] input) {
        int[][] output = new int[BLOCK_SIZE][BLOCK_SIZE];

        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                double sum = 0.0;

                for (int u = 0; u < BLOCK_SIZE; u++) {
                    for (int v = 0; v < BLOCK_SIZE; v++) {
                        double cu = (u == 0) ? 1 / Math.sqrt(2) : 1;
                        double cv = (v == 0) ? 1 / Math.sqrt(2) : 1;
                        sum += cu * cv * input[u][v] *
                                Math.cos((2 * x + 1) * u * Math.PI / (2 * BLOCK_SIZE)) *
                                Math.cos((2 * y + 1) * v * Math.PI / (2 * BLOCK_SIZE));
                    }
                }

                output[x][y] = (int) Math.round(0.25 * sum);  // Normalize by factor of 0.25
            }
        }

        return output;
    }
    public static int[][] convertTo2D(int[] input) {
        int[][] output = new int[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[i][j] = input[i * BLOCK_SIZE + j];
            }
        }
        return output;
    }
    public static int[] convertTo1D(int[][] input) {
        int[] output = new int[BLOCK_SIZE * BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[i * BLOCK_SIZE + j] = input[i][j];
            }
        }
        return output;
    }
    public static int[] zigZagScan(int[][] input) {
        int[] output = new int[BLOCK_SIZE * BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[zigZagOrder[i][j]] = input[i][j];
            }
        }
        return output;
    }
    public static int[][] inverseZigZagScan(int[] input) {
        int[][] output = new int[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                output[i][j] = input[zigZagOrder[i][j]];
            }
        }
        return output;
    }

}
