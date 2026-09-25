package com.vigidock.epss;

import java.time.Instant;

/**
 * EPSS exploit-likelihood data published by FIRST.
 */
public record EpssScore(
        String cveId,
        double probability,
        double percentile,
        Instant publishedAt,
        boolean available) {

    public static EpssScore unavailable(String cveId) {
        return new EpssScore(cveId, 0.0d, 0.0d, null, false);
    }
}
