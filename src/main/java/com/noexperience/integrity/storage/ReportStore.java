package com.noexperience.integrity.storage;

import com.noexperience.integrity.verifier.VerificationReport;
import java.util.List;
import java.util.Optional;

/** Storage abstraction for verification reports. */
public interface ReportStore {

    VerificationReport save(VerificationReport report);

    Optional<VerificationReport> find(String reportId);

    List<ReportSummary> listForManifest(String manifestId);
}
