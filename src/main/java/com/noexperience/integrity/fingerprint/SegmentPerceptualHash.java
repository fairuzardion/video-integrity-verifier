package com.noexperience.integrity.fingerprint;

/**
 * The perceptual hash of a segment is the concatenation of the 16-hex-digit dHash of each sampled frame, in order.
 * The distance between two segment hashes is the <em>maximum</em> per-frame Hamming distance, so a single swapped or
 * altered frame is not averaged away. Segments with a different number of frames are maximally distant.
 */
public final class SegmentPerceptualHash {

    public static final int MAX_DISTANCE = PerceptualHasher.HASH_BITS;

    private SegmentPerceptualHash() {
    }

    public static String encode(long[] frameHashes) {
        StringBuilder sb = new StringBuilder(frameHashes.length * PerceptualHasher.HEX_LENGTH);
        for (long h : frameHashes) {
            sb.append(PerceptualHasher.toHex(h));
        }
        return sb.toString();
    }

    public static long[] decode(String encoded) {
        if (encoded == null || encoded.length() % PerceptualHasher.HEX_LENGTH != 0) {
            throw new IllegalArgumentException("Malformed segment perceptual hash (length must be a multiple of "
                    + PerceptualHasher.HEX_LENGTH + ")");
        }
        long[] hashes = new long[encoded.length() / PerceptualHasher.HEX_LENGTH];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = PerceptualHasher.fromHex(
                    encoded.substring(i * PerceptualHasher.HEX_LENGTH, (i + 1) * PerceptualHasher.HEX_LENGTH));
        }
        return hashes;
    }

    public static int frameCount(String encoded) {
        return encoded.length() / PerceptualHasher.HEX_LENGTH;
    }

    public static int distance(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        return distance(decode(a), decode(b));
    }

    public static int distance(long[] a, long[] b) {
        if (a.length != b.length) {
            return MAX_DISTANCE;
        }
        int max = 0;
        for (int i = 0; i < a.length; i++) {
            max = Math.max(max, PerceptualHasher.hamming(a[i], b[i]));
        }
        return max;
    }
}
