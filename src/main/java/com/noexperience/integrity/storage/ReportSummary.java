package com.noexperience.integrity.storage;

/** Lightweight listing entry for a stored verification report. */
public record ReportSummary(String reportId, String manifestId, String verdict, String testVideoName, String createdAt) {
}
