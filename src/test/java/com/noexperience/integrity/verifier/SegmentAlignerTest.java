package com.noexperience.integrity.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noexperience.integrity.chain.SegmentEntry;
import com.noexperience.integrity.fingerprint.PerceptualHasher;
import com.noexperience.integrity.verifier.SegmentAligner.AlignedPair;
import com.noexperience.integrity.verifier.SegmentAligner.PairKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentAlignerTest {

    static final long A = 0L;
    static final long B = -1L;
    static final long C = 0x00000000FFFFFFFFL;
    static final long D = 0xFFFFFFFF00000000L;
    static final long X = 0x5555555555555555L;

    static SegmentEntry entry(int i, String sha, long hash) {
        return new SegmentEntry(i, i, i + 1, "p", sha, PerceptualHasher.toHex(hash), "c");
    }

    static List<SegmentEntry> seq(Object... pairs) {
        List<SegmentEntry> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(entry(i / 2, (String) pairs[i], (Long) pairs[i + 1]));
        }
        return list;
    }

    final SegmentAligner aligner = new SegmentAligner(10, 1_000_000);

    @Test
    void identicalSequencesAreAllExact() {
        List<AlignedPair> pairs = aligner.align(seq("a", A, "b", B), seq("a", A, "b", B));
        assertEquals(List.of(PairKind.EXACT, PairKind.EXACT), pairs.stream().map(AlignedPair::kind).toList());
    }

    @Test
    void detectsDeletionInTheMiddle() {
        List<AlignedPair> pairs = aligner.align(seq("a", A, "b", B, "c", C, "d", D), seq("a", A, "c", C, "d", D));
        assertEquals(List.of(PairKind.EXACT, PairKind.MISSING, PairKind.EXACT, PairKind.EXACT),
                pairs.stream().map(AlignedPair::kind).toList());
        assertEquals(1, pairs.get(1).referenceIndex());
        assertEquals(1, pairs.get(2).testIndex());
    }

    @Test
    void detectsInsertion() {
        List<AlignedPair> pairs = aligner.align(seq("a", A, "b", B), seq("a", A, "x", X, "b", B));
        assertEquals(List.of(PairKind.EXACT, PairKind.EXTRA, PairKind.EXACT),
                pairs.stream().map(AlignedPair::kind).toList());
        assertEquals(1, pairs.get(1).testIndex());
    }

    @Test
    void separatesSimilarFromAltered() {
        List<AlignedPair> pairs = aligner.align(
                seq("a", A, "b", B), seq("a2", 0b111L, "b2", 0x00FF00FF00FF00FFL));
        assertEquals(PairKind.SIMILAR, pairs.get(0).kind());
        assertEquals(3, pairs.get(0).distance());
        assertEquals(PairKind.ALTERED, pairs.get(1).kind());
    }

    @Test
    void tailTrimIsReportedAsMissingAtEnd() {
        List<AlignedPair> pairs = aligner.align(seq("a", A, "b", B, "c", C), seq("a", A));
        assertEquals(PairKind.MISSING, pairs.get(2).kind());
        assertEquals(PairKind.MISSING, pairs.get(1).kind());
    }

    @Test
    void fallsBackToPositionalComparisonForHugeMatrices() {
        List<AlignedPair> pairs = new SegmentAligner(10, 1).align(seq("a", A, "b", B, "c", C), seq("b", B, "c", C));
        assertEquals(3, pairs.size());
        assertEquals(PairKind.ALTERED, pairs.get(0).kind());
        assertEquals(PairKind.MISSING, pairs.get(2).kind());
    }
}
