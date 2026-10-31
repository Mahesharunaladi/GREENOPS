package com.greenops.workflow.notification;

import com.greenops.model.SystemIncidentEvent;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Converts authenticated Docker/CI health events into alerts for the configured on-call account. */
@Service
public final class PipelineHealthWatcher {
    private static final Logger log = LoggerFactory.getLogger(PipelineHealthWatcher.class);
    private final SecurityNotificationService notifications;
    private final String onCallUserId;

    public PipelineHealthWatcher(SecurityNotificationService notifications,
            @Value("${greenops.notifications.on-call-user-id:}") String onCallUserId) {
        this.notifications = notifications;
        this.onCallUserId = onCallUserId == null ? "" : onCallUserId.trim();
    }

    public Mono<Void> monitorDockerAndPipelineEvents(String containerId, String status, String exitCode) {
        return Mono.defer(() -> {
            String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
            Integer parsedExitCode = parseExitCode(exitCode);
            boolean failed = normalizedStatus.equals("CRASHED") || normalizedStatus.equals("FAILED")
                    || normalizedStatus.equals("ERROR") || parsedExitCode != null && parsedExitCode != 0;
            if (!failed) return Mono.empty();
            if (onCallUserId.isBlank()) return Mono.error(new IllegalStateException("No notification on-call user is configured"));
            String safeTarget = safe(containerId);
            String type = normalizedStatus.equals("CRASHED") ? "DOCKER_CONTAINER_CRASH" : "PIPELINE_FAILURE";
            String detail = "Target=" + safeTarget + "; status=" + safe(normalizedStatus)
                    + (parsedExitCode == null ? "" : "; exitCode=" + parsedExitCode);
            SystemIncidentEvent incident = new SystemIncidentEvent(UUID.randomUUID().toString(), type, "CRITICAL", detail, Instant.now());
            log.warn("Pipeline/container failure received: incidentId={}, type={}, target={}", incident.incidentId(), type, safeTarget);
            return notifications.notifyIncident(onCallUserId, incident).then();
        });
    }

    private static Integer parseExitCode(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("exitCode must be an integer", error); }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unspecified";
        String clean = value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }
}
