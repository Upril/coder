package org.encoder.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.encoder.utils.Macroblock.addResidual;
import static org.encoder.utils.RLE.decodeRLEData;
import static org.encoder.utils.RLE.inverseRLE;

public class Decoder {
    private List<VideoObjectPlane> vops;

    public Decoder() {
        vops = new ArrayList<>(); // Initialize the list for VOPs
    }

    public static void main(String[] args) {
        String inputPath = "output.bin";  // Path to the encoded binary file
        String outputYuvPath = "output.yuv";  // Output YUV file
        int width = 852;  // Example width of the video frames
        int height = 480; // Example height of the video frames
        int frameCount = 844; // Number of frames to decode (adjust as needed)

        Decoder decoder = new Decoder();  // Create a decoder instance

        try {
            List<BufferedImage> frames = decoder.decodeFromBinaryFile(inputPath, width, height, frameCount);
            decoder.saveFramesAsYUV(frames, outputYuvPath, width, height);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error decoding video: " + e.getMessage());
        }
    }

//    public List<BufferedImage> decodeFromBinaryFile(String inputPath, int width, int height, int frameCount) throws IOException {
//        List<BufferedImage> frames = new ArrayList<>();
//        byte[] encodedData = Files.readAllBytes(Paths.get(inputPath));
//        ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedData);
//        Bitstream bitstream = new Bitstream(encodedData);
//
//        for (int i = 0; i < frameCount; i++) {
//            VideoObjectPlane vop;
//            if (i == 0) {
//                vop = decodeIVOP(bitstream, width, height, inputStream);  // Decode I-VOP
//            } else {
//                // Use the last decoded VOP from the list
//                vop = decodePVOP(bitstream, width, height, inputStream, vops.get(i - 1));  // Pass previous VOP
//            }
//
//            vops.add(vop);  // Store the decoded VOP
//            frames.add(vopToBufferedImage(vop));  // Convert VOP to BufferedImage and store it
//        }
//        return frames;
//    }
public List<BufferedImage> decodeFromBinaryFile(String inputPath, int width, int height, int frameCount) throws IOException, ClassNotFoundException {
    List<BufferedImage> frames = new ArrayList<>();

    try (FileInputStream fis = new FileInputStream(inputPath)) {
        ObjectInputStream ois = new ObjectInputStream(fis);

        for (int i = 0; i < frameCount; i++) {
            VideoObjectPlane vop = (VideoObjectPlane) ois.readObject();  // Deserialize VOP
            frames.add(vopToBufferedImage(vop));  // Convert VOP to BufferedImage
        }

        ois.close();
    }

    return frames;
}
    public void saveFramesAsYUV(List<BufferedImage> frames, String outputYuvPath, int width, int height) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputYuvPath)) {
            for (BufferedImage frame : frames) {
                byte[] yuvData = bufferedImageToYUV(frame, width, height);
                fos.write(yuvData);
            }
        }
    }
    private byte[] bufferedImageToYUV(BufferedImage image, int width, int height) {
        byte[] yuvData = new byte[width * height * 3 / 2];  // YUV420 format: Y = width*height, U = V = width*height/4
        int frameSize = width * height;
        int[] rgbData = new int[width * height];
        image.getRGB(0, 0, width, height, rgbData, 0, width);

        // Extract Y, U, and V planes
        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + (frameSize / 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = rgbData[y * width + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Convert RGB to YUV
                int yValue = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int uValue = (int) (-0.169 * r - 0.331 * g + 0.5 * b + 128);
                int vValue = (int) (0.5 * r - 0.419 * g - 0.081 * b + 128);

                yValue = Math.max(0, Math.min(255, yValue));
                uValue = Math.max(0, Math.min(255, uValue));
                vValue = Math.max(0, Math.min(255, vValue));

                // Y plane
                yuvData[yIndex++] = (byte) yValue;

                // U and V planes (for 4:2:0 format, we sample U and V every 2x2 block)
                if (y % 2 == 0 && x % 2 == 0) {
                    yuvData[uIndex++] = (byte) uValue;
                    yuvData[vIndex++] = (byte) vValue;
                }
            }
        }
        return yuvData;
    }
