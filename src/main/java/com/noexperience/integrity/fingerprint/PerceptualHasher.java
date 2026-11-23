package com.noexperience.integrity.fingerprint;

import java.awt.image.BufferedImage;

/**
 * Difference hash (dHash): the image is reduced to a 9x8 grid of average luminance values (box filter) and each of the
 * 8 rows contributes 8 bits, "1" when a cell is brighter than its right neighbour. Visually similar images (e.g. after
 * re-encoding, mild scaling or brightness shifts) produce hashes with a small Hamming distance, while different content
 * yields roughly 32 differing bits out of 64.
 */
public final class PerceptualHasher {

    public static final int GRID_WIDTH = 9;
    public static final int GRID_HEIGHT = 8;
    public static final int HASH_BITS = 64;
    public static final int HEX_LENGTH = 16;

    private PerceptualHasher() {
    }

    public static long dHash(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < GRID_WIDTH || h < GRID_HEIGHT) {
            throw new IllegalArgumentException("Image too small for dHash: " + w + "x" + h
                    + " (minimum " + GRID_WIDTH + "x" + GRID_HEIGHT + ")");
        }
        long[][] sum = new long[GRID_HEIGHT][GRID_WIDTH];
        long[][] count = new long[GRID_HEIGHT][GRID_WIDTH];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int gy = (int) ((long) y * GRID_HEIGHT / h);
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int gx = (int) ((long) x * GRID_WIDTH / w);
                int p = row[x];
                int luma = 299 * ((p >> 16) & 0xFF) + 587 * ((p >> 8) & 0xFF) + 114 * (p & 0xFF);
                sum[gy][gx] += luma;
                count[gy][gx]++;
            }
        }
        long hash = 0L;
        for (int gy = 0; gy < GRID_HEIGHT; gy++) {
            for (int gx = 0; gx < GRID_WIDTH - 1; gx++) {
                double left = (double) sum[gy][gx] / count[gy][gx];
                double right = (double) sum[gy][gx + 1] / count[gy][gx + 1];
                hash = (hash << 1) | (left > right ? 1L : 0L);
            }
        }
        return hash;
    }

    /** Number of differing bits between two 64-bit hashes. */
    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    public static String toHex(long hash) {
        return String.format("%016x", hash);
    }

    public static long fromHex(String hex) {
        if (hex == null || hex.length() != HEX_LENGTH) {
            throw new IllegalArgumentException("A perceptual hash must be " + HEX_LENGTH + " hex characters: " + hex);
        }
        return Long.parseUnsignedLong(hex, 16);
    }

    public static int hamming(String hexA, String hexB) {
        return hamming(fromHex(hexA), fromHex(hexB));
    }
}
