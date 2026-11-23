package com.noexperience.integrity.verifier;

import com.noexperience.integrity.chain.HashChain;
import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.ManifestSignatureVerifier;
import com.noexperience.integrity.chain.SegmentEntry;
import com.noexperience.integrity.chain.SignatureCheck;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.fingerprint.AnalyzedSegment;
import com.noexperience.integrity.fingerprint.Sha256;
import com.noexperience.integrity.fingerprint.VideoAnalyzer;
import com.noexperience.integrity.segmenter.SegmentPlanner;
import com.noexperience.integrity.segmenter.SegmentWindow;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.verifier.SegmentAligner.AlignedPair;
import com.noexperience.integrity.verifier.SegmentAligner.PairKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Compares a test video against a signed reference manifest.
 *
 * <ol>
 *   <li>verify the manifest signature with the trusted key, then the manifest's own hash chain;</li>
 *   <li>if the whole-file SHA-256 matches, the video is byte-identical: AUTHENTIC;</li>
 *   <li>otherwise re-segment the video with the manifest's parameters, fingerprint every segment, align the segment
 *       sequences and translate the alignment into anomalies with exact segment indices and time ranges.</li>
 * </ol>
 */
@Component
public class ManifestVerifier {

    private static final Logger log = LoggerFactory.getLogger(ManifestVerifier.class);

    private final ManifestSignatureVerifier signatureVerifier;
    private final VideoAnalyzer analyzer;
    private final IntegrityProperties.Verifier settings;

    @Autowired
    public ManifestVerifier(ManifestSignatureVerifier signatureVerifier, VideoAnalyzer analyzer,
                            IntegrityProperties properties) {
        this(signatureVerifier, analyzer, properties.verifier());
    }

    public ManifestVerifier(ManifestSignatureVerifier signatureVerifier, VideoAnalyzer analyzer,
                            IntegrityProperties.Verifier settings) {
        this.signatureVerifier = signatureVerifier;
        this.analyzer = analyzer;
        this.settings = settings;
    }

    private record Header(String reportId, String createdAt, Manifest manifest, String testVideoName) {
    }

    public VerificationReport verify(SignedManifest signed, Path video, String testVideoName) {
        Manifest manifest = signed.manifest();
        String name = testVideoName == null || testVideoName.isBlank()
                ? String.valueOf(video.getFileName()) : testVideoName;
        Header header = new Header(UUID.randomUUID().toString(), Instant.now().toString(), manifest, name);

        // 1. Signature first: an untrusted manifest must never be used to judge a video.
        SignatureCheck signature = signatureVerifier.verify(signed);
        if (!signature.valid()) {
            log.warn("Manifest {} rejected: {}", manifest.manifestId(), signature.detail());
            return report(header, Verdict.INVALID_SIGNATURE, signature, false, null, 0, List.of(),
                    "Reference manifest is not trustworthy: " + signature.detail()
                            + ". The video was not analyzed.");
        }

        // 2. The signed hash chain must be internally consistent.
        List<Anomaly> manifestProblems = checkManifestChain(manifest);
        if (!manifestProblems.isEmpty()) {
            return report(header, Verdict.INVALID_SIGNATURE, signature, false, null, 0, manifestProblems,
                    "The signature is valid but the reference hash chain is internally inconsistent; "
                            + "the manifest cannot be used as a reference.");
        }

        // 3. Byte-identical file: nothing more to prove.
        String fileSha = Sha256.hashFile(video);
        if (fileSha.equals(manifest.videoSha256())) {
            return report(header, Verdict.AUTHENTIC, signature, true, manifest.durationSec(),
                    manifest.segments().size(), List.of(),
                    "Video is byte-identical to the registered original (SHA-256 match, valid signature).");
        }

        // 4. Content-level comparison.
        SegmentationParams params = new SegmentationParams(manifest.segmentDurationSec(), manifest.sampleFps(),
                manifest.analysisWidth());
        double testDuration = analyzer.probeDuration(video);
        double delta = testDuration - manifest.durationSec();
        boolean durationMatches = Math.abs(delta) <= settings.durationToleranceSeconds();

        List<SegmentWindow> referenceWindows = manifest.segments().stream()
                .map(e -> new SegmentWindow(e.index(), e.startSec(), e.endSec())).toList();
        List<SegmentWindow> testWindows = durationMatches
                ? referenceWindows
                : SegmentPlanner.plan(testDuration, params.segmentSeconds());

        List<AnalyzedSegment> analyzed = analyzer.fingerprintWindows(video, params, testWindows);
        List<SegmentEntry> referenceEntries = manifest.segments();
        List<SegmentEntry> testEntries = HashChain.build(analyzed);

        List<AlignedPair> pairs;
        try {
            pairs = new SegmentAligner(settings.hammingThreshold(), settings.maxAlignmentCells())
                    .align(referenceEntries, testEntries);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Manifest contains a malformed perceptual hash: " + e.getMessage(), e);
        }

        List<Anomaly> anomalies = new ArrayList<>();
        if (!durationMatches) {
            anomalies.add(durationAnomaly(manifest.durationSec(), testDuration, delta));
        }
        anomalies.addAll(contentAnomalies(pairs, referenceEntries, testEntries));
        anomalies.addAll(shiftAnomalies(pairs, referenceEntries, testEntries));
        chainBreak(referenceEntries, testEntries).ifPresent(anomalies::add);
        anomalies.sort(Comparator.comparingDouble(ManifestVerifier::position));

        Verdict verdict = verdictOf(anomalies);
        return report(header, verdict, signature, false, testDuration, testEntries.size(), anomalies,
                summarize(verdict, anomalies));
    }

