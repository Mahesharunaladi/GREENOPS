package com.greenops.workflow.security;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result produced by the interference detection workflow.
 *
 * <p>The assessment captures the highest threat level observed for a telemetry event and the
 * specific reasons that contributed to that decision. It intentionally avoids storing raw tokens or
 * high-cardinality request data, making it suitable for audit logs, policy engines, and session
 * revocation decisions.
 *
 * <p><strong>Thread safety:</strong> this record defensively copies the reason list into an
 * unmodifiable container. Instances are immutable and safe to share across servlet, worker, or AWS
 * Lambda invocation threads.
 */
public record SecurityThreatAssessment(
        String eventId,
        String sessionTokenHash,
        ThreatLevel threatLevel,
        List<String> detectionReasons,
        Instant assessedAt) {

    public SecurityThreatAssessment {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionTokenHash, "sessionTokenHash must not be null");
        Objects.requireNonNull(threatLevel, "threatLevel must not be null");
        detectionReasons = detectionReasons == null ? List.of() : List.copyOf(detectionReasons);
        assessedAt = assessedAt == null ? Instant.now() : assessedAt;
    }

    /**
     * Ordered threat severity used by Zero Trust policy enforcement.
     */
    public enum ThreatLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