//    private VideoObjectPlane decodeIVOP(Bitstream bitstream, int width, int height, InputStream inputStream) {
//        VideoObjectPlane vop = new I_VOP(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
//
//        // Iterate over macroblocks in the VideoObjectPlane
//        for (Macroblock mb : vop.getMacroblocks()) {
//            try {
//                // 1. Decode RLE data
//                List<int[]> rleLuminance = decodeRLEData(bitstream);
//                List<int[]> rleChrominanceU = decodeRLEData(bitstream);
//                List<int[]> rleChrominanceV = decodeRLEData(bitstream);
//
//                // 2. Inverse RLE
//                int[] luminanceData = RLE.runLengthDecode(rleLuminance);
//                if (luminanceData.length != 256) {
//                    throw new RuntimeException("Decoded luminance data does not match expected size 256: "+luminanceData.length);
//                }
//
//                int[] chrominanceUData = RLE.runLengthDecode(rleChrominanceU);
//                if (chrominanceUData.length != 64) {
//                    throw new RuntimeException("Decoded chrominance U data does not match expected size");
//                }
//
//                int[] chrominanceVData = RLE.runLengthDecode(rleChrominanceV);
//                if (chrominanceVData.length != 64) {
//                    throw new RuntimeException("Decoded chrominance V data does not match expected size");
//                }
//
//                // 3. Inverse Zig-Zag Scan
//                int[][] luminanceBlock = DCT.inverseZigZagScan(luminanceData); // If you have an inverse zig-zag method for 16x16
//
//                // 4. Dequantization (assuming you have quantization scales set)
//                double[][] dequantizedLuminance = Quantizer.dequantize(luminanceBlock, 10); // Assuming scale 10 for Y
//
//                // 5. Inverse DCT
//                int[][] decodedLuminance = DCT.inverseDCT(dequantizedLuminance);
//
//                // 6. Set the macroblock's luminance and chrominance values
//                mb.setLuminance(DCT.convertTo1D(decodedLuminance)); // Convert to 1D for macroblock storage
//                mb.setChrominanceU(chrominanceUData);
//                mb.setChrominanceV(chrominanceVData);
//            } catch (IOException e) {
//                throw new RuntimeException("Error decoding I-VOP", e);
//            }
//        }
//
//        return vop; // Return the fully decoded I-VOP
//    }
private VideoObjectPlane decodeIVOP(Bitstream bitstream, int width, int height) {
    VideoObjectPlane vop = new I_VOP(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));

    for (Macroblock mb : vop.getMacroblocks()) {
        try {
            // Read quantized DCT coefficients from the bitstream for Luminance and Chrominance
            int[] quantizedLuminance = new int[256]; // 16x16 block
            int[] quantizedChrominanceU = new int[64]; // 8x8 block
            int[] quantizedChrominanceV = new int[64]; // 8x8 block

            for (int i = 0; i < quantizedLuminance.length; i++) {
                quantizedLuminance[i] = bitstream.readBits(8);
            }
            for (int i = 0; i < quantizedChrominanceU.length; i++) {
                quantizedChrominanceU[i] = bitstream.readBits(8);
            }
            for (int i = 0; i < quantizedChrominanceV.length; i++) {
                quantizedChrominanceV[i] = bitstream.readBits(8);
            }

            // Dequantize the DCT coefficients
            double[][] dequantizedLuminance = Quantizer.dequantize(DCT.convertTo2D(quantizedLuminance), 10);
            double[][] dequantizedChrominanceU = Quantizer.dequantize(DCT.convertTo2D(quantizedChrominanceU), 15);
            double[][] dequantizedChrominanceV = Quantizer.dequantize(DCT.convertTo2D(quantizedChrominanceV), 15);

            // Apply Inverse DCT
            int[][] decodedLuminance = DCT.inverseDCT(dequantizedLuminance);
            int[][] decodedChrominanceU = DCT.inverseDCT(dequantizedChrominanceU);
            int[][] decodedChrominanceV = DCT.inverseDCT(dequantizedChrominanceV);

            // Set the macroblock’s decoded data
            mb.setLuminance(DCT.convertTo1D(decodedLuminance));
            mb.setChrominanceU(DCT.convertTo1D(decodedChrominanceU));
            mb.setChrominanceV(DCT.convertTo1D(decodedChrominanceV));
        } catch (IOException e) {
            throw new RuntimeException("Error decoding I-VOP", e);
        }
    }
    return vop;
}

    // Decode P-VOP frame
