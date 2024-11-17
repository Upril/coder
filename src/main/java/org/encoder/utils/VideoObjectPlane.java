package org.encoder.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class VideoObjectPlane implements Serializable {
    protected List<Macroblock> macroblocks;
    protected VOPHeader header;
    private int width;
    private int height;

    public List<Macroblock> getMacroblocks() {
        return macroblocks;
    }
    public Macroblock getMacroblockAt(int x, int y) {
        for (Macroblock mb : macroblocks) {
            if (mb.getStartX() == x && mb.getStartY() == y) {
                return mb;
            }
        }
        return null;
    }
    public int getWidth() {
        return width;
    }

    // Get the height of the VOP
    public int getHeight() {
        return height;
    }

    public VideoObjectPlane(BufferedImage image) {
        this.macroblocks = new ArrayList<>();
        splitIntoMacroblocks(image);
        this.width = image.getWidth();
        this.height = image.getHeight();
    }
    private void splitIntoMacroblocks(BufferedImage image){
        int width = image.getWidth();
        int height = image.getHeight();

        for(int y = 0; y<height; y+=16){
            for(int x = 0; x<width; x+=16){
                macroblocks.add(new Macroblock(image, x, y));
            }
        }
    }
    public abstract void encode(Bitstream bitstream);
}
