package com.noexperience.integrity.verifier;

import java.util.List;

/** Structured result of comparing a test video against a signed reference manifest. */
public record VerificationReport(
        String reportId,
        String manifestId,
        String videoName,
        String testVideoName,
        Verdict verdict,
        boolean signatureValid,
        String signatureDetail,
        boolean fileHashMatches,
        String createdAt,
        double referenceDurationSec,
        Double testDurationSec,
        int referenceSegmentCount,
        int testSegmentCount,
        int hammingThreshold,
        List<Anomaly> anomalies,
        String summary) {

    public VerificationReport {
        anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
    }
}
