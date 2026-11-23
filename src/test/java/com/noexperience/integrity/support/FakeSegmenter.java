package com.noexperience.integrity.support;

import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.segmenter.ExtractedSegment;
import com.noexperience.integrity.segmenter.SegmentWindow;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.segmenter.VideoSegmenter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * FFmpeg-free segmenter: each "video" (identified by file name) is a list of scene seeds, one scene per segment.
 * Frame content depends only on the scene seed, so cuts and splices behave like real content moves.
 */
public final class FakeSegmenter implements VideoSegmenter {

    public record FakeVideo(List<Integer> scenes, int noise) {
    }

    private final double segmentSeconds;
    private final Map<String, FakeVideo> videos = new HashMap<>();

    public FakeSegmenter(double segmentSeconds) {
        this.segmentSeconds = segmentSeconds;
    }

    public void register(String fileName, List<Integer> scenes, int noise) {
        videos.put(fileName, new FakeVideo(scenes, noise));
    }

    private FakeVideo video(Path path) {
        FakeVideo v = videos.get(path.getFileName().toString());
        if (v == null) {
            throw new MediaProcessingException("Unknown fake video " + path);
        }
        return v;
    }

    @Override
    public double probeDurationSeconds(Path video) {
        return video(video).scenes().size() * segmentSeconds;
    }

    @Override
    public ExtractedSegment extract(Path video, SegmentWindow window, SegmentationParams params, Path outputDir) {
        FakeVideo v = video(video);
        List<Path> frames = new ArrayList<>();
        if (window.index() < v.scenes().size()) {
            int scene = v.scenes().get(window.index());
            int count = params.expectedFrames(window);
            for (int f = 0; f < count; f++) {
                Path file = outputDir.resolve(String.format("frame_%06d.png", f + 1));
                try {
                    ImageIO.write(TestImages.blocks(scene * 100L + f, v.noise()), "png", file.toFile());
                } catch (IOException e) {
                    throw new MediaProcessingException(e.getMessage(), e);
                }
                frames.add(file);
            }
        }
        return new ExtractedSegment(window, frames);
    }
}
