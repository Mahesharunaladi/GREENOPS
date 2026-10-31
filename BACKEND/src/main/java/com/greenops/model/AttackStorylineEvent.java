package com.greenops.model;

import java.time.Instant;
import java.util.Set;

public record AttackStorylineEvent(
        String eventId,
        String sessionId,
        String stage,
        String actionDetails,
        String sourceIp,
        Instant timestamp) {
    private static final Set<String> STAGES = Set.of("INITIAL_AUTH", "BIOMETRIC_ANOMALY", "SPIRE_REVOCATION", "DECEPTION_REROUTE", "HONEYPOT_EXPLOIT");
    public AttackStorylineEvent {
        if (!STAGES.contains(stage)) throw new IllegalArgumentException("Unsupported attack storyline stage");
    }
}
