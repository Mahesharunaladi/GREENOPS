package com.greenops.scrapper.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Verified phone enrollment and transactional SMS delivery through AWS SNS. */
@Service
public final class UserPhoneAlertService {
    private static final Logger log = LoggerFactory.getLogger(UserPhoneAlertService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SnsAsyncClient sns;
    private final boolean enabled;
    private final byte[] otpPepper = randomBytes(32);
    private final Map<String, Contact> contacts = new ConcurrentHashMap<>();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public UserPhoneAlertService(SnsAsyncClient sns, @Value("${greenops.notifications.sms-enabled:false}") boolean enabled) {
        this.sns = sns;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    public PhoneStatus status(String email) {
        Contact contact = contacts.get(normalizeEmail(email));
        return new PhoneStatus(enabled, contact != null && contact.verified(), contact == null ? "" : mask(contact.phoneNumber()));
    }

    public Mono<PhoneStatus> requestVerification(String email, String phoneNumber) {
        if (!enabled) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS delivery is disabled; configure GREENOPS_SMS_ENABLED and AWS SNS."));
        if (email == null || email.isBlank()) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified account session is required."));
        if (phoneNumber == null || !phoneNumber.matches("\\+[1-9][0-9]{7,14}")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a phone number in E.164 format, for example +14155550123."));
        }
        String account = normalizeEmail(email);
        Instant previous = lastSentAt.get(account);
        if (previous != null && previous.plus(SEND_COOLDOWN).isAfter(Instant.now())) {
            return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another phone verification code."));
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Challenge challenge = new Challenge(phoneNumber, otpHash(account, code), Instant.now().plus(OTP_TTL), 0);
        challenges.put(account, challenge);
        return publish(phoneNumber, "GreenOps phone verification code: " + code + ". It expires in 10 minutes.")
                .doOnSuccess(ignored -> lastSentAt.put(account, Instant.now()))
                .thenReturn(new PhoneStatus(true, false, mask(phoneNumber)))
                .onErrorResume(error -> {
                    challenges.remove(account, challenge);
                    log.warn("Phone verification SMS could not be sent: account={}, errorType={}", account, error.getClass().getSimpleName());
                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send the verification code through the configured SMS provider."));
                });
    }

    public Mono<PhoneStatus> verify(String email, String code) {
        if (email == null || email.isBlank()) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified account session is required."));
        if (code == null || !code.matches("[0-9]{6}")) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the six-digit verification code."));
        String account = normalizeEmail(email);
        Challenge challenge = challenges.get(account);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            challenges.remove(account);
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "The phone verification code expired. Request another code."));
        }
        if (!MessageDigest.isEqual(challenge.codeHash(), otpHash(account, code))) {
            if (challenge.failedAttempts() >= 4) challenges.remove(account, challenge);
            else challenges.replace(account, challenge, challenge.failed());
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "The phone verification code is not valid."));
        }
        challenges.remove(account, challenge);
        contacts.put(account, new Contact(challenge.phoneNumber(), true));
        log.info("Verified alert phone number linked to account={}", account);
        return Mono.just(status(account));
    }

    public Mono<Void> notifyUser(String userIdOrEmail, String title, String detail) {
        if (!enabled || userIdOrEmail == null || userIdOrEmail.isBlank()) return Mono.empty();
        Contact contact = contacts.get(normalizeEmail(userIdOrEmail));
        if (contact == null || !contact.verified()) return Mono.empty();
        String message = "GreenOps alert: " + bound(title, 100) + ". " + bound(detail, 260);
        return publish(contact.phoneNumber(), message)
                .doOnSuccess(ignored -> log.info("Security SMS notification delivered: account={}, alert={}", normalizeEmail(userIdOrEmail), bound(title, 80)))
                .onErrorResume(error -> {
                    log.error("Security SMS notification delivery failed: account={}, errorType={}", normalizeEmail(userIdOrEmail), error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    private Mono<Void> publish(String phoneNumber, String message) {
        Map<String, MessageAttributeValue> attributes = Map.of("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                .dataType("String").stringValue("Transactional").build());
        CompletableFuture<?> result = sns.publish(PublishRequest.builder().phoneNumber(phoneNumber)
                .message(bound(message, 480)).messageAttributes(attributes).build());
        return Mono.fromFuture(result).then();
    }

    private byte[] otpHash(String account, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpPepper, "HmacSHA256"));
            return mac.doFinal((account + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new IllegalStateException("Could not secure phone verification code", failure); }
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static String bound(String value, int limit) {
        if (value == null) return "";
        String safe = value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }
    private static String mask(String phone) { return phone.length() < 5 ? "••••" : "••••••" + phone.substring(phone.length() - 4); }
    private static byte[] randomBytes(int size) { byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return bytes; }

    private record Contact(String phoneNumber, boolean verified) {}
    private record Challenge(String phoneNumber, byte[] codeHash, Instant expiresAt, int failedAttempts) {
        Challenge failed() { return new Challenge(phoneNumber, codeHash, expiresAt, failedAttempts + 1); }
    }
    public record PhoneStatus(boolean smsEnabled, boolean verified, String maskedPhoneNumber) {}
}
