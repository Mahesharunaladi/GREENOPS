package com.greenops.workflow.identity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable decision emitted by the Zero Trust identity agent.
 *
 * <p>The result captures the model score, the autonomous enforcement decision, and actionable
 * metadata for policy engines, audit trails, and session orchestration. Raw bearer tokens and
 * high-cardinality biometric samples are intentionally excluded.
 *
 * <p><strong>Thread safety:</strong> this record defensively copies metadata into an unmodifiable
 * map. Instances are immutable and safe to share across request, worker, or AWS Lambda threads.
 */
public record RiskEvaluationResult(
        String eventId,
        String sessionTokenHash,
        double anomalyScore,
        AgentDecision decision,
        String action,
        Map<String, String> metadata,
        Instant evaluatedAt) {

    public RiskEvaluationResult {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionTokenHash, "sessionTokenHash must not be null");
        if (!Double.isFinite(anomalyScore) || anomalyScore < 0.0d || anomalyScore > 1.0d) {
            throw new IllegalArgumentException("anomalyScore must be a finite value between 0 and 1");
        }
        Objects.requireNonNull(decision, "decision must not be null");
        action = action == null || action.isBlank() ? decision.defaultAction() : action;
        metadata = immutableMetadata(metadata);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    /**
     * Agentic enforcement decision selected from the anomaly score.
     */
    public enum AgentDecision {
        REVOKE_SESSION("revoke_session"),
        STEP_UP_AUTHENTICATION("trigger_mfa_challenge"),
        SEAMLESS_REVERIFY("seamless_reverify");

        private final String defaultAction;

        AgentDecision(String defaultAction) {
            this.defaultAction = defaultAction;
        }

        public String defaultAction() {
            return defaultAction;
        }
    }

    private static Map<String, String> immutableMetadata(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "metadata keys must not be null"),
                Objects.requireNonNull(value, "metadata values must not be null")));
        return Map.copyOf(copy);
    }
}
