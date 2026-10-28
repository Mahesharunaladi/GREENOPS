package com.greenops.model;

import java.time.Instant;
import java.util.Map;

/** Non-secret readiness summary for the console. */
public record SpireReadiness(
        boolean ready,
        String status,
        String message,
        Map<String, Boolean> checks,
        long attestationsSeen,
        Instant lastAttestationAt,
        Boolean lastAttestationVerified,
        Instant checkedAt) {
    public SpireReadiness {
        checks = checks == null ? Map.of() : Map.copyOf(checks);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }
}
