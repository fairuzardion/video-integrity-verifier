package com.noexperience.integrity.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noexperience.integrity.fingerprint.AnalyzedSegment;
import com.noexperience.integrity.fingerprint.SegmentFingerprint;
import com.noexperience.integrity.segmenter.SegmentWindow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HashChainTest {

    static List<AnalyzedSegment> segments(int n) {
        List<AnalyzedSegment> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new AnalyzedSegment(new SegmentWindow(i, i * 5.0, (i + 1) * 5.0),
                    new SegmentFingerprint("sha" + i, String.format("%016x", i), 1)));
        }
        return list;
    }

    @Test
    void linksEntriesToEachOtherStartingAtGenesis() {
        List<SegmentEntry> chain = HashChain.build(segments(4));
        assertEquals(HashChain.GENESIS_HASH, chain.get(0).prevHash());
        for (int i = 1; i < chain.size(); i++) {
            assertEquals(chain.get(i - 1).chainHash(), chain.get(i).prevHash());
        }
        assertEquals(chain.get(3).chainHash(), HashChain.rootOf(chain));
        assertTrue(HashChain.findBrokenLinks(chain).isEmpty());
    }

    @Test
    void isDeterministicAndContentSensitive() {
        assertEquals(HashChain.build(segments(3)), HashChain.build(segments(3)));
        List<AnalyzedSegment> changed = segments(3);
        changed.set(1, new AnalyzedSegment(changed.get(1).window(), new SegmentFingerprint("other", "0".repeat(16), 1)));
        List<SegmentEntry> a = HashChain.build(segments(3));
        List<SegmentEntry> b = HashChain.build(changed);
        assertEquals(a.get(0).chainHash(), b.get(0).chainHash());
        assertNotEquals(a.get(1).chainHash(), b.get(1).chainHash());
        assertNotEquals(a.get(2).chainHash(), b.get(2).chainHash(), "change must cascade");
    }

    @Test
    void detectsAlteredEntry() {
        List<SegmentEntry> chain = new ArrayList<>(HashChain.build(segments(5)));
        SegmentEntry e = chain.get(2);
        chain.set(2, new SegmentEntry(e.index(), e.startSec(), e.endSec(), e.prevHash(), "forged",
                e.perceptualHash(), e.chainHash()));
        assertEquals(List.of(2), HashChain.findBrokenLinks(chain));
    }

    @Test
    void detectsTimestampChange() {
        List<SegmentEntry> chain = new ArrayList<>(HashChain.build(segments(3)));
        SegmentEntry e = chain.get(1);
        chain.set(1, new SegmentEntry(e.index(), e.startSec() + 1, e.endSec(), e.prevHash(), e.segmentSha256(),
                e.perceptualHash(), e.chainHash()));
        assertEquals(List.of(1), HashChain.findBrokenLinks(chain));
    }

    @Test
    void detectsRemovedAndReorderedEntries() {
        List<SegmentEntry> removed = new ArrayList<>(HashChain.build(segments(5)));
        removed.remove(2);
        assertEquals(2, HashChain.findBrokenLinks(removed).get(0));

        List<SegmentEntry> swapped = new ArrayList<>(HashChain.build(segments(4)));
        SegmentEntry tmp = swapped.get(1);
        swapped.set(1, swapped.get(2));
        swapped.set(2, tmp);
        assertEquals(1, HashChain.findBrokenLinks(swapped).get(0));
    }
}
