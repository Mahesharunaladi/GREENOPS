package com.greenops.workflow.notification;

import com.greenops.model.SystemIncidentEvent;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Retains a bounded notification history per account and sends SMS to verified phone contacts. */
@Service
public final class SecurityNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SecurityNotificationService.class);
    private static final int MAX_PER_ACCOUNT = 100;
    private final SmsNotificationService smsNotifications;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<SecurityNotification>> notifications = new ConcurrentHashMap<>();

    public SecurityNotificationService(SmsNotificationService smsNotifications) { this.smsNotifications = smsNotifications; }

    public Mono<SecurityNotification> notifyUser(String userIdOrEmail, String source, String severity, String summary) {
        if (userIdOrEmail == null || !userIdOrEmail.contains("@")) {
            log.warn("Security event has no email-mapped user for notification: source={}, userId={}", source, userIdOrEmail);
            return Mono.empty();
        }
        String email = userIdOrEmail.trim().toLowerCase(Locale.ROOT);
        SecurityNotification notification = new SecurityNotification(UUID.randomUUID().toString(), email,
                bound(source, 32), bound(severity, 16), bound(summary, 512), Instant.now());
        String eventType = notification.source().contains("SPIRE") ? "SPIRE_REVOCATION"
                : notification.source().contains("DOCKER") ? "DOCKER_CONTAINER_CRASH"
                : notification.source().contains("PIPELINE") || notification.source().equals("CI") ? "PIPELINE_FAILURE"
                : "BIOMETRIC_ANOMALY";
        String mappedSeverity = notification.severity().equals("CRITICAL") ? "CRITICAL"
                : notification.severity().equals("INFO") ? "INFO" : "WARN";
        SystemIncidentEvent incident = new SystemIncidentEvent(notification.eventId(), eventType, mappedSeverity,
                notification.summary(), notification.occurredAt());
        return dispatchAndRecord(email, notification, incident);
    }

    public Mono<SecurityNotification> notifyIncident(String userIdOrEmail, SystemIncidentEvent incident) {
        if (userIdOrEmail == null || !userIdOrEmail.contains("@")) return Mono.empty();
        String email = userIdOrEmail.trim().toLowerCase(Locale.ROOT);
        SecurityNotification notification = new SecurityNotification(incident.incidentId(), email,
                incident.eventType(), incident.severity(), incident.details(), incident.timestamp());
        return dispatchAndRecord(email, notification, incident);
    }

    private Mono<SecurityNotification> dispatchAndRecord(String email, SecurityNotification notification,
            SystemIncidentEvent incident) {
        ConcurrentLinkedDeque<SecurityNotification> accountEvents = notifications.computeIfAbsent(email, ignored -> new ConcurrentLinkedDeque<>());
        accountEvents.addFirst(notification);
        while (accountEvents.size() > MAX_PER_ACCOUNT) accountEvents.pollLast();
        return smsNotifications.dispatchIncidentAlert(email, incident)
                .onErrorResume(error -> {
                    log.warn("Phone alert dispatch failed for security event {}, errorType={}", notification.eventId(), error.getClass().getSimpleName());
                    return Mono.empty();
                })
                .thenReturn(notification);
    }

    public List<SecurityNotification> recentFor(String email, int limit) {
        if (email == null || email.isBlank()) return List.of();
        var events = notifications.get(email.trim().toLowerCase(Locale.ROOT));
        if (events == null) return List.of();
        return events.stream().limit(Math.max(1, Math.min(limit, 100))).toList();
    }

    private static String bound(String value, int max) {
        if (value == null) return "unspecified";
        String safe = value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
        if (safe.isBlank()) return "unspecified";
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public record SecurityNotification(String eventId, String userId, String source, String severity, String summary, Instant occurredAt) {}
}
