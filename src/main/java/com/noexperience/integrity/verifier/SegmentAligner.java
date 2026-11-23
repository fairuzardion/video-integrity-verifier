package com.noexperience.integrity.verifier;

import com.noexperience.integrity.chain.SegmentEntry;
import com.noexperience.integrity.fingerprint.SegmentPerceptualHash;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aligns the reference segment sequence with the test segment sequence (global alignment / edit distance), so that a
 * cut or an insertion is reported as such instead of making every following segment look modified.
 *
 * <p>Costs: exact match 0, perceptually similar {@code 10 + 2 * distance} (capped at 90, so closer matches win
 * ties between candidate alignments), in-place substitution of altered content 100, gap (missing / extra segment) 100.
 * Remaining ties prefer the diagonal, then "missing", so ambiguity is resolved by reporting divergence as late as possible.
 */
public final class SegmentAligner {

    private static final Logger log = LoggerFactory.getLogger(SegmentAligner.class);

    static final int COST_EXACT = 0;
    static final int COST_SIMILAR = 10;
    static final int COST_ALTERED = 100;
    static final int COST_GAP = 100;

    public enum PairKind { EXACT, SIMILAR, ALTERED, MISSING, EXTRA }

    /**
     * @param referenceIndex position in the reference sequence, -1 for an extra test segment
     * @param testIndex      position in the test sequence, -1 for a missing reference segment
     * @param distance       perceptual distance for matched pairs, 0 otherwise
     */
    public record AlignedPair(int referenceIndex, int testIndex, PairKind kind, int distance) {
    }

    private record Profile(String[] sha, long[][] hashes) {
        static Profile of(List<SegmentEntry> entries) {
            String[] sha = new String[entries.size()];
            long[][] hashes = new long[entries.size()][];
            for (int i = 0; i < sha.length; i++) {
                sha[i] = entries.get(i).segmentSha256();
                hashes[i] = SegmentPerceptualHash.decode(entries.get(i).perceptualHash());
            }
            return new Profile(sha, hashes);
        }
    }

    private final int hammingThreshold;
    private final long maxCells;

    public SegmentAligner(int hammingThreshold, long maxCells) {
        this.hammingThreshold = hammingThreshold;
        this.maxCells = maxCells;
    }

    public List<AlignedPair> align(List<SegmentEntry> reference, List<SegmentEntry> test) {
        int n = reference.size();
        int m = test.size();
        Profile ref = Profile.of(reference);
        Profile tst = Profile.of(test);

        if ((long) (n + 1) * (m + 1) > maxCells) {
            log.warn("Alignment matrix of {}x{} exceeds integrity.verifier.max-alignment-cells={}; "
                    + "falling back to positional comparison (cuts/insertions will appear as shifted content)",
                    n, m, maxCells);
            return positional(ref, tst, n, m);
        }

        int width = m + 1;
        int[] cost = new int[(n + 1) * width];
        for (int j = m - 1; j >= 0; j--) {
            cost[n * width + j] = (m - j) * COST_GAP;
        }
        for (int i = n - 1; i >= 0; i--) {
            cost[i * width + m] = (n - i) * COST_GAP;
        }
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                int diagonal = substitutionCost(ref, i, tst, j) + cost[(i + 1) * width + j + 1];
                int missing = COST_GAP + cost[(i + 1) * width + j];
                int extra = COST_GAP + cost[i * width + j + 1];
                cost[i * width + j] = Math.min(diagonal, Math.min(missing, extra));
            }
        }

        List<AlignedPair> pairs = new ArrayList<>(Math.max(n, m));
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            int here = cost[i * width + j];
            if (i < n && j < m && here == substitutionCost(ref, i, tst, j) + cost[(i + 1) * width + j + 1]) {
                pairs.add(matched(ref, i, tst, j));
                i++;
                j++;
            } else if (i < n && here == COST_GAP + cost[(i + 1) * width + j]) {
                pairs.add(new AlignedPair(i, -1, PairKind.MISSING, 0));
                i++;
            } else if (j < m) {
                pairs.add(new AlignedPair(-1, j, PairKind.EXTRA, 0));
                j++;
            } else {
                throw new IllegalStateException("Alignment traceback failed at (" + i + "," + j + ")");
            }
        }
        return pairs;
    }

    private List<AlignedPair> positional(Profile ref, Profile tst, int n, int m) {
        List<AlignedPair> pairs = new ArrayList<>(Math.max(n, m));
        int common = Math.min(n, m);
        for (int k = 0; k < common; k++) {
            pairs.add(matched(ref, k, tst, k));
        }
        for (int k = common; k < n; k++) {
            pairs.add(new AlignedPair(k, -1, PairKind.MISSING, 0));
        }
        for (int k = common; k < m; k++) {
            pairs.add(new AlignedPair(-1, k, PairKind.EXTRA, 0));
        }
        return pairs;
    }

    private int substitutionCost(Profile ref, int i, Profile tst, int j) {
        if (ref.sha()[i].equals(tst.sha()[j])) {
            return COST_EXACT;
        }
        int distance = SegmentPerceptualHash.distance(ref.hashes()[i], tst.hashes()[j]);
        return distance <= hammingThreshold ? Math.min(90, COST_SIMILAR + 2 * distance) : COST_ALTERED;
    }

    private AlignedPair matched(Profile ref, int i, Profile tst, int j) {
        if (ref.sha()[i].equals(tst.sha()[j])) {
            return new AlignedPair(i, j, PairKind.EXACT, 0);
        }
        int distance = SegmentPerceptualHash.distance(ref.hashes()[i], tst.hashes()[j]);
        return new AlignedPair(i, j, distance <= hammingThreshold ? PairKind.SIMILAR : PairKind.ALTERED, distance);
    }
}
