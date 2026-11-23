package com.noexperience.integrity.storage;

import com.noexperience.integrity.verifier.VerificationReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaReportStore implements ReportStore {

    private final ReportRepository repository;
    private final JsonCodec codec;

    public JpaReportStore(ReportRepository repository, JsonCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    @Transactional
    public VerificationReport save(VerificationReport report) {
        repository.save(new VerificationReportEntity(report.reportId(), truncate(report.manifestId(), 255),
                report.verdict().name(), truncate(report.testVideoName(), 512), Instant.parse(report.createdAt()),
                codec.toJson(report)));
        return report;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationReport> find(String reportId) {
        return repository.findById(reportId).map(e -> codec.parseReport(e.getReportJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportSummary> listForManifest(String manifestId) {
        return repository.findByManifestIdOrderByCreatedAtDesc(manifestId).stream()
                .map(e -> new ReportSummary(e.getId(), e.getManifestId(), e.getVerdict(), e.getTestVideoName(),
                        e.getCreatedAt().toString()))
                .toList();
    }
}
