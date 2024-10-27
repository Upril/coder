package org.encoder.utils;

public class MotionEstimator {
    private VideoObjectPlane referenceVOP;
    private VideoObjectPlane currentVOP;
    private static final int SEARCH_RANGE = 16;

    public MotionEstimator(VideoObjectPlane referenceVOP, VideoObjectPlane currentVOP) {
        this.referenceVOP = referenceVOP;
        this.currentVOP = currentVOP;
    }

    // Perform motion estimation for all macroblocks
    public void estimate() {
        for (Macroblock currentMB : currentVOP.getMacroblocks()) {
            Macroblock bestMB = findBestMatch(currentMB);
            MotionVector bestMotionVector = calculateMotionVector(currentMB,bestMB);
            currentMB.setMotionVector(bestMotionVector);
        }
    }

    // Find the best match for a macroblock in the reference VOP
//    private Macroblock findBestMatch(Macroblock currentMB) {
//        if (currentMB == null) {
//            throw new IllegalArgumentException("currentMB cannot be null");
//        }
//
//        Macroblock bestMatch = null;
//        int minError = Integer.MAX_VALUE;
//
//        int blockSize = 16; // Macroblock size
//
//        // Iterate through possible macroblocks in the reference VOP
//        for (int refY = 0; refY < referenceVOP.getHeight(); refY += blockSize) {
//            for (int refX = 0; refX < referenceVOP.getWidth(); refX += blockSize) {
//                Macroblock refMB = referenceVOP.getMacroblockAt(refX, refY);
//                if (refMB != null) {
//                    int error = calculateBlockError(currentMB, refMB);
//                    if (error < minError) {
//                        minError = error;
//                        bestMatch = refMB;
//                    }
//                }
//            }
//        }
//        return bestMatch;
//    }
    private Macroblock findBestMatch(Macroblock currentMB) {
        Macroblock bestMatch = null;
        int minError = Integer.MAX_VALUE;

        int currentX = currentMB.getStartX();
        int currentY = currentMB.getStartY();

        // Search within the defined range
        for (int refY = Math.max(0, currentY - SEARCH_RANGE); refY < Math.min(referenceVOP.getHeight(), currentY + SEARCH_RANGE); refY += 16) {
            for (int refX = Math.max(0, currentX - SEARCH_RANGE); refX < Math.min(referenceVOP.getWidth(), currentX + SEARCH_RANGE); refX += 16) {
                Macroblock refMB = referenceVOP.getMacroblockAt(refX, refY);
                if (refMB != null) {
                    int error = calculateBlockError(currentMB, refMB);
                    if (error < minError) {
                        minError = error;
                        bestMatch = refMB;
                    }
                }
            }
        }
        return bestMatch;
    }
//    private MotionVector calculateMotionVector(Macroblock currentMB, Macroblock bestMatchMB) {
//        int blockSize = 16; // Macroblock size
//        int currentX = currentMB.getStartX();
//        int currentY = currentMB.getStartY();
//        int bestMatchX = bestMatchMB.getStartX();
//        int bestMatchY = bestMatchMB.getStartY();
//
//        // Calculate motion vector
//        int mvX = bestMatchX - currentX;
//        int mvY = bestMatchY - currentY;
//
//        return new MotionVector(mvX, mvY);
//    }
    private MotionVector calculateMotionVector(Macroblock currentMB, Macroblock bestMatchMB) {
        int mvX = bestMatchMB.getStartX() - currentMB.getStartX();
        int mvY = bestMatchMB.getStartY() - currentMB.getStartY();
        return new MotionVector(mvX, mvY);
    }
    private int calculateBlockError(Macroblock mb1, Macroblock mb2) {
        int error = 0;
        int[] lum1 = mb1.getLuminance();
        int[] lum2 = mb2.getLuminance();

//        for (int i = 0; i < lum1.length; i++) {
//            int diff = lum1[i] - lum2[i];
//            error += diff * diff;
//        }
        for (int i = 0; i < lum1.length; i++) {
            int diff = lum1[i] - lum2[i];
            error += Math.abs(diff);  // Sum of Absolute Differences (SAD)
        }

        return error;
    }
}
