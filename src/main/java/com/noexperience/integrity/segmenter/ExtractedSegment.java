package com.noexperience.integrity.segmenter;

import java.nio.file.Path;
import java.util.List;

/** Ordered segment metadata plus the frame image files extracted for it. */
public record ExtractedSegment(SegmentWindow window, List<Path> framePaths) {

    public ExtractedSegment {
        framePaths = List.copyOf(framePaths);
    }

    public int index() {
        return window.index();
    }

    public double startSec() {
        return window.startSec();
    }

    public double endSec() {
        return window.endSec();
    }
}
