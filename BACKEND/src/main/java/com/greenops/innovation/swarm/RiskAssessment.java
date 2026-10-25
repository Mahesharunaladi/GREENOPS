package com.greenops.innovation.swarm;

import java.util.Map;

public record RiskAssessment(String agent, double score, double weight, Map<String, String> evidence) {
    public RiskAssessment {
        if (agent == null || agent.isBlank() || !Double.isFinite(score) || score < 0 || score > 1
                || !Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("invalid risk assessment");
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
