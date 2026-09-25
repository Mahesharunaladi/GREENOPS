package com.vigidock.policy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Policy evaluation outcome used to gate pull-request creation.
 */
public record GuardrailEvaluation(boolean allowed, List<Violation> violations, Instant evaluatedAt) {

    public GuardrailEvaluation {
        violations = violations == null ? List.of() : List.copyOf(violations);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    /**
     * A policy violation with enough context for a developer to act on it.
     */
    public record Violation(String policyId, String message, boolean blocking) {
        public Violation {
            Objects.requireNonNull(policyId, "policyId must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }
}
