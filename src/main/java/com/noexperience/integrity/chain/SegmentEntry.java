package com.noexperience.integrity.chain;

/**
 * One link of the hash chain.
 *
 * @param index         zero-based position of the segment
 * @param startSec      start of the time window (seconds)
 * @param endSec        end of the time window (seconds)
 * @param prevHash      {@code chainHash} of the previous entry ({@link HashChain#GENESIS_HASH} for the first)
 * @param segmentSha256 strict content hash of the segment
 * @param perceptualHash concatenated per-frame dHash values
 * @param chainHash     SHA-256 binding all the fields above together
 */
public record SegmentEntry(
        int index,
        double startSec,
        double endSec,
        String prevHash,
        String segmentSha256,
        String perceptualHash,
        String chainHash) {
}
