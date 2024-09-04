package org.encoder.utils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class Bitstream {
    private ByteArrayOutputStream outputStream;
    private int bitBuffer;
    private int bitCount;

    public Bitstream() {
        this.outputStream = new ByteArrayOutputStream();
        this.bitBuffer = 0;
        this.bitCount = 0;
    }

    public void writeBits(int numBits, int value) throws IOException {
        if (numBits < 0 || numBits > 32) {
            throw new IllegalArgumentException("numBits must be between 0 and 32");
        }
        long maxValue = (1L << numBits) - 1;
        if (value < 0 || value > maxValue) {
            throw new IllegalArgumentException("value out of range for numBits");
        }

        bitBuffer |= (value << bitCount);
        bitCount += numBits;

        while (bitCount >= 8) {
            outputStream.write(bitBuffer & 0xFF);
            bitBuffer >>= 8;
            bitCount -= 8;
        }
    }

    public void flush() throws IOException {
        if (bitCount > 0) {
            outputStream.write(bitBuffer & 0xFF);
            bitBuffer = 0;
            bitCount = 0;
        }
        outputStream.flush();
    }

    public byte[] toByteArray() {
        try {
            // Write remaining bits
            if (bitCount > 0) {
                outputStream.write(bitBuffer & 0xFF);
            }
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error flushing bitstream", e);
        }
    }
    public void writeToFile(String filename) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(filename)) {
            fileOutputStream.write(toByteArray());
        }
    }
}
