package com.noexperience.integrity.fingerprint;

import com.noexperience.integrity.segmenter.SegmentWindow;

/** A time window together with the fingerprint computed for its content. */
public record AnalyzedSegment(SegmentWindow window, SegmentFingerprint fingerprint) {
}
