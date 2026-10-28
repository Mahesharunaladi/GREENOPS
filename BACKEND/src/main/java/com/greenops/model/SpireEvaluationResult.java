package com.greenops.model;

import java.time.Instant;

/** Decision and evidence summary for a workload attestation request. */
public record SpireEvaluationResult(
        boolean isVerified,
        String riskTier,
        String spiffeId,
        String decisionReason,
        double baselineMatchScore,
        Instant timestamp) {

    public SpireEvaluationResult {
        riskTier = riskTier == null ? "CRITICAL" : riskTier;
        spiffeId = spiffeId == null ? "unknown" : spiffeId;
        decisionReason = decisionReason == null ? "unspecified" : decisionReason;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        if (!Double.isFinite(baselineMatchScore) || baselineMatchScore < 0 || baselineMatchScore > 1) {
            throw new IllegalArgumentException("baselineMatchScore must be between 0 and 1");
        }
    }
}
