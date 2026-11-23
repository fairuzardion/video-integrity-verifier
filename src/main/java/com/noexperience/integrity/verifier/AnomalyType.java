package com.noexperience.integrity.verifier;

public enum AnomalyType {
    /** The reference manifest itself is internally inconsistent (broken links / wrong root). */
    MANIFEST_CHAIN_INVALID,
    /** The strict hash chain of the test video first diverges from the reference at this segment. */
    CHAIN_BREAK,
    /** Total duration differs from the reference beyond the configured tolerance. */
    DURATION_MISMATCH,
    /** Reference segments are absent from the test video: cut or trim. */
    SEGMENT_MISSING,
    /** Test segments have no counterpart in the reference: splice, insertion or appended content. */
    SEGMENT_INSERTED,
    /** Segment content changed visually (perceptual distance above the threshold). */
    SEGMENT_ALTERED,
    /** Segment bytes differ but remain perceptually similar: re-encoding or mild processing. */
    SEGMENT_REENCODED,
    /** Reference content appears at a different position on the timeline. */
    TIMESTAMP_SHIFT
}
