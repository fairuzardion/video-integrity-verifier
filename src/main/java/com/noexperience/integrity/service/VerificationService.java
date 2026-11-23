package com.noexperience.integrity.service;

import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.ResourceNotFoundException;
import com.noexperience.integrity.storage.ManifestStore;
import com.noexperience.integrity.storage.ReportStore;
import com.noexperience.integrity.storage.ReportSummary;
import com.noexperience.integrity.verifier.ManifestVerifier;
import com.noexperience.integrity.verifier.VerificationReport;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Verification with report persistence. */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final ManifestVerifier verifier;
    private final ManifestStore manifests;
    private final ReportStore reports;

    public VerificationService(ManifestVerifier verifier, ManifestStore manifests, ReportStore reports) {
        this.verifier = verifier;
        this.manifests = manifests;
        this.reports = reports;
    }

    public VerificationReport verifyAgainstStored(Path video, String testVideoName, String manifestId) {
        SignedManifest manifest = manifests.find(manifestId)
                .orElseThrow(() -> new ResourceNotFoundException("No manifest with id " + manifestId));
        return verifyAgainst(video, testVideoName, manifest);
    }

    public VerificationReport verifyAgainst(Path video, String testVideoName, SignedManifest manifest) {
        VerificationReport report = verifier.verify(manifest, video, testVideoName);
        reports.save(report);
        log.info("Report {} for manifest {}: {} ({} anomalies)", report.reportId(), report.manifestId(),
                report.verdict(), report.anomalies().size());
        return report;
    }

    public VerificationReport getReport(String reportId) {
        return reports.find(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("No report with id " + reportId));
    }

    public List<ReportSummary> listReports(String manifestId) {
        if (!manifests.exists(manifestId)) {
            throw new ResourceNotFoundException("No manifest with id " + manifestId);
        }
        return reports.listForManifest(manifestId);
    }
}
