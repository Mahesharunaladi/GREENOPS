package com.greenops.model;

import java.time.Instant;

/** Bounded, redacted request metadata captured by the synthetic deception service. */
public record AttackerActionLog(
        String logId,
        String compositeHash,
        String attemptedEndpoint,
        String httpMethod,
        String payloadCaptured,
        Instant timestamp) {
    public AttackerActionLog {
        if (logId == null || logId.isBlank()) throw new IllegalArgumentException("logId is required");
        compositeHash = compositeHash == null ? "unknown" : compositeHash;
        attemptedEndpoint = bounded(attemptedEndpoint == null ? "unknown" : attemptedEndpoint, 512);
        httpMethod = bounded(httpMethod == null ? "UNKNOWN" : httpMethod, 16);
        payloadCaptured = bounded(payloadCaptured == null ? "" : payloadCaptured, 8192);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    private static String bounded(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
