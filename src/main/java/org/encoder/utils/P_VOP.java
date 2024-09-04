package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class P_VOP extends VideoObjectPlane {
    private VideoObjectPlane referenceVOP;

    public P_VOP(BufferedImage image, VideoObjectPlane referenceVOP) {
        super(image);
        this.referenceVOP = referenceVOP;
        this.header = new VOPHeader(1,1,true,10, image.getWidth(), image.getHeight());
    }

    @Override
    public void encode(Bitstream bitstream) {
        try {

            // Encode VOP header
            header.encodeHeader(bitstream);

            // Perform motion estimation
            MotionEstimator motionEstimation = new MotionEstimator(referenceVOP, this);
            motionEstimation.estimate();

            // Encode macroblocks
            for (Macroblock macroblock : macroblocks) {
                macroblock.encodeInter(bitstream);
            }

            // Output the bitstream
            byte[] encodedData = bitstream.toByteArray();
            // Write encodedData to output file or stream
        } catch (IOException e) {
            throw new RuntimeException("Error encoding P-VOP", e);
        }
    }
}
