package com.vigidock.remediation;

import com.vigidock.epss.PrioritizedScan;
import com.vigidock.policy.GuardrailEvaluation;
import java.time.Instant;
import java.util.Objects;

/**
 * Complete pre-application review package for one VigiDock remediation.
 */
public record RemediationPlan(
        PrioritizedScan prioritizedScan,
        GuardrailEvaluation guardrailEvaluation,
        FixProposal fixProposal,
        Instant createdAt) {

    public RemediationPlan {
        Objects.requireNonNull(prioritizedScan, "prioritizedScan must not be null");
        Objects.requireNonNull(guardrailEvaluation, "guardrailEvaluation must not be null");
        Objects.requireNonNull(fixProposal, "fixProposal must not be null");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
