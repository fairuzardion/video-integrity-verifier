package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.config.IntegrityProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * {@link VideoSegmenter} backed by the FFmpeg / FFprobe command-line tools.
 *
 * <p>Frames are decoded (frame-accurate seek because {@code -ss} is applied while transcoding), resampled with the
 * {@code fps} filter and scaled to a fixed width, then written as lossless PNG files.
 */
@Component
public class FfmpegVideoSegmenter implements VideoSegmenter {

    private final IntegrityProperties.Ffmpeg config;
    private final ProcessRunner runner;

    public FfmpegVideoSegmenter(IntegrityProperties properties, ProcessRunner runner) {
        this.config = properties.ffmpeg();
        this.runner = runner;
    }

    @Override
    public double probeDurationSeconds(Path video) {
        requireReadable(video);
        List<String> command = List.of(
                config.probeBinary(), "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                video.toAbsolutePath().toString());
        ProcessRunner.Result result = runner.runChecked(command, config.timeout(), "ffprobe duration probe");
        String firstLine = result.stdout().lines().findFirst().orElse("").strip();
        try {
            double duration = Double.parseDouble(firstLine);
            if (!(duration > 0) || Double.isInfinite(duration)) {
                throw new NumberFormatException(firstLine);
            }
            return duration;
        } catch (NumberFormatException e) {
            throw new MediaProcessingException("Cannot determine video duration (ffprobe reported '" + firstLine
                    + "'). Is this a valid video file?", e);
        }
    }

    @Override
    public ExtractedSegment extract(Path video, SegmentWindow window, SegmentationParams params, Path outputDir) {
        requireReadable(video);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot create output directory " + outputDir + ": " + e.getMessage(), e);
        }
        String pattern = outputDir.resolve("frame_%06d.png").toAbsolutePath().toString();
        List<String> command = new ArrayList<>(List.of(
                config.binary(), "-hide_banner", "-nostdin", "-v", "error", "-y",
                "-ss", number(window.startSec(), 3),
                "-t", number(window.durationSec(), 3),
                "-i", video.toAbsolutePath().toString(),
                "-an", "-sn", "-dn",
                "-vf", "fps=" + number(params.sampleFps(), 4) + ",scale=" + params.analysisWidth() + ":-2:flags=bicubic",
                "-frames:v", String.valueOf(params.expectedFrames(window)),
                "-pix_fmt", "rgb24",
                "-f", "image2",
                pattern));
        runner.runChecked(command, config.timeout(), "ffmpeg frame extraction for segment " + window.index());
        return new ExtractedSegment(window, listFrames(outputDir));
    }

    private static List<Path> listFrames(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("frame_") && name.endsWith(".png");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot list extracted frames in " + dir + ": " + e.getMessage(), e);
        }
    }

    private static void requireReadable(Path video) {
        if (video == null || !Files.isRegularFile(video) || !Files.isReadable(video)) {
            throw new InvalidInputException("Video file does not exist or is not readable: " + video);
        }
    }

    private static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
