package com.greenops.workflow.notification;

import com.greenops.scrapper.auth.EmailAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** User-facing notification history and authenticated CI/container event ingestion. */
@RestController
@Validated
public final class OperationsNotificationController {
    private static final String SESSION_COOKIE = "greenops_session";
    private final SecurityNotificationService notifications;
    private final EmailAuthService auth;
    private final byte[] ingestKey;

    public OperationsNotificationController(SecurityNotificationService notifications, EmailAuthService auth,
            @Value("${greenops.notifications.operations-ingest-key:}") String ingestKey) {
        this.notifications = notifications;
        this.auth = auth;
        this.ingestKey = ingestKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/api/v1/notifications")
    public List<SecurityNotificationService.SecurityNotification> recent(HttpServletRequest request,
            @RequestParam(defaultValue = "20") int limit) {
        String session = request.getCookies() == null ? null : java.util.Arrays.stream(request.getCookies())
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName())).map(cookie -> cookie.getValue()).findFirst().orElse(null);
        String email = auth.emailForValidSession(session).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified account session is required."));
        return notifications.recentFor(email, limit);
    }

    @PostMapping("/api/v1/operations/events")
    public reactor.core.publisher.Mono<SecurityNotificationService.SecurityNotification> ingest(
            @Valid @RequestBody OperationsEventRequest event,
            @RequestHeader(name = "X-GreenOps-Operations-Key", required = false) String suppliedKey) {
        requireIngestKey(suppliedKey);
        return notifications.notifyUser(event.userEmail(), event.source(), event.severity(), event.summary());
    }

    private void requireIngestKey(String supplied) {
        if (ingestKey.length < 32 || supplied == null
                || !MessageDigest.isEqual(ingestKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operations event ingestion is not authorized.");
        }
    }

    public record OperationsEventRequest(
            @NotBlank @Email @Size(max = 254) String userEmail,
            @NotBlank @Pattern(regexp = "PIPELINE|DOCKER|CI") String source,
            @NotBlank @Pattern(regexp = "WARN|ERROR|CRITICAL") String severity,
            @NotBlank @Size(max = 512) String summary) {}
}
