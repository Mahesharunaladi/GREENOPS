package com.greenops.model;

import java.time.Instant;
import java.util.List;

public record ComplianceEvaluationResult(
        String framework,
        double complianceScorePercentage,
        List<String> passedRules,
        List<String> violations,
        Instant evaluatedAt) {
    public ComplianceEvaluationResult {
        passedRules = List.copyOf(passedRules);
        violations = List.copyOf(violations);
    }
}