    // ------------------------------------------------------------------------------------------------ manifest

    private List<Anomaly> checkManifestChain(Manifest manifest) {
        List<SegmentEntry> entries = manifest.segments();
        if (entries.isEmpty()) {
            return List.of(new Anomaly(AnomalyType.MANIFEST_CHAIN_INVALID, Severity.CRITICAL, List.of(), List.of(),
                    null, null, null, null, null, null, "Reference manifest contains no segments."));
        }
        List<Anomaly> problems = new ArrayList<>();
        List<Integer> broken = HashChain.findBrokenLinks(entries);
        if (!broken.isEmpty()) {
            SegmentEntry first = entries.get(broken.get(0));
            SegmentEntry last = entries.get(broken.get(broken.size() - 1));
            problems.add(new Anomaly(AnomalyType.MANIFEST_CHAIN_INVALID, Severity.CRITICAL, broken, List.of(),
                    first.startSec(), last.endSec(), null, null, null, null,
                    "Reference hash chain is broken at segment(s) " + indexLabel(broken)
                            + " (index, prevHash or chainHash inconsistent)."));
        }
        if (!HashChain.rootOf(entries).equals(manifest.chainRootHash())) {
            problems.add(new Anomaly(AnomalyType.MANIFEST_CHAIN_INVALID, Severity.CRITICAL, List.of(), List.of(),
                    null, null, null, null, null, null,
                    "Manifest chainRootHash does not equal the last chain link."));
        }
        return problems;
    }

    // ------------------------------------------------------------------------------------------------ anomalies

    private Anomaly durationAnomaly(double referenceDuration, double testDuration, double delta) {
        String direction = delta < 0 ? "shorter" : "longer";
        return new Anomaly(AnomalyType.DURATION_MISMATCH, Severity.CRITICAL, List.of(), List.of(),
                0.0, referenceDuration, 0.0, testDuration, null, delta,
                String.format(Locale.ROOT, "Test video is %.3fs %s than the reference (%.3fs vs %.3fs, tolerance %.3fs).",
                        Math.abs(delta), direction, testDuration, referenceDuration,
                        settings.durationToleranceSeconds()));
    }

    private List<Anomaly> contentAnomalies(List<AlignedPair> pairs, List<SegmentEntry> ref, List<SegmentEntry> test) {
        List<Anomaly> out = new ArrayList<>();
        int i = 0;
        while (i < pairs.size()) {
            PairKind kind = pairs.get(i).kind();
            if (kind == PairKind.EXACT) {
                i++;
                continue;
            }
            int j = i;
            while (j < pairs.size() && pairs.get(j).kind() == kind) {
                j++;
            }
            out.add(contentAnomaly(kind, pairs.subList(i, j), ref, test, i == 0, j == pairs.size()));
            i = j;
        }
        return out;
    }

