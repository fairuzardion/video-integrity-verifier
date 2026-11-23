package com.noexperience.integrity.fingerprint;

import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.segmenter.ExtractedSegment;
import com.noexperience.integrity.segmenter.SegmentWindow;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.segmenter.TempWorkspace;
import com.noexperience.integrity.segmenter.VideoSegmenter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Drives segment extraction and fingerprinting. Frames of one segment at a time are kept on disk and removed right
 * after hashing, so disk usage stays bounded for arbitrarily long videos; the whole workspace is deleted on any failure.
 */
@Component
public class VideoAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(VideoAnalyzer.class);

    private final VideoSegmenter segmenter;
    private final SegmentFingerprinter fingerprinter;
    private final String tempBaseDir;

    @Autowired
    public VideoAnalyzer(VideoSegmenter segmenter, SegmentFingerprinter fingerprinter, IntegrityProperties properties) {
        this(segmenter, fingerprinter, properties.ffmpeg().tempDir());
    }

    public VideoAnalyzer(VideoSegmenter segmenter, SegmentFingerprinter fingerprinter, String tempBaseDir) {
        this.segmenter = segmenter;
        this.fingerprinter = fingerprinter;
        this.tempBaseDir = tempBaseDir;
    }

    public double probeDuration(Path video) {
        return segmenter.probeDurationSeconds(video);
    }

    public List<AnalyzedSegment> fingerprintWindows(Path video, SegmentationParams params, List<SegmentWindow> windows) {
        List<AnalyzedSegment> result = new ArrayList<>(windows.size());
        long totalFrames = 0;
        try (TempWorkspace workspace = TempWorkspace.create(tempBaseDir)) {
            for (SegmentWindow window : windows) {
                Path dir = workspace.newSubDir("segment-" + window.index());
                ExtractedSegment extracted = segmenter.extract(video, window, params, dir);
                SegmentFingerprint fingerprint = fingerprinter.fingerprint(extracted.framePaths());
                TempWorkspace.deleteRecursively(dir);
                totalFrames += fingerprint.frameCount();
                result.add(new AnalyzedSegment(window, fingerprint));
                log.debug("Segment {} [{}-{}] -> {} frames, sha256={}", window.index(), window.startSec(),
                        window.endSec(), fingerprint.frameCount(), fingerprint.segmentSha256());
            }
        }
        if (totalFrames == 0) {
            throw new MediaProcessingException("No video frames could be decoded from " + video.getFileName()
                    + ". The file may not contain a video stream.");
        }
        return result;
    }
}
