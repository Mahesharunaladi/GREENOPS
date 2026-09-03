package com.greenops.workflow.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ingests identity telemetry and converts it into immutable events for autonomous Zero Trust risk
 * scoring.
 *
 * <p>The ingestor accepts OAuth/OIDC session tokens, client network context, TLS JA3 fingerprints,
 * User-Agent values, keystroke dwell and flight timing vectors, and mouse movement samples. It
 * normalizes high-frequency biometric signals into bounded, deterministic structures that can be
 * passed to a machine learning model without mutating caller-owned collections.
 *
 * <p>Bearer tokens are never returned in the produced event. Instead, the ingestor emits a SHA-256
 * token hash and non-sensitive JWT metadata when the token has a valid three-segment JWT shape.
 *
 * <p><strong>Thread safety:</strong> this component is stateless except for immutable collaborators.
 * {@link ObjectMapper} is thread-safe after configuration and is used read-only here. Instances are
 * safe as singleton Spring beans and can also be reused by AWS Lambda handlers across invocations.
 */
@Component
public final class TelemetryIngestor {

    private static final List<String> KEYSTROKE_FEATURE_NAMES = List.of(
            "dwell_count",
            "dwell_mean_ms",
            "dwell_stddev_ms",
            "dwell_min_ms",
            "dwell_max_ms",
            "flight_count",
            "flight_mean_ms",
            "flight_stddev_ms",
            "flight_min_ms",
            "flight_max_ms");
    private static final double IDLE_THRESHOLD_MILLIS = 750.0d;

    private final DeviceContextExtractor deviceContextExtractor;
    private final ObjectMapper objectMapper;

    public TelemetryIngestor(DeviceContextExtractor deviceContextExtractor) {
        this(deviceContextExtractor, new ObjectMapper());
    }