//    private VideoObjectPlane decodePVOP(Bitstream bitstream, int width, int height, InputStream inputStream, VideoObjectPlane referenceVOP) {
//        // Create an empty P-VOP
//        VideoObjectPlane vop = new P_VOP(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), referenceVOP);
//
//        // Iterate over macroblocks and decode each one
//        for (Macroblock mb : vop.getMacroblocks()) {
//            try {
//                // Decode the motion vector
//                int mvX = bitstream.readBits(16);  // Example: assuming 16 bits for motion vector X
//                int mvY = bitstream.readBits(16);  // Example: assuming 16 bits for motion vector Y
//                MotionVector motionVector = new MotionVector(mvX, mvY);
//                mb.setMotionVector(motionVector);
//
//                // Find the predicted macroblock using motion compensation
//                Macroblock predictedMB = referenceVOP.getMacroblockAt(mb.getStartX() + mvX, mb.getStartY() + mvY);
//
//                // Decode the residual RLE data
//                List<int[]> rleLuminance = decodeRLEData(bitstream);
//                List<int[]> rleChrominanceU = decodeRLEData(bitstream);
//                List<int[]> rleChrominanceV = decodeRLEData(bitstream);
//
//                // Perform inverse zig-zag scan
//                int[] zigZagLuminance = inverseRLE(rleLuminance);
//                int[] zigZagChrominanceU = inverseRLE(rleChrominanceU);
//                int[] zigZagChrominanceV = inverseRLE(rleChrominanceV);
//
//                // Convert zig-zag order back to 2D blocks
//                int[][] luminanceBlock = DCT.convertTo2D(zigZagLuminance);
//                int[][] chrominanceUBlock = DCT.convertTo2D(zigZagChrominanceU);
//                int[][] chrominanceVBlock = DCT.convertTo2D(zigZagChrominanceV);
//
//                // Dequantize the DCT coefficients
//                double[][] dequantizedLuminance = Quantizer.dequantize(luminanceBlock, 10);
//                double[][] dequantizedChrominanceU = Quantizer.dequantize(chrominanceUBlock, 15);
//                double[][] dequantizedChrominanceV = Quantizer.dequantize(chrominanceVBlock, 15);
//
//                // Apply Inverse DCT
//                int[][] decodedResidualLuminance = DCT.inverseDCT(dequantizedLuminance);
//                int[][] decodedResidualChrominanceU = DCT.inverseDCT(dequantizedChrominanceU);
//                int[][] decodedResidualChrominanceV = DCT.inverseDCT(dequantizedChrominanceV);
//
//                // Add the residual to the predicted macroblock to reconstruct the final macroblock
//                int[] finalLuminance = addResidual(predictedMB.getLuminance(), DCT.convertTo1D(decodedResidualLuminance));
//                int[] finalChrominanceU = addResidual(predictedMB.getChrominanceU(), DCT.convertTo1D(decodedResidualChrominanceU));
//                int[] finalChrominanceV = addResidual(predictedMB.getChrominanceV(), DCT.convertTo1D(decodedResidualChrominanceV));
//
//                // Set the macroblock's decoded data
//                mb.setLuminance(finalLuminance);
//                mb.setChrominanceU(finalChrominanceU);
//                mb.setChrominanceV(finalChrominanceV);
//            } catch (IOException e) {
//                throw new RuntimeException("Error decoding P-VOP", e);
//            }
//        }
//
//        return vop;
//    }
    private VideoObjectPlane decodePVOP(Bitstream bitstream, int width, int height, VideoObjectPlane referenceVOP) {
        VideoObjectPlane vop = new P_VOP(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), referenceVOP);

        for (Macroblock mb : vop.getMacroblocks()) {
            try {
                // Read quantized DCT coefficients of residuals from the bitstream
                int[] quantizedLuminance = new int[256];
                int[] quantizedChrominanceU = new int[64];
                int[] quantizedChrominanceV = new int[64];

                for (int i = 0; i < 256; i++) {
                    quantizedLuminance[i] = bitstream.readBits(8);
                }
                for (int i = 0; i < 64; i++) {
                    quantizedChrominanceU[i] = bitstream.readBits(8);
                    quantizedChrominanceV[i] = bitstream.readBits(8);
                }

                // Inverse Quantization and Inverse DCT
                double[][] dequantizedLuminance = Quantizer.dequantize(DCT.convertTo2D(quantizedLuminance), 10);
                double[][] dequantizedChrominanceU = Quantizer.dequantize(DCT.convertTo2D(quantizedChrominanceU), 15);
                double[][] dequantizedChrominanceV = Quantizer.dequantize(DCT.convertTo2D(quantizedChrominanceV), 15);

                int[][] decodedResidualLuminance = DCT.inverseDCT(dequantizedLuminance);
                int[][] decodedResidualChrominanceU = DCT.inverseDCT(dequantizedChrominanceU);
                int[][] decodedResidualChrominanceV = DCT.inverseDCT(dequantizedChrominanceV);

                // Add residuals to reference macroblock to reconstruct the final macroblock
                int[] finalLuminance = addResidual(referenceVOP.getMacroblockAt(mb.getStartX(), mb.getStartY()).getLuminance(), DCT.convertTo1D(decodedResidualLuminance));
                int[] finalChrominanceU = addResidual(referenceVOP.getMacroblockAt(mb.getStartX(), mb.getStartY()).getChrominanceU(), DCT.convertTo1D(decodedResidualChrominanceU));
                int[] finalChrominanceV = addResidual(referenceVOP.getMacroblockAt(mb.getStartX(), mb.getStartY()).getChrominanceV(), DCT.convertTo1D(decodedResidualChrominanceV));

                mb.setLuminance(finalLuminance);
                mb.setChrominanceU(finalChrominanceU);
                mb.setChrominanceV(finalChrominanceV);

            } catch (IOException e) {
                throw new RuntimeException("Error decoding P-VOP", e);
            }
        }
        return vop;
    }

    // Convert decoded VOP into BufferedImage or YUV format
    private BufferedImage vopToBufferedImage(VideoObjectPlane vop) {
        int width = vop.getWidth();
        int height = vop.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Loop through the macroblocks and set pixel values in the BufferedImage
        for (Macroblock mb : vop.getMacroblocks()) {
            int startX = mb.getStartX();
            int startY = mb.getStartY();

            // Get Y, U, and V values from the macroblock
            int[] luminance = mb.getLuminance();
            int[] chrominanceU = mb.getChrominanceU();
            int[] chrominanceV = mb.getChrominanceV();

            // Convert the 16x16 block of YUV data to RGB
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int pixelX = startX + x;
                    int pixelY = startY + y;

                    // Skip if the pixel is out of bounds
                    if (pixelX >= width || pixelY >= height) {
                        continue;
                    }

                    // Convert YUV to RGB
                    int yValue = luminance[y * 16 + x];
                    int uValue = chrominanceU[(y / 2) * 8 + (x / 2)];
                    int vValue = chrominanceV[(y / 2) * 8 + (x / 2)];

                    int[] rgb = yuvToRgb(yValue, uValue, vValue);

                    // Set RGB value in the BufferedImage
                    image.setRGB(pixelX, pixelY, (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]); // Set pixel in RGB format
                }
            }
        }

        return image; // Return the constructed BufferedImage
    }
    private int[] yuvToRgb(int y, int u, int v) {
        int r = (int) (y + 1.402 * (v - 128));
        int g = (int) (y - 0.344136 * (u - 128) - 0.714136 * (v - 128));
        int b = (int) (y + 1.772 * (u - 128));

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return new int[] {r, g, b};
    }
}
