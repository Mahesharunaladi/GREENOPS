package com.vigidock.policy;

import com.vigidock.model.ScanRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Evaluates organization-specific deployment and vulnerability guardrails.
 *
 * <p>The engine is deterministic and stateless. Blocking violations prevent automatic pull-request
 * creation, while non-blocking violations remain visible in the dry-run plan.
 */
@Service
public final class GuardrailPolicyEngine {

    private static final Pattern ROOT_USER = Pattern.compile("(?im)^[ \\t]*USER[ \\t]+(root|0)[ \\t]*$");
    private static final Pattern RUN_AS_NON_ROOT = Pattern.compile("(?im)^[ \\t]*runAsNonRoot:[ \\t]*true[ \\t]*$");

    /**
     * Evaluates an artifact and scan against the supplied organizational policies.
     *
     * @param artifact Dockerfile or Kubernetes manifest under review
     * @param scanRecord current vulnerability scan
     * @param policies organization policies; an empty list allows the artifact
     * @return policy outcome and all detected violations
     */
    public GuardrailEvaluation evaluate(
            IacArtifact artifact, ScanRecord scanRecord, List<GuardrailPolicy> policies) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        Objects.requireNonNull(scanRecord, "scanRecord must not be null");
        List<GuardrailEvaluation.Violation> violations = new ArrayList<>();
        for (GuardrailPolicy policy : policies == null ? List.<GuardrailPolicy>of() : List.copyOf(policies)) {
            switch (policy.ruleType()) {
                case DISALLOW_ROOT_USER -> evaluateRootUser(artifact, policy, violations);
                case MAX_BASE_IMAGE_AGE_DAYS -> evaluateBaseImageAge(artifact, policy, violations);
                case BLOCK_MINIMUM_SEVERITY -> evaluateSeverity(scanRecord, policy, violations);
            }
        }
        boolean allowed = violations.stream().noneMatch(GuardrailEvaluation.Violation::blocking);
        return new GuardrailEvaluation(allowed, violations, Instant.now());
    }

    private void evaluateRootUser(
            IacArtifact artifact, GuardrailPolicy policy, List<GuardrailEvaluation.Violation> violations) {
        boolean violation = artifact.type() == IacArtifact.ArtifactType.DOCKERFILE
                ? ROOT_USER.matcher(artifact.content()).find()
                : !RUN_AS_NON_ROOT.matcher(artifact.content()).find();
        if (violation) {
            String message = artifact.type() == IacArtifact.ArtifactType.DOCKERFILE
                    ? "Dockerfile runs as root or UID 0"
                    : "Kubernetes manifest does not set runAsNonRoot: true";
            violations.add(new GuardrailEvaluation.Violation(policy.id(), message, policy.blocking()));
        }
    }

    private void evaluateBaseImageAge(
            IacArtifact artifact, GuardrailPolicy policy, List<GuardrailEvaluation.Violation> violations) {
        int maxAgeDays = positiveInteger(policy.value(), policy.id());
        String imageCreatedAt = artifact.metadata().get("baseImageCreatedAt");
        if (imageCreatedAt == null || imageCreatedAt.isBlank()) {
            violations.add(new GuardrailEvaluation.Violation(
                    policy.id(),
                    "Base image creation timestamp is required for the maximum image age policy",
                    policy.blocking()));
            return;
        }
        try {
            long ageDays = Duration.between(Instant.parse(imageCreatedAt), Instant.now()).toDays();
            if (ageDays > maxAgeDays) {
                violations.add(new GuardrailEvaluation.Violation(
                        policy.id(),
                        "Base image age of " + ageDays + " days exceeds the " + maxAgeDays + " day limit",
                        policy.blocking()));
            }
        } catch (RuntimeException ex) {
            violations.add(new GuardrailEvaluation.Violation(
                    policy.id(), "baseImageCreatedAt must be an ISO-8601 instant", policy.blocking()));
        }
    }

    private void evaluateSeverity(
            ScanRecord scanRecord, GuardrailPolicy policy, List<GuardrailEvaluation.Violation> violations) {
        Severity threshold = Severity.parse(policy.value(), policy.id());
        int matchingFindings = findingsAtOrAbove(scanRecord, threshold);
        if (matchingFindings > 0) {
            violations.add(new GuardrailEvaluation.Violation(
                    policy.id(),
                    matchingFindings + " findings meet or exceed the " + threshold.name() + " severity threshold",
                    policy.blocking()));
        }
    }

    private int findingsAtOrAbove(ScanRecord scanRecord, Severity threshold) {
        return switch (threshold) {
            case CRITICAL -> scanRecord.criticalCount();
            case HIGH -> scanRecord.criticalCount() + scanRecord.highCount();
            case MEDIUM -> scanRecord.criticalCount() + scanRecord.highCount() + scanRecord.mediumCount();
            case LOW -> scanRecord.criticalCount() + scanRecord.highCount() + scanRecord.mediumCount() + scanRecord.lowCount();
        };
    }

    private static int positiveInteger(String value, String policyId) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Converted into a policy configuration failure below.
        }
        throw new IllegalArgumentException("Policy " + policyId + " requires a positive integer value");
    }

    private enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW;

        private static Severity parse(String value, String policyId) {
            try {
                return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "Policy " + policyId + " requires CRITICAL, HIGH, MEDIUM, or LOW as its value", ex);
            }
        }
    }
}
