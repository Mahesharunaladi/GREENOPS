package com.greenops.model;

import java.time.Instant;

public record TtcRiskAssessment(
        String userId,
        double timeToCompromiseMinutes,
        double exposureProbabilityPercentage,
        String primaryRiskFactor,
        Instant predictedAt) {
    public TtcRiskAssessment {
        if (!Double.isFinite(timeToCompromiseMinutes) || timeToCompromiseMinutes < 0) throw new IllegalArgumentException("timeToCompromiseMinutes must be non-negative");
        if (!Double.isFinite(exposureProbabilityPercentage) || exposureProbabilityPercentage < 0 || exposureProbabilityPercentage > 100) throw new IllegalArgumentException("exposureProbabilityPercentage must be between 0 and 100");
    }
}
