package com.noexperience.integrity.support;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Deterministic synthetic frames: a 9x8 grid of 10x10 blocks with seeded random luminance. */
public final class TestImages {

    private TestImages() {
    }

    public static BufferedImage blocks(long seed, int noise) {
        Random blockRandom = new Random(seed);
        int[][] level = new int[8][9];
        for (int gy = 0; gy < 8; gy++) {
            for (int gx = 0; gx < 9; gx++) {
                level[gy][gx] = 20 + blockRandom.nextInt(200);
            }
        }
        Random noiseRandom = new Random(seed * 31 + 7);
        BufferedImage image = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 90; x++) {
                int v = level[y / 10][x / 10];
                if (noise > 0) {
                    v += noiseRandom.nextInt(2 * noise + 1) - noise;
                }
                v = Math.max(0, Math.min(255, v));
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    public static BufferedImage flat(int value) {
        BufferedImage image = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 90; x++) {
                image.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }
        return image;
    }
}
