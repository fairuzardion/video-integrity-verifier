package com.noexperience.integrity.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<VerificationReportEntity, String> {

    List<VerificationReportEntity> findByManifestIdOrderByCreatedAtDesc(String manifestId);
}
