package com.greenops.workflow.notification;

import com.greenops.model.UserContactProfile;
import com.greenops.scrapper.auth.EmailAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Account-authenticated profile setup and shared-secret protected CI/Docker webhook. */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {
    private static final String SESSION_COOKIE = "greenops_session";
    private final EmailAuthService auth;
    private final SmsNotificationService smsNotifications;
    private final PipelineHealthWatcher pipelineWatcher;
    private final byte[] webhookSecret;

    public NotificationController(EmailAuthService auth, SmsNotificationService smsNotifications,
            PipelineHealthWatcher pipelineWatcher,
            @Value("${greenops.notifications.operations-ingest-key:}") String webhookSecret) {
        this.auth = auth;
        this.smsNotifications = smsNotifications;
        this.pipelineWatcher = pipelineWatcher;
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/profile/phone")
    public ProfileResponse registerPhoneProfile(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken,
            @Valid @RequestBody ProfileRequest request) {
        String userId = auth.emailForValidSession(sessionToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified account session is required."));
        try {
            UserContactProfile profile = smsNotifications.registerVerifiedProfile(userId, request.phoneNumber(),
                    request.spiffeId(), request.smsAlertsEnabled(), request.autoStepUpOnAnomaly());
            return new ProfileResponse(profile.userId(), mask(profile.phoneNumber()), profile.spiffeId(),
                    profile.smsAlertsEnabled(), profile.autoStepUpOnAnomaly());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
        }
    }

    @PostMapping("/webhook/pipeline-failure")
    public Mono<Void> pipelineFailure(@Valid @RequestBody PipelineFailureRequest request,
            @RequestHeader(name = "X-GreenOps-Operations-Key", required = false) String suppliedSecret) {
        requireWebhookSecret(suppliedSecret);
        return pipelineWatcher.monitorDockerAndPipelineEvents(request.containerId(), request.status(), request.exitCode());
    }

    private void requireWebhookSecret(String supplied) {
        if (webhookSecret.length < 32 || supplied == null
                || !MessageDigest.isEqual(webhookSecret, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pipeline webhook is not authorized.");
        }
    }

    private static String mask(String phone) { return "••••••" + phone.substring(Math.max(0, phone.length() - 4)); }

    public record ProfileRequest(
            @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phoneNumber,
            @Size(max = 255) String spiffeId,
            boolean smsAlertsEnabled,
            boolean autoStepUpOnAnomaly) {}

    public record PipelineFailureRequest(
            @NotBlank @Size(max = 120) String containerId,
            @NotBlank @Pattern(regexp = "(?i)CRASHED|FAILED|ERROR|RUNNING|SUCCEEDED|COMPLETED") String status,
            @Pattern(regexp = "-?[0-9]{1,8}") String exitCode) {}

    public record ProfileResponse(String userId, String maskedPhoneNumber, String spiffeId,
            boolean smsAlertsEnabled, boolean autoStepUpOnAnomaly) {}
}
