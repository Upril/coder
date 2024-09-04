package org.encoder.utils;

import java.awt.image.BufferedImage;

public abstract class VideoObjectPlane {
    protected BufferedImage image;

    public VideoObjectPlane(BufferedImage image) {
        this.image = image;
    }

    public abstract void encode();
}
