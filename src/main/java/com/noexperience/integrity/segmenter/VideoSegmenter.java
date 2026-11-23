package com.noexperience.integrity.segmenter;

import java.nio.file.Path;

/** Abstraction over the media toolchain so the rest of the system (and the tests) does not depend on FFmpeg. */
public interface VideoSegmenter {

    /** Container duration in seconds. */
    double probeDurationSeconds(Path video);

    /**
     * Decodes the frames of {@code window}, sampled at {@code params.sampleFps()} and scaled to
     * {@code params.analysisWidth()}, into {@code outputDir} and returns them in presentation order.
     */
    ExtractedSegment extract(Path video, SegmentWindow window, SegmentationParams params, Path outputDir);
}
