import org.encoder.utils.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Encoder {
    private List<VideoObjectPlane> vops;

    public Encoder() {
        vops = new ArrayList<>();
    }

    // Function to encode the sequence of frames

    public void encodeToBinaryFile(List<BufferedImage> frames, String outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            for (int i = 0; i < frames.size(); i++) {
                VideoObjectPlane vop;
                if (i == 0) {
                    vop = new I_VOP(frames.get(i)); // First frame as I-VOP
                } else {
                    vop = new P_VOP(frames.get(i), vops.get(i - 1)); // Subsequent frames as P-VOPs
                }
                vops.add(vop);

                // Serialize VOP object to the file
                oos.writeObject(vop);
            }

            oos.close();
        }
    }
    public static void main(String[] args) {
        // Example usage
        List<BufferedImage> frames = loadYUVFrames("C:\\Users\\jaxxo\\Desktop\\sample.yuv", 852, 480, 844); // Load YUV frames
        Encoder encoder = new Encoder();
        try{
            encoder.encodeToBinaryFile(frames,"output.bin");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    // Function to load YUV frames (as it is in your original code)
    private static List<BufferedImage> loadYUVFrames(String yuvFilePath, int width, int height, int frameCount) {
        List<BufferedImage> frames = new ArrayList<>();
        try {
            byte[] yuvData = Files.readAllBytes(new File(yuvFilePath).toPath());
            int frameSize = width * height * 3 / 2; // YUV 4:2:0 format

            for (int i = 0; i < frameCount; i++) {
                int offset = i * frameSize;
                if (offset + frameSize <= yuvData.length) {
                    BufferedImage frame = yuvToBufferedImage(yuvData, offset, width, height);
                    frames.add(frame);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading YUV file: " + e.getMessage());
        }
        return frames;
    }

    private static BufferedImage yuvToBufferedImage(byte[] yuvData, int offset, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int frameSize = width * height;
        int chromaSize = frameSize / 4;

        int yIndex = offset;
        int uIndex = offset + frameSize;
        int vIndex = uIndex + chromaSize;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yValue = yuvData[yIndex + y * width + x] & 0xFF;
                int uValue = yuvData[uIndex + (y / 2) * (width / 2) + (x / 2)] & 0xFF;
                int vValue = yuvData[vIndex + (y / 2) * (width / 2) + (x / 2)] & 0xFF;

                int r = (int) (yValue + 1.402 * (vValue - 128));
                int g = (int) (yValue - 0.344136 * (uValue - 128) - 0.714136 * (vValue - 128));
                int b = (int) (yValue + 1.772 * (uValue - 128));

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
