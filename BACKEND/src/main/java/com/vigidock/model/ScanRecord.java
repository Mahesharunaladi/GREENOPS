package com.vigidock.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable security scan data used to generate a VigiDock report.
 *
 * <p>The record contains only report-safe scan metadata and AI-generated remediation guidance.
 * It must not contain source images, credentials, or raw scan command output.
 */
public record ScanRecord(
        String imageOrYamlName,
        Instant scannedAt,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        List<VulnerabilityFinding> findings) {

    public ScanRecord {
        if (imageOrYamlName == null || imageOrYamlName.isBlank()) {
            throw new IllegalArgumentException("imageOrYamlName must not be blank");
        }
        imageOrYamlName = imageOrYamlName.trim();
        scannedAt = scannedAt == null ? Instant.now() : scannedAt;
        requireNonNegative(criticalCount, "criticalCount");
        requireNonNegative(highCount, "highCount");
        requireNonNegative(mediumCount, "mediumCount");
        requireNonNegative(lowCount, "lowCount");
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * Report-ready vulnerability detail and AI explanation.
     */
    public record VulnerabilityFinding(
            String cveId,
            String packageName,
            String aiExplanation,
            String remediationFix,
            Double cvssScore) {

        public VulnerabilityFinding {
            cveId = requireText(cveId, "cveId");
            packageName = requireText(packageName, "packageName");
            aiExplanation = requireText(aiExplanation, "aiExplanation");
            remediationFix = requireText(remediationFix, "remediationFix");
            if (cvssScore != null && (!Double.isFinite(cvssScore) || cvssScore < 0.0d || cvssScore > 10.0d)) {
                throw new IllegalArgumentException("cvssScore must be between 0 and 10 when present");
            }
        }

        public VulnerabilityFinding(
                String cveId, String packageName, String aiExplanation, String remediationFix) {
            this(cveId, packageName, aiExplanation, remediationFix, null);
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.toString(value, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