    private Anomaly contentAnomaly(PairKind kind, List<AlignedPair> run, List<SegmentEntry> ref,
                                   List<SegmentEntry> test, boolean atHead, boolean atTail) {
        List<Integer> refIdx = run.stream().map(AlignedPair::referenceIndex).filter(x -> x >= 0).toList();
        List<Integer> testIdx = run.stream().map(AlignedPair::testIndex).filter(x -> x >= 0).toList();
        Double refStart = null;
        Double refEnd = null;
        Double testStart = null;
        Double testEnd = null;
        if (!refIdx.isEmpty()) {
            refStart = ref.get(refIdx.get(0)).startSec();
            refEnd = ref.get(refIdx.get(refIdx.size() - 1)).endSec();
        }
        if (!testIdx.isEmpty()) {
            testStart = test.get(testIdx.get(0)).startSec();
            testEnd = test.get(testIdx.get(testIdx.size() - 1)).endSec();
        }
        int maxDistance = run.stream().mapToInt(AlignedPair::distance).max().orElse(0);

        AnomalyType type;
        Severity severity;
        Integer distance = null;
        String description;
        switch (kind) {
            case SIMILAR -> {
                type = AnomalyType.SEGMENT_REENCODED;
                severity = Severity.WARNING;
                distance = maxDistance;
                description = String.format(Locale.ROOT,
                        "Segment(s) %s (%s) differ at pixel level but stay perceptually similar (max Hamming distance %d "
                                + "<= threshold %d): consistent with re-encoding or mild processing.",
                        indexLabel(refIdx), range(refStart, refEnd), maxDistance, settings.hammingThreshold());
            }
            case ALTERED -> {
                type = AnomalyType.SEGMENT_ALTERED;
                severity = Severity.CRITICAL;
                distance = maxDistance;
                description = String.format(Locale.ROOT,
                        "Visual content of segment(s) %s (%s) was altered (max Hamming distance %d > threshold %d).",
                        indexLabel(refIdx), range(refStart, refEnd), maxDistance, settings.hammingThreshold());
            }
            case MISSING -> {
                type = AnomalyType.SEGMENT_MISSING;
                severity = Severity.CRITICAL;
                String where = atHead ? "the beginning of the video was trimmed"
                        : atTail ? "the end of the video was trimmed" : "a section was cut out of the middle";
                description = String.format(Locale.ROOT,
                        "Reference segment(s) %s (%s) are missing from the test video: %s.",
                        indexLabel(refIdx), range(refStart, refEnd), where);
            }
            case EXTRA -> {
                type = AnomalyType.SEGMENT_INSERTED;
                severity = Severity.CRITICAL;
                String where = atHead ? "content was prepended"
                        : atTail ? "content was appended" : "content was spliced into the middle";
                description = String.format(Locale.ROOT,
                        "Test segment(s) %s (%s) have no counterpart in the reference: %s.",
                        indexLabel(testIdx), range(testStart, testEnd), where);
            }
            default -> throw new IllegalStateException("Unexpected kind " + kind);
        }
        return new Anomaly(type, severity, refIdx, testIdx, refStart, refEnd, testStart, testEnd, distance, null,
                description);
    }

    private List<Anomaly> shiftAnomalies(List<AlignedPair> pairs, List<SegmentEntry> ref, List<SegmentEntry> test) {
        List<Anomaly> out = new ArrayList<>();
        int i = 0;
        while (i < pairs.size()) {
            AlignedPair first = pairs.get(i);
            if (!isMatched(first) || first.testIndex() == first.referenceIndex()) {
                i++;
                continue;
            }
            int shift = first.testIndex() - first.referenceIndex();
            int j = i + 1;
            while (j < pairs.size() && isMatched(pairs.get(j))
                    && pairs.get(j).testIndex() - pairs.get(j).referenceIndex() == shift) {
                j++;
            }
            List<AlignedPair> run = pairs.subList(i, j);
            List<Integer> refIdx = run.stream().map(AlignedPair::referenceIndex).toList();
            List<Integer> testIdx = run.stream().map(AlignedPair::testIndex).toList();
            double refStart = ref.get(refIdx.get(0)).startSec();
            double refEnd = ref.get(refIdx.get(refIdx.size() - 1)).endSec();
            double testStart = test.get(testIdx.get(0)).startSec();
            double testEnd = test.get(testIdx.get(testIdx.size() - 1)).endSec();
            double shiftSec = testStart - refStart;
            out.add(new Anomaly(AnomalyType.TIMESTAMP_SHIFT, Severity.CRITICAL, refIdx, testIdx,
                    refStart, refEnd, testStart, testEnd, null, shiftSec,
                    String.format(Locale.ROOT,
                            "Reference segment(s) %s (%s) appear at test segment(s) %s (%s): timeline shifted by %+.3fs.",
                            indexLabel(refIdx), range(refStart, refEnd), indexLabel(testIdx),
                            range(testStart, testEnd), shiftSec)));
            i = j;
        }
        return out;
    }

