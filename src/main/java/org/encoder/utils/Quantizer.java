package org.encoder.utils;

public class Quantizer {
//    private static final int[][] QUANTIZATION_MATRIX = {
//            {16, 11, 10, 16, 24, 40, 51, 61},
//            {12, 12, 14, 19, 26, 58, 60, 55},
//            {14, 13, 16, 24, 40, 57, 69, 56},
//            {14, 17, 22, 29, 51, 87, 80, 62},
//            {18, 22, 37, 56, 68, 109, 103, 77},
//            {24, 35, 55, 64, 81, 104, 113, 92},
//            {49, 64, 78, 87, 103, 121, 120, 101},
//            {72, 92, 95, 98, 112, 100, 103, 99}
//    };
private static final int[][] QUANTIZATION_MATRIX = {
        {16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55},
        {12, 12, 14, 19, 26, 58, 60, 55, 12, 12, 14, 19, 26, 58, 60, 55},
        {14, 13, 16, 24, 40, 57, 69, 56, 14, 13, 16, 24, 40, 57, 69, 56},
        {14, 17, 22, 29, 51, 87, 80, 62, 14, 17, 22, 29, 51, 87, 80, 62},
        {18, 22, 37, 56, 68, 109, 103, 77, 18, 22, 37, 56, 68, 109, 103, 77},
        {24, 35, 55, 64, 81, 104, 113, 92, 24, 35, 55, 64, 81, 104, 113, 92},
        {49, 64, 78, 87, 103, 121, 120, 101, 49, 64, 78, 87, 103, 121, 120, 101},
        {72, 92, 95, 98, 112, 100, 103, 99, 72, 92, 95, 98, 112, 100, 103, 99},
        {16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55},
        {12, 12, 14, 19, 26, 58, 60, 55, 12, 12, 14, 19, 26, 58, 60, 55},
        {14, 13, 16, 24, 40, 57, 69, 56, 14, 13, 16, 24, 40, 57, 69, 56},
        {14, 17, 22, 29, 51, 87, 80, 62, 14, 17, 22, 29, 51, 87, 80, 62},
        {18, 22, 37, 56, 68, 109, 103, 77, 18, 22, 37, 56, 68, 109, 103, 77},
        {24, 35, 55, 64, 81, 104, 113, 92, 24, 35, 55, 64, 81, 104, 113, 92},
        {49, 64, 78, 87, 103, 121, 120, 101, 49, 64, 78, 87, 103, 121, 120, 101},
        {72, 92, 95, 98, 112, 100, 103, 99, 72, 92, 95, 98, 112, 100, 103, 99}
};

    public static int[][] quantize(double[][] dctCoefficients, int quantizationScale) {
        int[][] quantizedCoefficients = new int[DCT.BLOCK_SIZE][DCT.BLOCK_SIZE];
        for (int i = 0; i < DCT.BLOCK_SIZE; i++) {
            for (int j = 0; j < DCT.BLOCK_SIZE; j++) {
                quantizedCoefficients[i][j] = (int) Math.round(dctCoefficients[i][j] / (QUANTIZATION_MATRIX[i][j] * quantizationScale));
            }
        }
        return quantizedCoefficients;
    }
    public static double[][] dequantize(int[][] quantizedCoefficients, int quantizationScale) {
        double[][] dequantizedCoefficients = new double[DCT.BLOCK_SIZE][DCT.BLOCK_SIZE];
        for (int i = 0; i < DCT.BLOCK_SIZE; i++) {
            for (int j = 0; j < DCT.BLOCK_SIZE; j++) {
                dequantizedCoefficients[i][j] = quantizedCoefficients[i][j] * QUANTIZATION_MATRIX[i][j] * quantizationScale;
            }
        }
        return dequantizedCoefficients;
    }
}
