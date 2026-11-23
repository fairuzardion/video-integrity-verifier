package com.noexperience.integrity.verifier;

import java.util.List;

/**
 * One detected problem. Segment index lists are exact; time ranges are the (segment-granular) approximate location,
 * expressed on the reference timeline and/or the test timeline depending on the anomaly type.
 *
 * @param referenceSegments  affected segment indices of the reference manifest
 * @param testSegments       affected segment indices of the analyzed video
 * @param maxHammingDistance largest per-frame perceptual distance in the affected segments (content anomalies)
 * @param shiftSec           timeline shift (test minus reference) for {@code TIMESTAMP_SHIFT} / {@code DURATION_MISMATCH}
 */
public record Anomaly(
        AnomalyType type,
        Severity severity,
        List<Integer> referenceSegments,
        List<Integer> testSegments,
        Double referenceStartSec,
        Double referenceEndSec,
        Double testStartSec,
        Double testEndSec,
        Integer maxHammingDistance,
        Double shiftSec,
        String description) {

    public Anomaly {
        referenceSegments = referenceSegments == null ? List.of() : List.copyOf(referenceSegments);
        testSegments = testSegments == null ? List.of() : List.copyOf(testSegments);
    }
}
