package core;

import java.io.*;

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

    public void writeBytes(byte[] data) throws IOException {
        for (byte b : data) {
            writeBits(8, b & 0xFF);
        }
    }

    public int readBits(int numBits) throws IOException {
        int value = 0;
        System.out.print("Reading "+numBits);
        while (numBits > 0) {
            // Check if we need to load a new byte from the input stream
            if (bitCount == 0) {
                bitBuffer = inputStream.read(); // Read the next byte
                if (bitBuffer == -1) {
                    throw new EOFException("End of stream reached");
                }
                bitCount = 8; // Reset bit count to 8 after loading a new byte
            }

            // Determine how many bits to read from the current byte
            int bitsToRead = Math.min(numBits, bitCount);
            value <<= bitsToRead; // Shift the current value to make space for the new bits
            value |= (bitBuffer >> (bitCount - bitsToRead)) & ((1 << bitsToRead) - 1); // Extract the bits and append them to value

            // Update the bit count and number of bits left to read
            bitCount -= bitsToRead;
            numBits -= bitsToRead;
        }

        // Apply a mask to ensure the result is unsigned
        System.out.println(" bits: "+(value & ((1 << numBits) - 1)));
        return value & ((1 << numBits) - 1); // Mask to handle overflow/unsigned result
    }

    public boolean hasRemaining() {
        return inputStream.available() > 0 || bitCount > 0;
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
        if (bitCount > 0) {
            outputStream.write(bitBuffer & 0xFF);
        }
//      outputStream.flush();
        return outputStream.toByteArray();
    }
    public void writeToFile(String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(toByteArray());
        }
    }
}