package org.encoder.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RLE {
    public static List<int[]> runLengthEncode(int[] input) {
        List<int[]> output = new ArrayList<>();
        int zeroCount = 0;

        for (int i = 0; i < input.length; i++) {
            if (input[i] == 0) {
                zeroCount++;
            } else {
                output.add(new int[] { zeroCount, input[i] });
                zeroCount = 0;
            }
        }

        // Handle trailing zeros
        if (zeroCount > 0) {
            output.add(new int[] { zeroCount, 0 });
        }

        return output;
    }
    public static int[] inverseRLE(List<int[]> rleData) {
        // Use a dynamic list to accumulate the output values
        List<Integer> output = new ArrayList<>();

        // Iterate through each run in the RLE data
        for (int[] run : rleData) {
            int zeroCount = run[0]; // Number of leading zeros
            int value = run[1];     // Actual value after the zeros

            // Add zeros
            for (int i = 0; i < zeroCount; i++) {
                output.add(0); // Add zeros based on run length
            }

            // Add the value
            output.add(value);
        }

        // Convert the List<Integer> to an int[]
        return output.stream().mapToInt(i -> i).toArray();
    }
    public static int[] runLengthDecode(List<int[]> rleData) {
        List<Integer> output = new ArrayList<>();

        for (int[] run : rleData) {
            int zeroCount = run[0]; // Number of leading zeros
            int value = run[1];     // Value after the zeros

            // Add the specified number of zeros
            for (int i = 0; i < zeroCount; i++) {
                output.add(0);
            }

            // Add the actual value (if non-zero)
            output.add(value);
        }

        // Convert the List<Integer> back to an int[]
        return output.stream().mapToInt(i -> i).toArray();
    }
    public static List<int[]> decodeRLEData(Bitstream bitstream) throws IOException {
        List<int[]> rleData = new ArrayList<>();

        // Loop to extract run-length and value pairs
        while (true) {
            // Read the run-length (number of leading zeros)
            int runLength = bitstream.readBits(8);  // Assuming 8 bits for run-length

            // Read the value following the zeros
            int value = bitstream.readBits(8);  // Assuming 8 bits for the value

            // Stop condition (if needed) - adjust based on your encoding scheme
            if (runLength == 0 && value == 0) {
                break;  // End of RLE data for this block
            }

            rleData.add(new int[] { runLength, value });
        }

        return rleData;
    }
}
