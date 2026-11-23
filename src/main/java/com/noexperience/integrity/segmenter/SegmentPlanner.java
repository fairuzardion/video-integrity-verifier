package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.InvalidInputException;
import java.util.ArrayList;
import java.util.List;

/** Splits a duration into consecutive fixed-length windows (the last one may be shorter). */
public final class SegmentPlanner {

    public static final int MAX_SEGMENTS = 100_000;

    private SegmentPlanner() {
    }

    public static List<SegmentWindow> plan(double durationSec, double segmentSeconds) {
        if (!(durationSec > 0) || Double.isInfinite(durationSec)) {
            throw new InvalidInputException("Video duration must be a positive finite number, got " + durationSec);
        }
        if (!(segmentSeconds > 0)) {
            throw new InvalidInputException("Segment length must be positive, got " + segmentSeconds);
        }
        long count = (long) Math.ceil(durationSec / segmentSeconds - 1e-9);
        if (count > MAX_SEGMENTS) {
            throw new InvalidInputException("Video would produce " + count + " segments (max " + MAX_SEGMENTS
                    + "); increase the segment length");
        }
        List<SegmentWindow> windows = new ArrayList<>((int) Math.max(count, 1));
        for (int i = 0; i < count; i++) {
            double start = round3(i * segmentSeconds);
            double end = round3(Math.min((i + 1) * segmentSeconds, durationSec));
            if (end <= start) {
                break;
            }
            windows.add(new SegmentWindow(i, start, end));
        }
        if (windows.isEmpty()) {
            throw new InvalidInputException("Video is too short to be segmented (duration " + durationSec + "s)");
        }
        return List.copyOf(windows);
    }

    static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
