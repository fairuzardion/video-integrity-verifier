package com.noexperience.integrity.chain;

import com.noexperience.integrity.fingerprint.AnalyzedSegment;
import com.noexperience.integrity.fingerprint.Sha256;
import java.util.ArrayList;
import java.util.List;

/**
 * Hash chain over ordered segments:
 * <pre>
 *   chainHash[i] = SHA256( "VIV1" | prevHash[i] | index | startMs | endMs | segmentSha256 | perceptualHash )
 *   prevHash[0]  = GENESIS_HASH,   prevHash[i] = chainHash[i-1]
 * </pre>
 * Changing, removing, inserting or reordering any segment changes every following chain hash.
 */
public final class HashChain {

    public static final String GENESIS_HASH = "0".repeat(64);
    private static final String DOMAIN = "VIV1";

    private HashChain() {
    }

    public static long millis(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    public static String computeChainHash(String prevHash, int index, double startSec, double endSec,
                                          String segmentSha256, String perceptualHash) {
        String material = String.join("|", DOMAIN, prevHash, Integer.toString(index),
                Long.toString(millis(startSec)), Long.toString(millis(endSec)), segmentSha256, perceptualHash);
        return Sha256.hashHex(material);
    }

    /** Builds the chain for the analyzed segments in the order given. */
    public static List<SegmentEntry> build(List<AnalyzedSegment> segments) {
        List<SegmentEntry> entries = new ArrayList<>(segments.size());
        String prev = GENESIS_HASH;
        for (int i = 0; i < segments.size(); i++) {
            AnalyzedSegment s = segments.get(i);
            String sha = s.fingerprint().segmentSha256();
            String phash = s.fingerprint().perceptualHash();
            String chainHash = computeChainHash(prev, i, s.window().startSec(), s.window().endSec(), sha, phash);
            entries.add(new SegmentEntry(i, s.window().startSec(), s.window().endSec(), prev, sha, phash, chainHash));
            prev = chainHash;
        }
        return List.copyOf(entries);
    }

    /** Hash of the last link (the chain head), or the genesis hash for an empty chain. */
    public static String rootOf(List<SegmentEntry> entries) {
        return entries.isEmpty() ? GENESIS_HASH : entries.get(entries.size() - 1).chainHash();
    }

    /**
     * Positions of entries that are not internally consistent: wrong index, {@code prevHash} not equal to the
     * previous link, or {@code chainHash} not matching the entry's own fields.
     */
    public static List<Integer> findBrokenLinks(List<SegmentEntry> entries) {
        List<Integer> broken = new ArrayList<>();
        String expectedPrev = GENESIS_HASH;
        for (int i = 0; i < entries.size(); i++) {
            SegmentEntry e = entries.get(i);
            boolean ok = e.index() == i
                    && expectedPrev.equals(e.prevHash())
                    && e.chainHash() != null
                    && e.chainHash().equals(computeChainHash(
                            String.valueOf(e.prevHash()), e.index(), e.startSec(), e.endSec(),
                            String.valueOf(e.segmentSha256()), String.valueOf(e.perceptualHash())));
            if (!ok) {
                broken.add(i);
            }
            expectedPrev = e.chainHash();
        }
        return broken;
    }
}
