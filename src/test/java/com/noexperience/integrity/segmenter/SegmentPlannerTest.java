package com.noexperience.integrity.segmenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.noexperience.integrity.common.InvalidInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentPlannerTest {

    @Test
    void lastWindowIsShorter() {
        List<SegmentWindow> w = SegmentPlanner.plan(23.4, 5);
        assertEquals(5, w.size());
        assertEquals(20.0, w.get(4).startSec());
        assertEquals(23.4, w.get(4).endSec());
    }

    @Test
    void exactMultipleHasNoEmptyTail() {
        List<SegmentWindow> w = SegmentPlanner.plan(10, 5);
        assertEquals(2, w.size());
        assertEquals(10.0, w.get(1).endSec());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(InvalidInputException.class, () -> SegmentPlanner.plan(0, 5));
        assertThrows(InvalidInputException.class, () -> SegmentPlanner.plan(10, 0));
        assertThrows(InvalidInputException.class, () -> new SegmentationParams(0.1, 2, 320));
    }
}
