package com.greenops.model;

import java.util.Objects;

/** A verified user's notification preferences and workload identity binding. */
public record UserContactProfile(
        String userId,
        String phoneNumber,
        String spiffeId,
        boolean smsAlertsEnabled,
        boolean autoStepUpOnAnomaly) {
    public UserContactProfile {
        userId = requireText(userId, "userId");
        phoneNumber = requireText(phoneNumber, "phoneNumber");
        if (!phoneNumber.matches("\\+[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("phoneNumber must use E.164 format");
        }
        spiffeId = spiffeId == null ? "" : spiffeId.trim();
        if (!spiffeId.isEmpty() && !spiffeId.startsWith("spiffe://greenops.internal/")) {
            throw new IllegalArgumentException("spiffeId must be within the GreenOps trust domain");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
