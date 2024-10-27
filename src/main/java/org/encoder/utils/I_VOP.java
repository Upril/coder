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

            // Encode VOP header
            header.encodeHeader(bitstream);

            // Encode macroblocks
            for (Macroblock macroblock : macroblocks) {
//                // DCT
//                int[] dctLuminance = applyDCTAndQuantize(macroblock.getLuminance(), true);
//                int[] dctChrominanceU = applyDCTAndQuantize(macroblock.getChrominanceU(), false);
//                int[] dctChrominanceV = applyDCTAndQuantize(macroblock.getChrominanceV(), false);
//
//                // Quantization
//                int[] zigZagLuminance = DCT.zigZagScan(DCT.convertTo2D(dctLuminance));
//                int[] zigZagChrominanceU = DCT.zigZagScan(DCT.convertTo2D(dctChrominanceU));
//                int[] zigZagChrominanceV = DCT.zigZagScan(DCT.convertTo2D(dctChrominanceV));
//
//                List<int[]> rleLuminance = RLE.runLengthEncode(zigZagLuminance);
//                List<int[]> rleChrominanceU = RLE.runLengthEncode(zigZagChrominanceU);
//                List<int[]> rleChrominanceV = RLE.runLengthEncode(zigZagChrominanceV);
//
//                // Bitstream encoding of processed MB
//                encodeRLEData(bitstream, rleLuminance);
//                encodeRLEData(bitstream, rleChrominanceU);
//                encodeRLEData(bitstream, rleChrominanceV);

                int[] luminance = macroblock.getLuminance();
                int[] chrominanceU = macroblock.getChrominanceU();
                int[] chrominanceV = macroblock.getChrominanceV();

                for (int value : luminance) {
                    bitstream.writeBits(8, value);
                }
                for (int value : chrominanceU) {
                    bitstream.writeBits(8, value);
                }
                for (int value : chrominanceV) {
                    bitstream.writeBits(8, value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error encoding I-VOP", e);
        }
    }
}
