package org.encoder.utils;

import java.awt.image.BufferedImage;

public class I_VOP extends VideoObjectPlane{

    public I_VOP(BufferedImage image) {
        super(image);
    }

    @Override
    public void encode() {
        //DCT,Quantization and other processing
        System.out.println("Encoding I-VOP...");

    }
}
