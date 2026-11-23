package com.noexperience.integrity.cli;

import com.noexperience.integrity.verifier.Anomaly;
import com.noexperience.integrity.verifier.VerificationReport;
import java.util.List;
import java.util.Locale;

/** Human-readable rendering of a {@link VerificationReport} for the terminal. */
public final class ReportFormatter {

    private ReportFormatter() {
    }

    public static String format(VerificationReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("VERDICT: ").append(r.verdict()).append('\n');
        sb.append("  summary        : ").append(r.summary()).append('\n');
        sb.append("  manifest       : ").append(r.manifestId()).append(" (").append(r.videoName()).append(")\n");
        sb.append("  analyzed video : ").append(r.testVideoName()).append('\n');
        sb.append("  signature      : ").append(r.signatureValid() ? "VALID" : "INVALID")
                .append(" - ").append(r.signatureDetail()).append('\n');
        sb.append("  file hash match: ").append(r.fileHashMatches()).append('\n');
        sb.append(String.format(Locale.ROOT, "  duration       : reference %.3fs, test %s%n", r.referenceDurationSec(),
                r.testDurationSec() == null ? "n/a" : String.format(Locale.ROOT, "%.3fs", r.testDurationSec())));
        sb.append("  segments       : reference ").append(r.referenceSegmentCount())
                .append(", test ").append(r.testSegmentCount()).append('\n');
        sb.append("  hamming limit  : ").append(r.hammingThreshold()).append(" bits per frame\n");
        List<Anomaly> anomalies = r.anomalies();
        if (anomalies.isEmpty()) {
            sb.append("  anomalies      : none\n");
        } else {
            sb.append("  anomalies (").append(anomalies.size()).append("):\n");
            int n = 1;
            for (Anomaly a : anomalies) {
                sb.append("   ").append(n++).append(". [").append(a.severity()).append("] ").append(a.type());
                if (!a.referenceSegments().isEmpty()) {
                    sb.append("  ref segments ").append(a.referenceSegments());
                }
                if (!a.testSegments().isEmpty()) {
                    sb.append("  test segments ").append(a.testSegments());
                }
                sb.append('\n');
                if (a.referenceStartSec() != null) {
                    sb.append(String.format(Locale.ROOT, "        reference time %.3fs - %.3fs%n",
                            a.referenceStartSec(), a.referenceEndSec()));
                }
                if (a.testStartSec() != null) {
                    sb.append(String.format(Locale.ROOT, "        test time      %.3fs - %.3fs%n",
                            a.testStartSec(), a.testEndSec()));
                }
                sb.append("        ").append(a.description()).append('\n');
            }
        }
        return sb.toString();
    }
}