    TelemetryIngestor(DeviceContextExtractor deviceContextExtractor, ObjectMapper objectMapper) {
        this.deviceContextExtractor = Objects.requireNonNull(deviceContextExtractor, "deviceContextExtractor must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Converts a raw telemetry payload into a normalized, strongly typed event.
     *
     * @param payload incoming identity telemetry payload
     * @return immutable event ready for risk scoring, session revocation policies, or durable storage
     */
    public TelemetryEvent ingest(TelemetryPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        String sessionToken = requireText(payload.sessionToken(), "sessionToken");
        TelemetryEvent.DeviceContext deviceContext = deviceContextExtractor.extract(
                payload.clientIp(), payload.tlsJa3Fingerprint(), payload.userAgent());
        return new TelemetryEvent(
                UUID.randomUUID().toString(),
                sha256Hex(sessionToken),
                extractTokenContext(sessionToken),
                deviceContext,
                normalizeKeystrokes(payload.dwellTimesMillis(), payload.flightTimesMillis()),
                summarizeMouseDynamics(payload.mouseMovements()),
                payload.observedAt(),
                payload.attributes());
    }

    /**
     * Normalizes dwell and flight time vectors for model inference.
     *
     * <p>The returned object contains z-score normalized sequences and a fixed-order feature vector
     * with counts and descriptive statistics. Z-score normalization is computed per vector and emits
     * {@code 0.0} for constant vectors to avoid division by zero.
     *
     * @param dwellTimesMillis key hold durations in milliseconds
     * @param flightTimesMillis inter-key transition durations in milliseconds
     * @return normalized keystroke telemetry
     */
    public TelemetryEvent.KeystrokeTelemetry normalizeKeystrokes(
            List<Double> dwellTimesMillis, List<Double> flightTimesMillis) {
        List<Double> dwell = immutableFiniteNonNegative(dwellTimesMillis, "dwellTimesMillis");
        List<Double> flight = immutableFiniteNonNegative(flightTimesMillis, "flightTimesMillis");
        VectorStats dwellStats = VectorStats.from(dwell);
        VectorStats flightStats = VectorStats.from(flight);

        List<Double> featureVector = List.of(
                (double) dwell.size(),
                dwellStats.mean(),
                dwellStats.stddev(),
                dwellStats.min(),
                dwellStats.max(),
                (double) flight.size(),
                flightStats.mean(),
                flightStats.stddev(),
                flightStats.min(),
                flightStats.max());
        return new TelemetryEvent.KeystrokeTelemetry(
                zScore(dwell, dwellStats),
                zScore(flight, flightStats),
                KEYSTROKE_FEATURE_NAMES,
                featureVector);
    }

    /**
     * Summarizes raw mouse movement samples into stable interaction-dynamics features.
     *
     * @param samples ordered mouse samples from the client
     * @return aggregate mouse telemetry
     */
    public TelemetryEvent.MouseTelemetry summarizeMouseDynamics(List<MouseMovementSample> samples) {
        List<MouseMovementSample> orderedSamples = samples == null ? List.of() : List.copyOf(samples);
        if (orderedSamples.size() < 2) {
            return new TelemetryEvent.MouseTelemetry(orderedSamples.size(), 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
        }

        double totalDistance = 0.0d;
        double maxSpeed = 0.0d;
        double idleDuration = 0.0d;
        int directionChanges = 0;
        double previousDx = 0.0d;
        double previousDy = 0.0d;
        MouseMovementSample first = orderedSamples.get(0);
        MouseMovementSample previous = first;

        for (int index = 1; index < orderedSamples.size(); index++) {
            MouseMovementSample current = orderedSamples.get(index);
            double deltaMillis = current.occurredAtMillis() - previous.occurredAtMillis();
            if (deltaMillis < 0.0d) {
                throw new IllegalArgumentException("mouse movement samples must be ordered by occurredAtMillis");
            }
            double dx = current.x() - previous.x();
            double dy = current.y() - previous.y();
            double distance = Math.hypot(dx, dy);
            totalDistance += distance;
            if (deltaMillis > 0.0d) {
                maxSpeed = Math.max(maxSpeed, distance / deltaMillis * 1000.0d);
            }
            if (deltaMillis >= IDLE_THRESHOLD_MILLIS) {
                idleDuration += deltaMillis;
            }
            if (index > 1 && changedDirection(previousDx, previousDy, dx, dy)) {
                directionChanges++;
            }
            previousDx = dx;
            previousDy = dy;
            previous = current;
        }

        double durationMillis = previous.occurredAtMillis() - first.occurredAtMillis();
        double averageSpeed = durationMillis <= 0.0d ? 0.0d : totalDistance / durationMillis * 1000.0d;
        double directionChangeRate = (double) directionChanges / Math.max(1, orderedSamples.size() - 2);
        double idleRatio = durationMillis <= 0.0d ? 0.0d : Math.min(1.0d, idleDuration / durationMillis);
        return new TelemetryEvent.MouseTelemetry(
                orderedSamples.size(), totalDistance, averageSpeed, maxSpeed, directionChangeRate, idleRatio);
    }

    private TelemetryEvent.TokenContext extractTokenContext(String sessionToken) {
        String[] parts = sessionToken.split("\\.");
        if (parts.length != 3) {
            return new TelemetryEvent.TokenContext("unknown", "unknown", "unknown", null, false);
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode claims = objectMapper.readTree(payload);
            return new TelemetryEvent.TokenContext(
                    textClaim(claims, "iss"),
                    textClaim(claims, "sub"),
                    audienceClaim(claims.get("aud")),
                    instantClaim(claims, "exp"),
                    true);
        } catch (Exception ex) {
            return new TelemetryEvent.TokenContext("unknown", "unknown", "unknown", null, true);
        }
    }

    private List<Double> immutableFiniteNonNegative(List<Double> values, String fieldName) {
        if (values == null) {
            return List.of();
        }
        List<Double> copy = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0.0d) {
                throw new IllegalArgumentException(fieldName + " must contain finite non-negative values");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private List<Double> zScore(List<Double> values, VectorStats stats) {
        if (values.isEmpty() || stats.stddev() == 0.0d) {
            return values.stream().map(ignored -> 0.0d).toList();
        }
        return values.stream()
                .map(value -> (value - stats.mean()) / stats.stddev())
                .toList();
    }

    private boolean changedDirection(double previousDx, double previousDy, double dx, double dy) {
        double previousMagnitude = Math.hypot(previousDx, previousDy);
        double currentMagnitude = Math.hypot(dx, dy);
        if (previousMagnitude == 0.0d || currentMagnitude == 0.0d) {
            return false;
        }
        double cosine = ((previousDx * dx) + (previousDy * dy)) / (previousMagnitude * currentMagnitude);
        return cosine < 0.0d;
    }

    private String textClaim(JsonNode claims, String claimName) {
        JsonNode node = claims.get(claimName);
        return node == null || !node.isTextual() || node.asText().isBlank() ? "unknown" : node.asText();
    }

    private String audienceClaim(JsonNode audienceNode) {
        if (audienceNode == null) {
            return "unknown";
        }
        if (audienceNode.isTextual()) {
            return audienceNode.asText();
        }
        if (audienceNode.isArray()) {
            List<String> audiences = new ArrayList<>();
            audienceNode.forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    audiences.add(node.asText());
                }
            });
            return audiences.isEmpty() ? "unknown" : String.join(",", audiences);
        }
        return "unknown";
    }

    private Instant instantClaim(JsonNode claims, String claimName) {
        JsonNode node = claims.get(claimName);
        return node == null || !node.canConvertToLong() ? null : Instant.ofEpochSecond(node.asLong());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "=".repeat(4 - remainder);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    /**
     * Incoming telemetry payload accepted by the ingestion engine.
     *
     * @param sessionToken OAuth 2.0 or OIDC session token. JWTs are parsed for non-sensitive metadata.
     * @param clientIp source IP address from the trusted ingress layer
     * @param tlsJa3Fingerprint TLS JA3 fingerprint
     * @param userAgent HTTP User-Agent header
     * @param dwellTimesMillis key dwell durations
     * @param flightTimesMillis inter-key transition durations
     * @param mouseMovements ordered mouse movement samples
     * @param observedAt event observation timestamp
     * @param attributes optional low-cardinality attributes such as tenant ID or Lambda request ID
     */
    public record TelemetryPayload(
            String sessionToken,
            String clientIp,
            String tlsJa3Fingerprint,
            String userAgent,
            List<Double> dwellTimesMillis,
            List<Double> flightTimesMillis,
            List<MouseMovementSample> mouseMovements,
            Instant observedAt,
            Map<String, String> attributes) {

        public TelemetryPayload {
            dwellTimesMillis = dwellTimesMillis == null ? List.of() : List.copyOf(dwellTimesMillis);
            flightTimesMillis = flightTimesMillis == null ? List.of() : List.copyOf(flightTimesMillis);
            mouseMovements = mouseMovements == null ? List.of() : List.copyOf(mouseMovements);
            attributes = immutableAttributes(attributes);
        }
    }

    /**
     * Raw mouse movement sample captured on the client.
     *
     * @param x horizontal pointer position in pixels
     * @param y vertical pointer position in pixels
     * @param occurredAtMillis monotonic client-side event timestamp in milliseconds
     * @param buttonDown whether a pointer button was pressed for this sample
     */
    public record MouseMovementSample(double x, double y, long occurredAtMillis, boolean buttonDown) {
        public MouseMovementSample {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("mouse coordinates must be finite");
            }
            if (occurredAtMillis < 0L) {
                throw new IllegalArgumentException("occurredAtMillis must be non-negative");
            }
        }
    }

    private record VectorStats(double mean, double stddev, double min, double max) {
        private static VectorStats from(List<Double> values) {
            if (values.isEmpty()) {
                return new VectorStats(0.0d, 0.0d, 0.0d, 0.0d);
            }
            double sum = 0.0d;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double value : values) {
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            double mean = sum / values.size();
            double variance = 0.0d;
            for (double value : values) {
                variance += Math.pow(value - mean, 2.0d);
            }
            return new VectorStats(mean, Math.sqrt(variance / values.size()), min, max);
        }
    }

    private static Map<String, String> immutableAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        attributes.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "attribute keys must not be null"),
                Objects.requireNonNull(value, "attribute values must not be null")));
        return Collections.unmodifiableMap(copy);
    }
}
