package com.noexperience.integrity.service;

import com.noexperience.integrity.chain.HashChain;
import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.ManifestSigner;
import com.noexperience.integrity.chain.SegmentEntry;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.fingerprint.AnalyzedSegment;
import com.noexperience.integrity.fingerprint.Sha256;
import com.noexperience.integrity.fingerprint.VideoAnalyzer;
import com.noexperience.integrity.segmenter.SegmentPlanner;
import com.noexperience.integrity.segmenter.SegmentWindow;
import com.noexperience.integrity.segmenter.SegmentationParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** "Sign" pipeline: segment, fingerprint, chain, sign. Pure computation; persistence is handled by callers. */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final VideoAnalyzer analyzer;
    private final ManifestSigner signer;

    public RegistrationService(VideoAnalyzer analyzer, ManifestSigner signer) {
        this.analyzer = analyzer;
        this.signer = signer;
    }

    public SignedManifest register(Path video, String videoName, SegmentationParams params) {
        if (video == null || !Files.isRegularFile(video)) {
            throw new InvalidInputException("Video file does not exist: " + video);
        }
        long started = System.nanoTime();
        String fileSha = Sha256.hashFile(video);
        double duration = analyzer.probeDuration(video);
        List<SegmentWindow> windows = SegmentPlanner.plan(duration, params.segmentSeconds());
        List<AnalyzedSegment> analyzed = analyzer.fingerprintWindows(video, params, windows);
        List<SegmentEntry> chain = HashChain.build(analyzed);

        Manifest manifest = new Manifest(
                UUID.randomUUID().toString(),
                Manifest.CURRENT_FORMAT_VERSION,
                videoName,
                fileSha,
                Math.round(duration * 1000.0) / 1000.0,
                params.segmentSeconds(),
                params.sampleFps(),
                params.analysisWidth(),
                Instant.now().toString(),
                signer.algorithm().label(),
                signer.keyId(),
                chain,
                HashChain.rootOf(chain));
        SignedManifest signed = signer.sign(manifest);
        log.info("Registered '{}' as manifest {}: {} segments, {}s, took {} ms", videoName, manifest.manifestId(),
                chain.size(), manifest.durationSec(), (System.nanoTime() - started) / 1_000_000);
        return signed;
    }
}
