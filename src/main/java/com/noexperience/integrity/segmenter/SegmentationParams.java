package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.InvalidInputException;

/**
 * Parameters that define how a video is cut into analysis segments. They are stored in the signed manifest so that
 * verification uses exactly the same sampling grid as registration.
 *
 * @param segmentSeconds length of each time window
 * @param sampleFps      how many frames per second are sampled inside a window
 * @param analysisWidth  width (px) every sampled frame is scaled to before hashing; height keeps the aspect ratio
 */
public record SegmentationParams(double segmentSeconds, double sampleFps, int analysisWidth) {

    public SegmentationParams {
        if (!(segmentSeconds >= 0.5) || segmentSeconds > 3600) {
            throw new InvalidInputException("segmentSeconds must be within 0.5..3600, got " + segmentSeconds);
        }
        if (!(sampleFps >= 0.1) || sampleFps > 60) {
            throw new InvalidInputException("sampleFps must be within 0.1..60, got " + sampleFps);
        }
        if (analysisWidth < 16 || analysisWidth > 4096) {
            throw new InvalidInputException("analysisWidth must be within 16..4096, got " + analysisWidth);
        }
    }

    /** Maximum number of sampled frames that belong to the given window. */
    public int expectedFrames(SegmentWindow window) {
        return Math.max(1, (int) Math.ceil(window.durationSec() * sampleFps - 1e-9));
    }
}
