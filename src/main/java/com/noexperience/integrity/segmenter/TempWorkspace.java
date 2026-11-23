package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.MediaProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A temporary directory tree that is always removed on {@link #close()}. */
public final class TempWorkspace implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TempWorkspace.class);

    private final Path root;

    private TempWorkspace(Path root) {
        this.root = root;
    }

    /** @param baseDir optional parent directory; the system temp directory is used when null or blank */
    public static TempWorkspace create(String baseDir) {
        try {
            Path root;
            if (baseDir == null || baseDir.isBlank()) {
                root = Files.createTempDirectory("viv-");
            } else {
                Path base = Path.of(baseDir);
                Files.createDirectories(base);
                root = Files.createTempDirectory(base, "viv-");
            }
            return new TempWorkspace(root);
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot create temporary workspace: " + e.getMessage(), e);
        }
    }

    public Path root() {
        return root;
    }

    public Path newSubDir(String name) {
        try {
            return Files.createDirectories(root.resolve(name));
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot create temporary directory " + name + ": " + e.getMessage(), e);
        }
    }

    public static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete temporary file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean temporary directory {}: {}", path, e.getMessage());
        }
    }

    @Override
    public void close() {
        deleteRecursively(root);
    }
}
