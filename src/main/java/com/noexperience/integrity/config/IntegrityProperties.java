package com.noexperience.integrity.config;

import com.noexperience.integrity.chain.SignatureAlgorithm;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.segmenter.SegmentationParams;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Strongly typed, validated application settings (prefix {@code integrity}). */
@ConfigurationProperties(prefix = "integrity")
public record IntegrityProperties(
        @DefaultValue Ffmpeg ffmpeg,
        @DefaultValue Segmentation segmentation,
        @DefaultValue Verifier verifier,
        @DefaultValue Keys keys) {

    /** FFmpeg / FFprobe invocation settings. */
    public record Ffmpeg(
            @DefaultValue("ffmpeg") String binary,
            @DefaultValue("ffprobe") String probeBinary,
            @DefaultValue("300") int timeoutSeconds,
            String tempDir) {

        public Ffmpeg {
            if (binary == null || binary.isBlank()) {
                throw new InvalidInputException("integrity.ffmpeg.binary must not be blank");
            }
            if (probeBinary == null || probeBinary.isBlank()) {
                throw new InvalidInputException("integrity.ffmpeg.probe-binary must not be blank");
            }
            if (timeoutSeconds < 1) {
                throw new InvalidInputException("integrity.ffmpeg.timeout-seconds must be >= 1");
            }
        }

        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }
    }

    /** Default segmentation parameters used at registration time. */
    public record Segmentation(
            @DefaultValue("5") double segmentSeconds,
            @DefaultValue("2") double sampleFps,
            @DefaultValue("320") int analysisWidth) {

        public Segmentation {
            toParams(segmentSeconds, sampleFps, analysisWidth); // validates
        }

        public SegmentationParams toParams() {
            return toParams(segmentSeconds, sampleFps, analysisWidth);
        }

        private static SegmentationParams toParams(double seg, double fps, int width) {
            return new SegmentationParams(seg, fps, width);
        }
    }

    /** Verification tuning. */
    public record Verifier(
            @DefaultValue("10") int hammingThreshold,
            @DefaultValue("0.5") double durationToleranceSeconds,
            @DefaultValue("4000000") long maxAlignmentCells) {

        public Verifier {
            if (hammingThreshold < 0 || hammingThreshold > 64) {
                throw new InvalidInputException("integrity.verifier.hamming-threshold must be within 0..64");
            }
            if (!(durationToleranceSeconds >= 0)) {
                throw new InvalidInputException("integrity.verifier.duration-tolerance-seconds must be >= 0");
            }
            if (maxAlignmentCells < 1) {
                throw new InvalidInputException("integrity.verifier.max-alignment-cells must be >= 1");
            }
        }
    }

    /** Signing key configuration: PEM files or a PKCS#12 keystore. */
    public record Keys(
            @DefaultValue("ed25519") SignatureAlgorithm algorithm,
            @DefaultValue("./keys/integrity-private.pem") String privateKeyPath,
            @DefaultValue("./keys/integrity-public.pem") String publicKeyPath,
            String keystorePath,
            String keystorePassword,
            @DefaultValue("integrity") String keyAlias,
            @DefaultValue("true") boolean autoGenerate) {

        public Keys {
            if (algorithm == null) {
                throw new InvalidInputException("integrity.keys.algorithm must be set");
            }
        }
    }
}
