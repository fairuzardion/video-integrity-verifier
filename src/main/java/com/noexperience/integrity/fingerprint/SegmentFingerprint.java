package com.noexperience.integrity.fingerprint;

/**
 * @param segmentSha256  SHA-256 over the decoded pixel content of every sampled frame (strict content hash)
 * @param perceptualHash concatenated per-frame dHash values (see {@link SegmentPerceptualHash})
 * @param frameCount     number of sampled frames
 */
public record SegmentFingerprint(String segmentSha256, String perceptualHash, int frameCount) {
}