    private java.util.Optional<Anomaly> chainBreak(List<SegmentEntry> ref, List<SegmentEntry> test) {
        int common = Math.min(ref.size(), test.size());
        int first = -1;
        for (int k = 0; k < common; k++) {
            if (!ref.get(k).chainHash().equals(test.get(k).chainHash())) {
                first = k;
                break;
            }
        }
        if (first < 0 && ref.size() != test.size()) {
            first = common;
        }
        if (first < 0) {
            return java.util.Optional.empty();
        }
        List<Integer> refIdx = first < ref.size() ? List.of(first) : List.of();
        List<Integer> testIdx = first < test.size() ? List.of(first) : List.of();
        Double refStart = first < ref.size() ? ref.get(first).startSec() : null;
        Double refEnd = first < ref.size() ? ref.get(first).endSec() : null;
        Double testStart = first < test.size() ? test.get(first).startSec() : null;
        Double testEnd = first < test.size() ? test.get(first).endSec() : null;
        return java.util.Optional.of(new Anomaly(AnomalyType.CHAIN_BREAK, Severity.INFO, refIdx, testIdx,
                refStart, refEnd, testStart, testEnd, null, null,
                "The strict hash chain first diverges from the reference at segment " + first
                        + "; every later chain hash differs as well because chain hashes cascade. "
                        + "See the content anomalies for what actually changed."));
    }

    private static boolean isMatched(AlignedPair p) {
        return p.kind() == PairKind.EXACT || p.kind() == PairKind.SIMILAR;
    }

    // ------------------------------------------------------------------------------------------------ verdict & report

    private static Verdict verdictOf(List<Anomaly> anomalies) {
        boolean warning = false;
        for (Anomaly a : anomalies) {
            if (a.severity() == Severity.CRITICAL) {
                return Verdict.TAMPERED;
            }
            if (a.severity() == Severity.WARNING) {
                warning = true;
            }
        }
        return warning ? Verdict.MODIFIED_OR_REENCODED : Verdict.AUTHENTIC;
    }

    private static String summarize(Verdict verdict, List<Anomaly> anomalies) {
        switch (verdict) {
            case AUTHENTIC:
                return "All segments match the signed reference; only the container bytes differ (e.g. a remux).";
            case MODIFIED_OR_REENCODED: {
                List<Integer> affected = anomalies.stream()
                        .filter(a -> a.type() == AnomalyType.SEGMENT_REENCODED)
                        .flatMap(a -> a.referenceSegments().stream()).sorted().toList();
                return "Content is perceptually consistent with the reference but differs at pixel level in segment(s) "
                        + indexLabel(affected) + ": typical of re-encoding or mild processing. "
                        + "No cut, splice, shift or visual alteration was detected.";
            }
            case TAMPERED: {
                StringBuilder sb = new StringBuilder("Video does not match the signed reference. Critical findings: ");
                boolean firstItem = true;
                for (Anomaly a : anomalies) {
                    if (a.severity() != Severity.CRITICAL) {
                        continue;
                    }
                    if (!firstItem) {
                        sb.append("; ");
                    }
                    firstItem = false;
                    boolean useRef = a.referenceStartSec() != null && a.type() != AnomalyType.SEGMENT_INSERTED;
                    sb.append(a.type()).append(" at ")
                            .append(useRef ? range(a.referenceStartSec(), a.referenceEndSec())
                                    : range(a.testStartSec(), a.testEndSec()))
                            .append(useRef ? " (reference timeline)" : " (test timeline)");
                }
                return sb.toString();
            }
            default:
                return "Verification finished.";
        }
    }

    private VerificationReport report(Header h, Verdict verdict, SignatureCheck signature, boolean fileMatches,
                                      Double testDuration, int testSegments, List<Anomaly> anomalies, String summary) {
        Manifest m = h.manifest();
        return new VerificationReport(h.reportId(), m.manifestId(), m.videoName(), h.testVideoName(), verdict,
                signature.valid(), signature.detail(), fileMatches, h.createdAt(), m.durationSec(), testDuration,
                m.segments().size(), testSegments, settings.hammingThreshold(), anomalies, summary);
    }

    // ------------------------------------------------------------------------------------------------ formatting

    private static double position(Anomaly a) {
        if (a.referenceStartSec() != null) {
            return a.referenceStartSec();
        }
        if (a.testStartSec() != null) {
            return a.testStartSec();
        }
        return -1;
    }

    private static String range(Double start, Double end) {
        if (start == null || end == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.3fs-%.3fs", start, end);
    }

    /** Compresses sorted indices: [0,2,3,4] becomes "0, 2-4". */
    static String indexLabel(List<Integer> indices) {
        if (indices.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int runStart = indices.get(0);
        int prev = runStart;
        for (int k = 1; k < indices.size(); k++) {
            int cur = indices.get(k);
            if (cur != prev + 1) {
                appendRun(sb, runStart, prev);
                runStart = cur;
            }
            prev = cur;
        }
        appendRun(sb, runStart, prev);
        return sb.toString();
    }

    private static void appendRun(StringBuilder sb, int start, int end) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(start == end ? Integer.toString(start) : start + "-" + end);
    }
}
