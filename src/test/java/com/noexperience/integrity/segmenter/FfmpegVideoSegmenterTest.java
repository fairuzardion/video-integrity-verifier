package com.noexperience.integrity.segmenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noexperience.integrity.chain.SignatureAlgorithm;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.config.IntegrityProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class FfmpegVideoSegmenterTest {

    @TempDir
    Path dir;

    ProcessRunner runner;
    FfmpegVideoSegmenter segmenter;
    Path video;

    @BeforeEach
    void setUp() throws Exception {
        runner = mock(ProcessRunner.class);
        IntegrityProperties props = new IntegrityProperties(
                new IntegrityProperties.Ffmpeg("ffmpeg-bin", "ffprobe-bin", 60, null),
                new IntegrityProperties.Segmentation(5, 2, 320),
                new IntegrityProperties.Verifier(10, 0.5, 1000),
                new IntegrityProperties.Keys(SignatureAlgorithm.ED25519, "a", "b", null, null, "x", false));
        segmenter = new FfmpegVideoSegmenter(props, runner);
        video = Files.writeString(dir.resolve("v.mp4"), "dummy");
    }

    @Test
    void probeParsesDuration() {
        when(runner.runChecked(any(), any(), anyString())).thenReturn(new ProcessRunner.Result(0, "12.500000\n", ""));
        assertEquals(12.5, segmenter.probeDurationSeconds(video));
    }

    @Test
    void probeRejectsGarbage() {
        when(runner.runChecked(any(), any(), anyString())).thenReturn(new ProcessRunner.Result(0, "N/A\n", ""));
        assertThrows(MediaProcessingException.class, () -> segmenter.probeDurationSeconds(video));
    }

    @Test
    void missingFileIsRejected() {
        assertThrows(InvalidInputException.class, () -> segmenter.probeDurationSeconds(dir.resolve("nope.mp4")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractBuildsSafeCommandAndListsFrames() {
        when(runner.runChecked(any(), any(), anyString())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            Path pattern = Path.of(cmd.get(cmd.size() - 1));
            Files.writeString(pattern.getParent().resolve("frame_000002.png"), "x");
            Files.writeString(pattern.getParent().resolve("frame_000001.png"), "x");
            Files.writeString(pattern.getParent().resolve("ignored.txt"), "x");
            return new ProcessRunner.Result(0, "", "");
        });
        SegmentWindow window = new SegmentWindow(1, 5.0, 10.0);
        ExtractedSegment result = segmenter.extract(video, window, new SegmentationParams(5, 2, 320), dir.resolve("out"));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(runner).runChecked(captor.capture(), any(), anyString());
        List<String> cmd = captor.getValue();
        assertEquals("ffmpeg-bin", cmd.get(0));
        assertTrue(cmd.containsAll(List.of("-nostdin", "-ss", "5.000", "-t", "5.000", "-frames:v", "10", "-an")));
        assertTrue(cmd.contains("fps=2.0000,scale=320:-2:flags=bicubic"));
        assertEquals(2, result.framePaths().size());
        assertEquals("frame_000001.png", result.framePaths().get(0).getFileName().toString());
    }
}
