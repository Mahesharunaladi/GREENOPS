package com.greenops.workflow.telemetry;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable telemetry event emitted by the ingestion layer for identity verification and third-party
 * interference detection.
 *
 * <p>The event intentionally stores a cryptographic hash of the OAuth/OIDC token rather than the raw
 * bearer token. Downstream services can correlate a session without receiving reusable credentials.
 * Keystroke data is represented both as normalized sequences and as a fixed-order feature vector for
 * machine learning inference.
 *
 * <p><strong>Thread safety:</strong> this record and all nested records defensively copy collection
 * inputs into unmodifiable containers. Instances are immutable and safe to share across threads.
 */
public record TelemetryEvent(
        String eventId,
        String sessionTokenHash,
        TokenContext tokenContext,
        DeviceContext deviceContext,
        KeystrokeTelemetry keystrokeTelemetry,
        MouseTelemetry mouseTelemetry,
        Instant observedAt,
        Map<String, String> attributes) {

    public TelemetryEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionTokenHash, "sessionTokenHash must not be null");
        Objects.requireNonNull(tokenContext, "tokenContext must not be null");
        Objects.requireNonNull(deviceContext, "deviceContext must not be null");
        Objects.requireNonNull(keystrokeTelemetry, "keystrokeTelemetry must not be null");
        Objects.requireNonNull(mouseTelemetry, "mouseTelemetry must not be null");
        observedAt = observedAt == null ? Instant.now() : observedAt;
        attributes = immutableStringMap(attributes);
    }

    /**
     * Non-sensitive token metadata extracted from a JWT when the incoming session token is an
     * OAuth/OIDC JWT. Raw claims are intentionally not retained.
     */
    public record TokenContext(String issuer, String subject, String audience, Instant expiresAt, boolean jwtFormat) {
        public TokenContext {
            issuer = normalizeUnknown(issuer);
            subject = normalizeUnknown(subject);
            audience = normalizeUnknown(audience);
        }
    }

    /**
     * Coarse device and transport context used for IP drift, JA3 drift, and suspicious automation
     * checks.
     */
    public record DeviceContext(
            String clientIp,
            String ipVersion,
            String ipScope,
            String tlsJa3Fingerprint,
            String deviceType,
            String operatingSystem,
            String browserFamily,
            boolean automationSuspected) {

        public DeviceContext {
            Objects.requireNonNull(clientIp, "clientIp must not be null");
            Objects.requireNonNull(ipVersion, "ipVersion must not be null");
            Objects.requireNonNull(ipScope, "ipScope must not be null");
            Objects.requireNonNull(tlsJa3Fingerprint, "tlsJa3Fingerprint must not be null");
            Objects.requireNonNull(deviceType, "deviceType must not be null");
            Objects.requireNonNull(operatingSystem, "operatingSystem must not be null");
            Objects.requireNonNull(browserFamily, "browserFamily must not be null");
        }
    }

    /**
     * Normalized keystroke biometrics for dwell and flight timing inference.
     *
     * @param normalizedDwellTimes z-score normalized key dwell times
     * @param normalizedFlightTimes z-score normalized inter-key flight times
     * @param featureNames fixed feature order for {@code featureVector}
     * @param featureVector fixed-order ML feature vector
     */
    public record KeystrokeTelemetry(
            List<Double> normalizedDwellTimes,
            List<Double> normalizedFlightTimes,
            List<String> featureNames,
            List<Double> featureVector) {

        public KeystrokeTelemetry {
            normalizedDwellTimes = immutableDoubleList(normalizedDwellTimes);
            normalizedFlightTimes = immutableDoubleList(normalizedFlightTimes);
            featureNames = immutableStringList(featureNames);
            featureVector = immutableDoubleList(featureVector);
            if (featureNames.size() != featureVector.size()) {
                throw new IllegalArgumentException("featureNames and featureVector must have the same size");
            }
        }
    }

    /**
     * Aggregate mouse dynamics suitable for interaction-pattern anomaly models.
     */
    public record MouseTelemetry(
            int sampleCount,
            double totalDistancePixels,
            double averageSpeedPixelsPerSecond,
            double maxSpeedPixelsPerSecond,
            double directionChangeRate,
            double idleRatio) {

        public MouseTelemetry {
            requireNonNegative(sampleCount, "sampleCount");
            requireNonNegative(totalDistancePixels, "totalDistancePixels");
            requireNonNegative(averageSpeedPixelsPerSecond, "averageSpeedPixelsPerSecond");
            requireNonNegative(maxSpeedPixelsPerSecond, "maxSpeedPixelsPerSecond");
            requireRatio(directionChangeRate, "directionChangeRate");
            requireRatio(idleRatio, "idleRatio");
        }
    }

    private static List<Double> immutableDoubleList(List<Double> values) {
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(values.stream()
                .map(value -> {
                    Objects.requireNonNull(value, "numeric telemetry values must not contain nulls");
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("numeric telemetry values must be finite");
                    }
                    return value;
                })
                .toList());
    }

    private static List<String> immutableStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(values.stream()
                .map(value -> Objects.requireNonNull(value, "string telemetry values must not contain nulls"))
                .toList());
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "attribute keys must not be null"),
                Objects.requireNonNull(value, "attribute values must not be null")));
        return Collections.unmodifiableMap(copy);
    }

    private static String normalizeUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static void requireNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be a finite non-negative value");
        }
    }

    private static void requireRatio(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be a finite ratio between 0 and 1");
        }
    }
}
