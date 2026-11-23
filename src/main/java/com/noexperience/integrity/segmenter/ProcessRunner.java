package com.noexperience.integrity.segmenter;

import com.noexperience.integrity.common.MediaProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs external programs safely: argument list only (no shell), stdin closed, stdout/stderr drained concurrently with a
 * size cap, hard timeout with forced kill, and exit-code checking.
 */
@Component
public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);
    private static final int MAX_CAPTURE_BYTES = 64 * 1024;
    private static final ExecutorService DRAINERS = Executors.newVirtualThreadPerTaskExecutor();

    /** Captured result of a finished process. */
    public record Result(int exitCode, String stdout, String stderr) {
    }

    /** Runs the command and returns its result without interpreting the exit code. */
    public Result run(List<String> command, Duration timeout) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }
        log.debug("Executing: {}", command);
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new MediaProcessingException("Cannot start '" + command.get(0)
                    + "'. Is it installed and configured (integrity.ffmpeg.*)? " + e.getMessage(), e);
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // stdin may already be closed; harmless
        }
        Future<String> out = DRAINERS.submit(() -> drain(process.getInputStream()));
        Future<String> err = DRAINERS.submit(() -> drain(process.getErrorStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new MediaProcessingException("'" + command.get(0) + "' timed out after " + timeout.toSeconds() + "s");
            }
            String stdout = out.get(10, TimeUnit.SECONDS);
            String stderr = err.get(10, TimeUnit.SECONDS);
            return new Result(process.exitValue(), stdout, stderr);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("Interrupted while waiting for '" + command.get(0) + "'", e);
        } catch (ExecutionException | TimeoutException e) {
            process.destroyForcibly();
            throw new MediaProcessingException("Failed to read output of '" + command.get(0) + "': " + e.getMessage(), e);
        }
    }

    /** Runs the command and throws if it exits with a non-zero status. */
    public Result runChecked(List<String> command, Duration timeout, String description) {
        Result result = run(command, timeout);
        if (result.exitCode() != 0) {
            String detail = result.stderr().isBlank() ? result.stdout() : result.stderr();
            throw new MediaProcessingException(description + " failed (exit code " + result.exitCode() + "): "
                    + abbreviate(detail.strip(), 800));
        }
        return result;
    }

    private static String drain(InputStream in) {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                int room = MAX_CAPTURE_BYTES - buffer.size();
                if (room > 0) {
                    buffer.write(chunk, 0, Math.min(read, room));
                }
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
