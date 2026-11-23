package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.InvalidInputException;

/** A fixed time window [startSec, endSec) of the video timeline. */
public record SegmentWindow(int index, double startSec, double endSec) {

    public SegmentWindow {
        if (index < 0) {
            throw new InvalidInputException("Segment index must be >= 0, got " + index);
        }
        if (!(startSec >= 0) || !(endSec > startSec)) {
            throw new InvalidInputException("Invalid segment window [" + startSec + ", " + endSec + ")");
        }
    }

    public double durationSec() {
        return endSec - startSec;
    }
}
