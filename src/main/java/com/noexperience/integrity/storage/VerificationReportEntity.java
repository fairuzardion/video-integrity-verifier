package com.noexperience.integrity.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Persisted verification report: queryable columns plus the full JSON document. */
@Entity
@Table(name = "verification_reports", indexes = @Index(name = "idx_reports_manifest", columnList = "manifestId"))
public class VerificationReportEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String manifestId;

    @Column(nullable = false, length = 32)
    private String verdict;

    @Column(nullable = false, length = 512)
    private String testVideoName;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "text")
    private String reportJson;

    protected VerificationReportEntity() {
        // required by JPA
    }

    public VerificationReportEntity(String id, String manifestId, String verdict, String testVideoName,
                                    Instant createdAt, String reportJson) {
        this.id = id;
        this.manifestId = manifestId;
        this.verdict = verdict;
        this.testVideoName = testVideoName;
        this.createdAt = createdAt;
        this.reportJson = reportJson;
    }

    public String getId() {
        return id;
    }

    public String getManifestId() {
        return manifestId;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getTestVideoName() {
        return testVideoName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReportJson() {
        return reportJson;
    }
}
