package com.noexperience.integrity.chain;

import java.util.List;
import java.util.Objects;

/**
 * The reference description of a video: global metadata, the sampling parameters and the hash chain.
 * This is the object that is canonically serialized and signed.
 */
public record Manifest(
        String manifestId,
        int formatVersion,
        String videoName,
        String videoSha256,
        double durationSec,
        double segmentDurationSec,
        double sampleFps,
        int analysisWidth,
        String createdAt,
        String signatureAlgorithm,
        String keyId,
        List<SegmentEntry> segments,
        String chainRootHash) {

    public static final int CURRENT_FORMAT_VERSION = 1;

    public Manifest {
        Objects.requireNonNull(manifestId, "manifestId");
        Objects.requireNonNull(videoName, "videoName");
        Objects.requireNonNull(videoSha256, "videoSha256");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(signatureAlgorithm, "signatureAlgorithm");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(chainRootHash, "chainRootHash");
        segments = List.copyOf(segments);
    }
}
