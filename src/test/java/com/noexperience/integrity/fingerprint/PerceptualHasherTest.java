package com.noexperience.integrity.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noexperience.integrity.support.TestImages;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PerceptualHasherTest {

    @Test
    void identicalImagesHaveZeroDistance() {
        long a = PerceptualHasher.dHash(TestImages.blocks(1, 0));
        long b = PerceptualHasher.dHash(TestImages.blocks(1, 0));
        assertEquals(a, b);
        assertEquals(0, PerceptualHasher.hamming(a, b));
    }

    @Test
    void mildNoiseKeepsHashClose() {
        long clean = PerceptualHasher.dHash(TestImages.blocks(7, 0));
        long noisy = PerceptualHasher.dHash(TestImages.blocks(7, 3));
        assertTrue(PerceptualHasher.hamming(clean, noisy) <= 3);
    }

    @Test
    void differentContentIsFarApart() {
        long a = PerceptualHasher.dHash(TestImages.blocks(1, 0));
        long b = PerceptualHasher.dHash(TestImages.blocks(2, 0));
        assertTrue(PerceptualHasher.hamming(a, b) > 10);
    }

    @Test
    void flatImageHashesToZero() {
        assertEquals(0L, PerceptualHasher.dHash(TestImages.flat(128)));
    }

    @Test
    void tooSmallImageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PerceptualHasher.dHash(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));
    }

    @Test
    void hammingBasics() {
        assertEquals(0, PerceptualHasher.hamming(5L, 5L));
        assertEquals(64, PerceptualHasher.hamming(0L, -1L));
        assertEquals(1, PerceptualHasher.hamming(0b1000L, 0b0000L));
        assertEquals(PerceptualHasher.hamming(12345L, 999L), PerceptualHasher.hamming(999L, 12345L));
    }

    @Test
    void hexRoundTrip() {
        for (long v : new long[] {0L, -1L, 0x0123456789abcdefL, Long.MIN_VALUE}) {
            String hex = PerceptualHasher.toHex(v);
            assertEquals(16, hex.length());
            assertEquals(v, PerceptualHasher.fromHex(hex));
        }
        assertThrows(IllegalArgumentException.class, () -> PerceptualHasher.fromHex("abc"));
    }

    @Test
    void segmentHashUsesMaxFrameDistanceAndFrameCount() {
        String a = SegmentPerceptualHash.encode(new long[] {0L, 0L});
        String b = SegmentPerceptualHash.encode(new long[] {0L, 0b111L});
        String c = SegmentPerceptualHash.encode(new long[] {0L});
        assertEquals(0, SegmentPerceptualHash.distance(a, a));
        assertEquals(3, SegmentPerceptualHash.distance(a, b));
        assertEquals(SegmentPerceptualHash.MAX_DISTANCE, SegmentPerceptualHash.distance(a, c));
        assertEquals(2, SegmentPerceptualHash.frameCount(a));
        assertThrows(IllegalArgumentException.class, () -> SegmentPerceptualHash.decode("zz"));
    }
}
