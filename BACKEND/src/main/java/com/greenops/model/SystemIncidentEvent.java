package com.greenops.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable security or operations incident sent to the notification pipeline. */
public record SystemIncidentEvent(
        String incidentId,
        String eventType,
        String severity,
        String details,
        Instant timestamp) {
    private static final Set<String> EVENT_TYPES = Set.of(
            "SPIRE_REVOCATION", "PIPELINE_FAILURE", "DOCKER_CONTAINER_CRASH", "BIOMETRIC_ANOMALY");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARN", "CRITICAL");

    public SystemIncidentEvent {
        incidentId = incidentId == null || incidentId.isBlank() ? UUID.randomUUID().toString() : incidentId.trim();
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (!EVENT_TYPES.contains(eventType)) throw new IllegalArgumentException("Unsupported eventType");
        if (!SEVERITIES.contains(severity)) throw new IllegalArgumentException("Unsupported severity");
        if (details == null || details.isBlank()) throw new IllegalArgumentException("details must not be blank");
        details = details.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
        if (details.length() > 512) details = details.substring(0, 512);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
