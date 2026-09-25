package com.vigidock.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * API response for a Trivy scan enriched with EPSS-based real-world risk tags.
 */
public record ScanResponse(
        String targetName,
        Instant scannedAt,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        List<VulnerabilityDetail> vulnerabilities) {

    public ScanResponse {
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("targetName must not be blank");
        }
        targetName = targetName.trim();
        scannedAt = scannedAt == null ? Instant.now() : scannedAt;
        requireNonNegative(criticalCount, "criticalCount");
        requireNonNegative(highCount, "highCount");
        requireNonNegative(mediumCount, "mediumCount");
        requireNonNegative(lowCount, "lowCount");
        vulnerabilities = vulnerabilities == null ? List.of() : List.copyOf(vulnerabilities);
    }

    private static void requireNonNegative(int count, String fieldName) {
        if (count < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }
}
