package com.noexperience.integrity.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noexperience.integrity.chain.HashChain;
import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.ManifestSignatureVerifier;
import com.noexperience.integrity.chain.ManifestSigner;
import com.noexperience.integrity.chain.SegmentEntry;
import com.noexperience.integrity.chain.SignatureAlgorithm;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.fingerprint.SegmentFingerprinter;
import com.noexperience.integrity.fingerprint.VideoAnalyzer;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.service.RegistrationService;
import com.noexperience.integrity.support.FakeSegmenter;
import com.noexperience.integrity.support.InMemoryKeyProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of sign + verify with a synthetic segmenter (no FFmpeg needed). The reference video has six
 * one-second segments (scenes 1..6, two sampled frames each).
 */
class ManifestVerifierTest {

    @TempDir
    Path dir;

    final SegmentationParams params = new SegmentationParams(1.0, 2.0, 320);
    final IntegrityProperties.Verifier settings = new IntegrityProperties.Verifier(10, 0.5, 1_000_000);

    FakeSegmenter fake;
    InMemoryKeyProvider keys;
    ManifestVerifier verifier;
    SignedManifest signed;
    Path original;

    @BeforeEach
    void setUp() throws IOException {
        fake = new FakeSegmenter(1.0);
        keys = new InMemoryKeyProvider(SignatureAlgorithm.ED25519);
        VideoAnalyzer analyzer = new VideoAnalyzer(fake, new SegmentFingerprinter(), dir.resolve("work").toString());
        verifier = new ManifestVerifier(new ManifestSignatureVerifier(keys), analyzer, settings);
        RegistrationService registration = new RegistrationService(analyzer, new ManifestSigner(keys));

        original = video("original.mp4", "bytes-of-original", List.of(1, 2, 3, 4, 5, 6), 0);
        signed = registration.register(original, "original.mp4", params);
    }

    Path video(String name, String content, List<Integer> scenes, int noise) throws IOException {
        fake.register(name, scenes, noise);
        return Files.writeString(dir.resolve(name), content);
    }

    VerificationReport verify(Path file) {
        return verifier.verify(signed, file, file.getFileName().toString());
    }

    Optional<Anomaly> find(VerificationReport r, AnomalyType type) {
        return r.anomalies().stream().filter(a -> a.type() == type).findFirst();
    }

    @Test
    void registrationProducesConsistentSignedManifest() {
        Manifest m = signed.manifest();
        assertEquals(6, m.segments().size());
        assertEquals(6.0, m.durationSec());
        assertTrue(HashChain.findBrokenLinks(m.segments()).isEmpty());
        assertEquals(HashChain.rootOf(m.segments()), m.chainRootHash());
    }

    @Test
    void originalIsAuthentic() {
        VerificationReport r = verify(original);
        assertEquals(Verdict.AUTHENTIC, r.verdict());
        assertTrue(r.fileHashMatches());
        assertTrue(r.signatureValid());
        assertTrue(r.anomalies().isEmpty());
    }

    @Test
    void remuxWithIdenticalContentIsAuthenticWithoutAnomalies() throws IOException {
        Path remux = video("remux.mkv", "different-container-bytes", List.of(1, 2, 3, 4, 5, 6), 0);
        VerificationReport r = verify(remux);
        assertEquals(Verdict.AUTHENTIC, r.verdict());
        assertFalse(r.fileHashMatches());
        assertTrue(r.anomalies().isEmpty());
    }

    @Test
    void reencodedVideoIsModifiedNotTampered() throws IOException {
        Path reencoded = video("reencoded.mp4", "lossy", List.of(1, 2, 3, 4, 5, 6), 2);
        VerificationReport r = verify(reencoded);
        assertEquals(Verdict.MODIFIED_OR_REENCODED, r.verdict());
        Anomaly a = find(r, AnomalyType.SEGMENT_REENCODED).orElseThrow();
        assertEquals(List.of(0, 1, 2, 3, 4, 5), a.referenceSegments());
        assertTrue(a.maxHammingDistance() <= 10);
        assertTrue(r.anomalies().stream().noneMatch(x -> x.severity() == Severity.CRITICAL));
    }

    @Test
    void middleCutIsLocatedExactly() throws IOException {
        Path cut = video("cut.mp4", "cut", List.of(1, 2, 5, 6), 0);
        VerificationReport r = verify(cut);
        assertEquals(Verdict.TAMPERED, r.verdict());
        Anomaly missing = find(r, AnomalyType.SEGMENT_MISSING).orElseThrow();
        assertEquals(List.of(2, 3), missing.referenceSegments());
        assertEquals(2.0, missing.referenceStartSec());
        assertEquals(4.0, missing.referenceEndSec());
        Anomaly shift = find(r, AnomalyType.TIMESTAMP_SHIFT).orElseThrow();
        assertEquals(List.of(4, 5), shift.referenceSegments());
        assertEquals(-2.0, shift.shiftSec());
        assertTrue(find(r, AnomalyType.DURATION_MISMATCH).isPresent());
        assertTrue(find(r, AnomalyType.CHAIN_BREAK).isPresent());
    }

