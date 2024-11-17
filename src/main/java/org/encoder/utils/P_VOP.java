package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class P_VOP extends VideoObjectPlane implements Serializable {
    private VideoObjectPlane referenceVOP;

    public P_VOP(BufferedImage image, VideoObjectPlane referenceVOP) {
        super(image);
        this.referenceVOP = referenceVOP;
        this.header = new VOPHeader(1,1,true,10, image.getWidth(), image.getHeight());
    }

    @Override
    public void encode(Bitstream bitstream) {
        try {
            header.encodeHeader(bitstream);

            for (Macroblock macroblock : macroblocks) {
                macroblock.applyDCTAndQuantization();

                int[] luminance = macroblock.getLuminance();
                int[] chrominanceU = macroblock.getChrominanceU();
                int[] chrominanceV = macroblock.getChrominanceV();

                final int OFFSET = 128;
                encodeWithRLE(bitstream, luminance, OFFSET);
                encodeWithRLE(bitstream, chrominanceU, OFFSET);
                encodeWithRLE(bitstream, chrominanceV, OFFSET);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error encoding P-VOP", e);
        }
    }
    private int[] calculateResidual(int[] current, int[] predicted) {
        int[] residual = new int[current.length];
        for (int i = 0; i < current.length; i++) {
            residual[i] = current[i] - predicted[i];
        }
        return residual;
    }
    private void encodeWithRLE(Bitstream bitstream, int[] data, int offset) throws IOException {
        int currentValue = data[0] + offset;
        int runLength = 1;

        for (int i = 1; i < data.length; i++) {
            int nextValue = data[i] + offset;

            if (nextValue == currentValue) {
                runLength++;
            } else {
                bitstream.writeBits(8, currentValue);
                bitstream.writeBits(8, runLength);

                currentValue = nextValue;
                runLength = 1;
            }
        }
        bitstream.writeBits(8, currentValue);
        bitstream.writeBits(8, runLength);
    }
}
