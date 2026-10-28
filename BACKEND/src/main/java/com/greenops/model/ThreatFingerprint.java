package com.greenops.model;

import java.time.Instant;

/** Immutable composite network/process threat signature. */
public record ThreatFingerprint(
        String compositeHash,
        String ipAddress,
        String tlsJa3Hash,
        String userAgent,
        String spiffeSvid,
        double lastBiometricScore,
        Instant firstDetectedAt,
        Instant lastAttemptAt,
        int violationCount) {
    public ThreatFingerprint {
        if (compositeHash == null || compositeHash.isBlank()) throw new IllegalArgumentException("compositeHash is required");
        ipAddress = ipAddress == null ? "unknown" : ipAddress;
        tlsJa3Hash = tlsJa3Hash == null ? "unknown" : tlsJa3Hash;
        userAgent = userAgent == null ? "unknown" : userAgent;
        spiffeSvid = spiffeSvid == null ? "unknown" : spiffeSvid;
        if (!Double.isFinite(lastBiometricScore) || lastBiometricScore < 0 || lastBiometricScore > 1) throw new IllegalArgumentException("lastBiometricScore must be between 0 and 1");
        firstDetectedAt = firstDetectedAt == null ? Instant.now() : firstDetectedAt;
        lastAttemptAt = lastAttemptAt == null ? firstDetectedAt : lastAttemptAt;
        if (violationCount < 1) throw new IllegalArgumentException("violationCount must be positive");
    }
}
