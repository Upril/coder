package core;

import java.io.IOException;
import java.io.Serializable;

public class VOPHeader implements Serializable {
    private int vopType;                 // VOP type (0 = I-VOP, 1 = P-VOP)
    private int vopTimeIncrement;        // VOP time increment
    private boolean vopCoded;            // Indicates if VOP is coded
    private int vopQuant;                // Quantization parameterw
    private int vopWidth;                // VOP width in pixels
    private int vopHeight;               // VOP height in pixels

    public VOPHeader(int vopType, int vopTimeIncrement, boolean vopCoded, int vopQuant, int vopWidth, int vopHeight) {
        this.vopType = vopType;
        this.vopTimeIncrement = vopTimeIncrement;
        this.vopCoded = vopCoded;
        this.vopQuant = vopQuant;
        this.vopWidth = vopWidth;
        this.vopHeight = vopHeight;
    }

    public void encodeHeader(Bitstream bitstream) throws IOException {
        // Start Code
        bitstream.writeBits(32, 0x000001B6);  // VOP start code is typically 0x000001B6
    }

    public int getVopType() {
        return vopType;
    }

    public int getVopTimeIncrement() {
        return vopTimeIncrement;
    }

    public boolean isVopCoded() {
        return vopCoded;
    }

    public int getVopQuant() {
        return vopQuant;
    }

    public int getVopWidth() {
        return vopWidth;
    }

    public int getVopHeight() {
        return vopHeight;
    }
}
