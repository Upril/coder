package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public class I_VOP extends VideoObjectPlane implements Serializable {

    public I_VOP(BufferedImage image) {
        super(image);
        header = new VOPHeader(0,1,true,10, image.getWidth(), image.getHeight());
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
            throw new RuntimeException("Error encoding I-VOP", e);
        }
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
