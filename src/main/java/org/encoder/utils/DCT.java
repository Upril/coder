package org.encoder.utils;

public class DCT {
    private static final int N = 8; // Block size

    public static double[][] applyDCT(double[][] block) {
        double[][] dct = new double[N][N];
        for (int u = 0; u < N; u++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int x = 0; x < N; x++) {
                    for (int y = 0; y < N; y++) {
                        sum += block[x][y] * Math.cos((2 * x + 1) * u * Math.PI / (2 * N))
                                * Math.cos((2 * y + 1) * v * Math.PI / (2 * N));
                    }
                }
                double cU = (u == 0) ? 1 / Math.sqrt(2) : 1.0;
                double cV = (v == 0) ? 1 / Math.sqrt(2) : 1.0;
                dct[u][v] = 0.25 * cU * cV * sum;
            }
        }
        return dct;
    }
    public static double[][] applyIDCT(double[][] dctBlock) {
        double[][] idct = new double[N][N];

        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                double sum = 0.0;
                for (int u = 0; u < N; u++) {
                    for (int v = 0; v < N; v++) {
                        double cU = (u == 0) ? 1 / Math.sqrt(2) : 1.0;
                        double cV = (v == 0) ? 1 / Math.sqrt(2) : 1.0;

                        sum += cU * cV * dctBlock[u][v] *
                                Math.cos((2 * x + 1) * u * Math.PI / (2 * N)) *
                                Math.cos((2 * y + 1) * v * Math.PI / (2 * N));
                    }
                }
                idct[x][y] = 0.25 * sum;
            }
        }

        return idct;
    }
}