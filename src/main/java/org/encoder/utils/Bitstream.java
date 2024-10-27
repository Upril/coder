package org.encoder.utils;

import java.io.*;
import java.util.List;

public class Bitstream implements Serializable{
    private ByteArrayOutputStream outputStream;
    private ByteArrayInputStream inputStream;
    private int bitBuffer;
    private int bitCount;

    public Bitstream() {
        this.outputStream = new ByteArrayOutputStream();
        this.bitBuffer = 0;
        this.bitCount = 0;
    }
    public Bitstream(byte[] data) {
        this.inputStream = new ByteArrayInputStream(data);
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

    // Method to read 'n' bits from the bitstream
    public int readBits(int numBits) throws IOException {
        int value = 0;
        while (numBits > 0) {
            if (bitCount == 0) {
                bitBuffer = inputStream.read();
                if (bitBuffer == -1) {
                    throw new EOFException("End of stream reached");
                }
                bitCount = 8;
            }

            int bitsToRead = Math.min(numBits, bitCount);
            value <<= bitsToRead;
            value |= (bitBuffer >> (bitCount - bitsToRead)) & ((1 << bitsToRead) - 1);
            bitCount -= bitsToRead;
            numBits -= bitsToRead;
        }
        return value;
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
}