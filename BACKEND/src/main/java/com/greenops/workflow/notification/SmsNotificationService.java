package com.greenops.workflow.notification;

import com.greenops.model.SystemIncidentEvent;
import com.greenops.model.UserContactProfile;
import com.greenops.scrapper.auth.UserPhoneAlertService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Sends incident SMS through AWS SNS to verified, account-linked phone numbers. */
@Service
public final class SmsNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationService.class);
    private final SnsAsyncClient sns;
    private final CloudWatchAsyncClient cloudWatch;
    private final UserPhoneAlertService verifiedPhones;
    private final boolean enabled;
    private final String alertActionUrl;
    private final ConcurrentHashMap<String, UserContactProfile> profiles = new ConcurrentHashMap<>();

    public SmsNotificationService(SnsAsyncClient sns, CloudWatchAsyncClient cloudWatch,
            UserPhoneAlertService verifiedPhones,
            @Value("${greenops.notifications.sms-enabled:false}") boolean enabled,
            @Value("${greenops.notifications.action-url:}") String alertActionUrl) {
        this.sns = sns;
        this.cloudWatch = cloudWatch;
        this.verifiedPhones = verifiedPhones;
        this.enabled = enabled;
        this.alertActionUrl = alertActionUrl == null ? "" : alertActionUrl.trim();
    }

    /** Persist in the active process only, and only after matching the phone already OTP-verified for this user. */
    public UserContactProfile registerVerifiedProfile(String authenticatedUserId, String phoneNumber,
            String spiffeId, boolean smsAlertsEnabled, boolean autoStepUpOnAnomaly) {
        UserContactProfile profile = new UserContactProfile(authenticatedUserId, phoneNumber, spiffeId,
                smsAlertsEnabled, autoStepUpOnAnomaly);
        String verified = verifiedPhones.verifiedPhoneNumber(authenticatedUserId)
                .orElseThrow(() -> new IllegalStateException("Verify a phone number through account settings before registering an alert profile."));
        if (!verified.equals(profile.phoneNumber())) {
            throw new IllegalArgumentException("The profile phone number must match the verified account phone.");
        }
        profiles.put(key(authenticatedUserId), profile);
        return profile;
    }

    public Optional<UserContactProfile> profileFor(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        String key = key(userId);
        UserContactProfile configured = profiles.get(key);
        if (configured != null) return Optional.of(configured);
        // Existing verified-phone enrollment remains usable without a second registration step.
        return verifiedPhones.verifiedPhoneNumber(userId)
                .map(phone -> new UserContactProfile(userId, phone, "", true, false));
    }

    public Mono<Void> dispatchIncidentAlert(String userId, SystemIncidentEvent incident) {
        return Mono.defer(() -> {
            if (!enabled) return Mono.error(new IllegalStateException("SMS notifications are disabled"));
            UserContactProfile profile = profileFor(userId)
                    .filter(contact -> contact.smsAlertsEnabled())
                    .orElseThrow(() -> new IllegalStateException("No SMS-enabled verified contact is registered for this user"));
            String message = format(incident);
            PublishRequest request = PublishRequest.builder()
                    .phoneNumber(profile.phoneNumber())
                    .message(message)
                    .messageAttributes(Map.of("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                            .dataType("String").stringValue("Transactional").build()))
                    .build();
            CompletableFuture<?> publish = sns.publish(request);
            return Mono.fromFuture(publish)
                    .doOnSuccess(ignored -> emitMetric("greenops.notifications.sms_sent"))
                    .then();
        }).doOnError(error -> {
            emitMetric("greenops.notifications.delivery_failed");
            log.warn("Incident SMS dispatch failed: incidentId={}, errorType={}", incident.incidentId(), error.getClass().getSimpleName());
        });
    }

    private String format(SystemIncidentEvent incident) {
        String message = "[GREENOPS ALERT] " + incident.severity() + " " + incident.eventType()
                + ": " + incident.details();
        if (!alertActionUrl.isBlank() && incident.severity().equals("CRITICAL")) {
            message += " Action: " + alertActionUrl;
        }
        return message.length() <= 480 ? message : message.substring(0, 480);
    }

    private void emitMetric(String name) {
        try {
            cloudWatch.putMetricData(PutMetricDataRequest.builder()
                    .namespace("GreenOps/Notifications")
                    .metricData(MetricDatum.builder().metricName(name).unit(StandardUnit.COUNT).value(1.0)
                            .timestamp(Instant.now()).build())
                    .build()).whenComplete((ignored, error) -> {
                        if (error != null) log.debug("CloudWatch metric emission failed: metric={}, errorType={}", name, error.getClass().getSimpleName());
                    });
        } catch (RuntimeException error) {
            log.debug("CloudWatch metric emission failed: metric={}, errorType={}", name, error.getClass().getSimpleName());
        }
    }

    private static String key(String userId) { return userId.trim().toLowerCase(Locale.ROOT); }
}
