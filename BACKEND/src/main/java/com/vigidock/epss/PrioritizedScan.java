package com.vigidock.epss;

import com.vigidock.model.ScanRecord;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Scan result enriched with exploit probability and ordered remediation priorities.
 */
public record PrioritizedScan(ScanRecord scanRecord, List<PrioritizedVulnerability> vulnerabilities, Instant enrichedAt) {

    public PrioritizedScan {
        Objects.requireNonNull(scanRecord, "scanRecord must not be null");
        vulnerabilities = vulnerabilities == null ? List.of() : List.copyOf(vulnerabilities);
        enrichedAt = enrichedAt == null ? Instant.now() : enrichedAt;
    }
}
