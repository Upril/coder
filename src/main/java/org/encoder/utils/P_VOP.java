package org.encoder.utils;

import java.awt.image.BufferedImage;

public class P_VOP extends VideoObjectPlane {
    private VideoObjectPlane referenceVOP;

    public P_VOP(BufferedImage image, VideoObjectPlane referenceVOP) {
        super(image);
        this.referenceVOP = referenceVOP;
    }

    @Override
    public void encode() {
        // Apply motion estimation, DCT, quantization, etc. for P-VOP
        System.out.println("Encoding P-VOP...");
        // MotionEstimation motionEstimation = new MotionEstimation(image, referenceVOP.getImage());
    }
}
