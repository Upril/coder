package org.encoder.utils;

import java.io.IOException;

public class VOPHeader {
    private int vopType;                 // VOP type (0 = I-VOP, 1 = P-VOP)
    private int vopTimeIncrement;        // VOP time increment
    private boolean vopCoded;            // Indicates if VOP is coded
    private int vopQuant;                // Quantization parameter
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

        // VOP Type (2 bits)
        bitstream.writeBits(2, vopType);

        // VOP Time Increment (Modulo Time Base)
        bitstream.writeBits(16, vopTimeIncrement);  // Assuming 16 bits for time increment

        // VOP Coded (1 bit)
        bitstream.writeBits(1, vopCoded ? 1 : 0);

        // VOP Quantization Parameter (5 bits)
        bitstream.writeBits(5, vopQuant);

        // VOP Width (13 bits)
        bitstream.writeBits(13, vopWidth);

        // VOP Height (13 bits)
        bitstream.writeBits(13, vopHeight);

        // Padding or other flags can be added here as needed
    }
}
