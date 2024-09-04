package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class I_VOP extends VideoObjectPlane{

    public I_VOP(BufferedImage image) {
        super(image);
        header = new VOPHeader(0,1,true,10, image.getWidth(), image.getHeight());
    }

    @Override
    public void encode(Bitstream bitstream) {
        try {

            // Encode VOP header
            header.encodeHeader(bitstream);

            // Encode macroblocks
            for (Macroblock macroblock : macroblocks) {
                macroblock.encodeIntra(bitstream);
            }

            // Output the bitstream
            byte[] encodedData = bitstream.toByteArray();
            // Write encodedData to output file or stream
        } catch (IOException e) {
            throw new RuntimeException("Error encoding I-VOP", e);
        }
    }
}
