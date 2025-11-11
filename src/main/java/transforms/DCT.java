package transforms;

public class DCT {
    private static final int N = 8; // Block size
    private static final double[][] COSINE_TABLE = new double[N][N];
    private static final double[] C = new double[N];

    static {
        for (int i = 0; i < N; i++) {
            C[i] = (i == 0) ? 1 / Math.sqrt(2) : 1.0;
            for (int j = 0; j < N; j++) {
                COSINE_TABLE[i][j] = Math.cos((2 * j + 1) * i * Math.PI / (2 * N));
            }
        }
    }

    public static double[][] applyDCT(double[][] block) {
        double[][] temp = new double[N][N];
        double[][] result = new double[N][N];

        for (int i = 0; i < N; i++) {
            for (int u = 0; u < N; u++) {
                double sum = 0.0;
                for (int x = 0; x < N; x++) {
                    sum += block[i][x] * COSINE_TABLE[u][x];
                }
                temp[i][u] = sum * C[u] * 0.5;
            }
        }

        for (int j = 0; j < N; j++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int y = 0; y < N; y++) {
                    sum += temp[y][j] * COSINE_TABLE[v][y];
                }
                result[v][j] = sum * C[v] * 0.5;
            }
        }

        return result;
    }
//    public static double[][] applyDCT(double[][] block) {
//        double[][] dct = new double[N][N];
//        for (int u = 0; u < N; u++) {
//            for (int v = 0; v < N; v++) {
//                double sum = 0.0;
//                for (int x = 0; x < N; x++) {
//                    for (int y = 0; y < N; y++) {
//                        sum += block[x][y] * Math.cos((2 * x + 1) * u * Math.PI / (2 * N))
//                                * Math.cos((2 * y + 1) * v * Math.PI / (2 * N));
//                    }
//                }
//                double cU = (u == 0) ? 1 / Math.sqrt(2) : 1.0;
//                double cV = (v == 0) ? 1 / Math.sqrt(2) : 1.0;
//                dct[u][v] = 0.25 * cU * cV * sum;
//            }
//        }
//        return dct;
//    }

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