    @Test
    void headTrimAndTailTrimAreDetected() throws IOException {
        VerificationReport head = verify(video("head.mp4", "head", List.of(3, 4, 5, 6), 0));
        assertEquals(Verdict.TAMPERED, head.verdict());
        Anomaly headMissing = find(head, AnomalyType.SEGMENT_MISSING).orElseThrow();
        assertEquals(List.of(0, 1), headMissing.referenceSegments());
        assertTrue(headMissing.description().contains("beginning"));

        VerificationReport tail = verify(video("tail.mp4", "tail", List.of(1, 2, 3, 4), 0));
        assertEquals(Verdict.TAMPERED, tail.verdict());
        Anomaly tailMissing = find(tail, AnomalyType.SEGMENT_MISSING).orElseThrow();
        assertEquals(List.of(4, 5), tailMissing.referenceSegments());
        assertEquals(4.0, tailMissing.referenceStartSec());
        assertTrue(tailMissing.description().contains("end"));
        assertTrue(find(tail, AnomalyType.TIMESTAMP_SHIFT).isEmpty());
    }

    @Test
    void splicedSegmentIsLocatedExactly() throws IOException {
        Path spliced = video("spliced.mp4", "spliced", List.of(1, 2, 3, 99, 4, 5, 6), 0);
        VerificationReport r = verify(spliced);
        assertEquals(Verdict.TAMPERED, r.verdict());
        Anomaly inserted = find(r, AnomalyType.SEGMENT_INSERTED).orElseThrow();
        assertEquals(List.of(3), inserted.testSegments());
        assertEquals(3.0, inserted.testStartSec());
        assertEquals(4.0, inserted.testEndSec());
        Anomaly shift = find(r, AnomalyType.TIMESTAMP_SHIFT).orElseThrow();
        assertEquals(List.of(3, 4, 5), shift.referenceSegments());
        assertEquals(1.0, shift.shiftSec());
    }

    @Test
    void visuallyAlteredSegmentIsFlaggedInPlace() throws IOException {
        VerificationReport r = verify(video("altered.mp4", "altered", List.of(1, 2, 33, 4, 5, 6), 0));
        assertEquals(Verdict.TAMPERED, r.verdict());
        Anomaly altered = find(r, AnomalyType.SEGMENT_ALTERED).orElseThrow();
        assertEquals(List.of(2), altered.referenceSegments());
        assertEquals(2.0, altered.referenceStartSec());
        assertTrue(altered.maxHammingDistance() > 10);
        assertTrue(find(r, AnomalyType.SEGMENT_MISSING).isEmpty());
        assertTrue(find(r, AnomalyType.SEGMENT_INSERTED).isEmpty());
    }

    @Test
    void forgedManifestContentIsRejectedBeforeAnalysis() throws IOException {
        Manifest m = signed.manifest();
        Manifest forged = new Manifest(m.manifestId(), m.formatVersion(), m.videoName(), "0".repeat(64),
                m.durationSec(), m.segmentDurationSec(), m.sampleFps(), m.analysisWidth(), m.createdAt(),
                m.signatureAlgorithm(), m.keyId(), m.segments(), m.chainRootHash());
        VerificationReport r = verifier.verify(new SignedManifest(forged, signed.signature()), original, "x.mp4");
        assertEquals(Verdict.INVALID_SIGNATURE, r.verdict());
        assertFalse(r.signatureValid());
        assertEquals(0, r.testSegmentCount());
    }

    @Test
    void manifestSignedByUntrustedKeyIsRejected() {
        InMemoryKeyProvider attacker = new InMemoryKeyProvider(SignatureAlgorithm.ED25519);
        SignedManifest forged = new ManifestSigner(attacker).sign(signed.manifest());
        VerificationReport r = verifier.verify(forged, original, "original.mp4");
        assertEquals(Verdict.INVALID_SIGNATURE, r.verdict());
    }

    @Test
    void validlySignedButInconsistentChainIsRejected() {
        List<SegmentEntry> entries = new ArrayList<>(signed.manifest().segments());
        SegmentEntry e = entries.get(3);
        entries.set(3, new SegmentEntry(e.index(), e.startSec(), e.endSec(), e.prevHash(), "forged-sha",
                e.perceptualHash(), e.chainHash()));
        Manifest m = signed.manifest();
        Manifest broken = new Manifest(m.manifestId(), m.formatVersion(), m.videoName(), m.videoSha256(),
                m.durationSec(), m.segmentDurationSec(), m.sampleFps(), m.analysisWidth(), m.createdAt(),
                m.signatureAlgorithm(), m.keyId(), entries, m.chainRootHash());
        SignedManifest resigned = new ManifestSigner(keys).sign(broken);

        VerificationReport r = verifier.verify(resigned, original, "original.mp4");
        assertEquals(Verdict.INVALID_SIGNATURE, r.verdict());
        assertTrue(r.signatureValid());
        Anomaly a = find(r, AnomalyType.MANIFEST_CHAIN_INVALID).orElseThrow();
        assertEquals(List.of(3), a.referenceSegments());
    }
}
