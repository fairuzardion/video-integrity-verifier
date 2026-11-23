package com.noexperience.integrity.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.segmenter.ExtractedSegment;
import com.noexperience.integrity.segmenter.SegmentPlanner;
import com.noexperience.integrity.segmenter.SegmentWindow;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.segmenter.VideoSegmenter;
import com.noexperience.integrity.support.TestImages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoAnalyzerTest {

    @TempDir
    Path dir;

    final SegmentationParams params = new SegmentationParams(1, 2, 320);

    private long leftovers() throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void fingerprintsEverySegmentAndCleansUp() throws Exception {
        VideoSegmenter segmenter = mock(VideoSegmenter.class);
        when(segmenter.extract(any(), any(), any(), any())).thenAnswer(inv -> {
            SegmentWindow w = inv.getArgument(1);
            Path out = inv.getArgument(3);
            Path frame = out.resolve("frame_000001.png");
            ImageIO.write(TestImages.blocks(w.index(), 0), "png", frame.toFile());
            return new ExtractedSegment(w, List.of(frame));
        });
        VideoAnalyzer analyzer = new VideoAnalyzer(segmenter, new SegmentFingerprinter(), dir.toString());
        List<SegmentWindow> windows = SegmentPlanner.plan(3, 1);

        List<AnalyzedSegment> result = analyzer.fingerprintWindows(dir.resolve("v.mp4"), params, windows);

        assertEquals(3, result.size());
        verify(segmenter, times(3)).extract(any(), any(), any(), any());
        assertEquals(64, result.get(0).fingerprint().segmentSha256().length());
        assertEquals(0, leftovers(), "temporary workspace must be removed");
    }

    @Test
    void cleansUpWhenExtractionFails() throws Exception {
        VideoSegmenter segmenter = mock(VideoSegmenter.class);
        when(segmenter.extract(any(), any(), any(), any())).thenThrow(new MediaProcessingException("boom"));
        VideoAnalyzer analyzer = new VideoAnalyzer(segmenter, new SegmentFingerprinter(), dir.toString());
        assertThrows(MediaProcessingException.class,
                () -> analyzer.fingerprintWindows(dir.resolve("v.mp4"), params, SegmentPlanner.plan(2, 1)));
        assertEquals(0, leftovers());
    }

    @Test
    void failsWhenNoFramesDecoded() {
        VideoSegmenter segmenter = mock(VideoSegmenter.class);
        when(segmenter.extract(any(), any(), any(), any()))
                .thenAnswer(inv -> new ExtractedSegment(inv.getArgument(1), List.of()));
        VideoAnalyzer analyzer = new VideoAnalyzer(segmenter, new SegmentFingerprinter(), dir.toString());
        assertThrows(MediaProcessingException.class,
                () -> analyzer.fingerprintWindows(dir.resolve("v.mp4"), params, SegmentPlanner.plan(2, 1)));
    }
}
