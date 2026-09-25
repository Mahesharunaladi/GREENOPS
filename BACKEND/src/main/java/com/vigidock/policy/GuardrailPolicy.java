package com.vigidock.policy;

import java.util.Objects;

/**
 * A team-defined DevSecOps policy applied before a generated fix can be proposed or submitted.
 */
public record GuardrailPolicy(String id, RuleType ruleType, boolean blocking, String value) {

    public GuardrailPolicy {
        id = requireText(id, "id");
        Objects.requireNonNull(ruleType, "ruleType must not be null");
        value = value == null ? "" : value.trim();
    }

    public enum RuleType {
        DISALLOW_ROOT_USER,
        MAX_BASE_IMAGE_AGE_DAYS,
        BLOCK_MINIMUM_SEVERITY
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
