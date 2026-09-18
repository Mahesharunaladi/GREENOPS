package com.greenops.workflow.identity;

import com.greenops.workflow.identity.InferenceClient.InferenceRequest;
import com.greenops.workflow.identity.InferenceClient.InferenceResponse;
import com.greenops.workflow.identity.RiskEvaluationResult.AgentDecision;
import com.greenops.workflow.telemetry.TelemetryEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;

/**
 * Autonomous identity agent for continuous Zero Trust verification.
 *
 * <p>The agent evaluates behavioral biometric telemetry against baseline model features through
 * {@link InferenceClient}, then selects an enforcement action without requiring a human operator in
 * the loop. The decision policy mirrors the Zero Trust flow shown in the visual reference: confirm
 * identity, verify device posture, and enforce least-privilege access continuously.
 *
 * <p>Decision policy:
 *
 * <ul>
 *   <li>{@code anomalyScore > 0.85}: revoke the session immediately.
 *   <li>{@code 0.60 <= anomalyScore <= 0.85}: trigger step-up authentication such as MFA.
 *   <li>{@code anomalyScore < 0.60}: re-verify silently and keep the user uninterrupted.
 * </ul>
 *
 * <p><strong>Thread safety:</strong> this service is stateless. It uses immutable collaborators,
 * method-local maps, and immutable result records, so it is safe as a singleton Spring bean and as a
 * reused AWS Lambda execution-environment object.
 */
@Service
public final class ZeroTrustAgent {

    private static final double REVOKE_THRESHOLD = 0.85d;
    private static final double STEP_UP_THRESHOLD = 0.60d;

    private final InferenceClient inferenceClient;

    public ZeroTrustAgent(InferenceClient inferenceClient) {
        this.inferenceClient = Objects.requireNonNull(inferenceClient, "inferenceClient must not be null");
    }

    /**
     * Performs continuous identity verification for a telemetry event.
     *
     * @param event normalized telemetry from the ingestion workflow
     * @return actionable risk evaluation result for policy enforcement
     */
    public RiskEvaluationResult evaluateIdentityRisk(TelemetryEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        InferenceRequest request = buildInferenceRequest(event);
        InferenceResponse response = inferenceClient.evaluateBehavioralRisk(request);
        AgentDecision decision = decisionFor(response.anomalyScore());

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("identityVerification", identityVerificationState(response.anomalyScore()));
        metadata.put("deviceVerification", deviceVerificationState(event));
        metadata.put("leastPrivilegeAccess", leastPrivilegeState(decision));
        metadata.put("modelReference", response.modelReference());
        metadata.put("inferenceMode", response.inferenceMode());
        metadata.put("subject", event.tokenContext().subject());
        metadata.put("issuer", event.tokenContext().issuer());
        metadata.put("browserFamily", event.deviceContext().browserFamily());
        metadata.put("operatingSystem", event.deviceContext().operatingSystem());
        metadata.put("clientIpScope", event.deviceContext().ipScope());
        metadata.put("keystrokeDwellDeviation", format(featureDeviation(event, "dwell_mean_ms")));
        metadata.put("keystrokeFlightDeviation", format(featureDeviation(event, "flight_mean_ms")));
        metadata.put("interactionPatternScore", format(interactionPatternScore(event)));
        response.attributes().forEach((key, value) -> metadata.put("inference." + key, value));

        return new RiskEvaluationResult(
                event.eventId(),
                event.sessionTokenHash(),
                response.anomalyScore(),
                decision,
                decision.defaultAction(),
                metadata,
                Instant.now());
    }

    private InferenceRequest buildInferenceRequest(TelemetryEvent event) {
        return new InferenceRequest(
                event.eventId(),
                event.sessionTokenHash(),
                event.tokenContext().subject(),
                event.keystrokeTelemetry().featureVector(),
                interactionFeatures(event),
                contextFeatures(event),
                baselineFeatures(event),
                event.observedAt());
    }

    private AgentDecision decisionFor(double anomalyScore) {
        if (anomalyScore > REVOKE_THRESHOLD) {
            return AgentDecision.REVOKE_SESSION;
        }
        if (anomalyScore >= STEP_UP_THRESHOLD) {
            return AgentDecision.STEP_UP_AUTHENTICATION;
        }
        return AgentDecision.SEAMLESS_REVERIFY;
    }

    private Map<String, Double> interactionFeatures(TelemetryEvent event) {
        TelemetryEvent.MouseTelemetry mouse = event.mouseTelemetry();
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("mouseSampleCount", (double) mouse.sampleCount());
        features.put("totalDistancePixels", mouse.totalDistancePixels());
        features.put("averageSpeedPixelsPerSecond", mouse.averageSpeedPixelsPerSecond());
        features.put("maxSpeedPixelsPerSecond", mouse.maxSpeedPixelsPerSecond());
        features.put("directionChangeRate", mouse.directionChangeRate());
        features.put("idleRatio", mouse.idleRatio());
        features.put("interactionPatternScore", interactionPatternScore(event));
        return features;
    }

    private Map<String, String> contextFeatures(TelemetryEvent event) {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("jwtFormat", Boolean.toString(event.tokenContext().jwtFormat()));
        features.put("tokenExpired", Boolean.toString(event.tokenContext().expiresAt() != null
                && event.tokenContext().expiresAt().isBefore(Instant.now())));
        features.put("automationSuspected", Boolean.toString(event.deviceContext().automationSuspected()));
        features.put("ipScope", event.deviceContext().ipScope());
        features.put("deviceType", event.deviceContext().deviceType());
        features.put("browserFamily", event.deviceContext().browserFamily());
        features.put("operatingSystem", event.deviceContext().operatingSystem());
        return features;
    }

    private Map<String, Double> baselineFeatures(TelemetryEvent event) {
        Map<String, Double> baseline = new LinkedHashMap<>();
        putBaseline(event, baseline, "baseline.dwell_mean_ms");
        putBaseline(event, baseline, "baseline.dwell_stddev_ms");
        putBaseline(event, baseline, "baseline.flight_mean_ms");
        putBaseline(event, baseline, "baseline.flight_stddev_ms");
        putBaseline(event, baseline, "baseline.interactionPatternScore");
        return baseline;
    }

    private void putBaseline(TelemetryEvent event, Map<String, Double> baseline, String key) {
        parseDouble(event.attributes().get(key)).ifPresent(value -> baseline.put(key, value));
    }

    private double featureDeviation(TelemetryEvent event, String featureName) {
        double observed = featureValue(event, featureName).orElse(0.0d);
        double baseline = parseDouble(event.attributes().get("baseline." + featureName)).orElse(observed);
        if (baseline == 0.0d) {
            return observed == 0.0d ? 0.0d : 1.0d;
        }
        return Math.abs(observed - baseline) / Math.abs(baseline);
    }

    private OptionalDouble featureValue(TelemetryEvent event, String featureName) {
        var featureNames = event.keystrokeTelemetry().featureNames();
        var featureVector = event.keystrokeTelemetry().featureVector();
        for (int index = 0; index < featureNames.size() && index < featureVector.size(); index++) {
            if (featureName.equals(featureNames.get(index))) {
                return OptionalDouble.of(featureVector.get(index));
            }
        }
        return OptionalDouble.empty();
    }

    private double interactionPatternScore(TelemetryEvent event) {
        TelemetryEvent.MouseTelemetry mouse = event.mouseTelemetry();
        double speedScore = Math.min(1.0d, mouse.maxSpeedPixelsPerSecond() / 6000.0d);
        double directionScore = mouse.directionChangeRate();
        double idleScore = mouse.idleRatio();
        return clamp((speedScore * 0.35d) + (directionScore * 0.35d) + (idleScore * 0.30d));
    }

    private String identityVerificationState(double anomalyScore) {
        if (anomalyScore > REVOKE_THRESHOLD) {
            return "identity_compromised";
        }
        if (anomalyScore >= STEP_UP_THRESHOLD) {
            return "identity_uncertain";
        }
        return "identity_continuously_verified";
    }

    private String deviceVerificationState(TelemetryEvent event) {
        if (event.deviceContext().automationSuspected()) {
            return "automation_suspected";
        }
        if ("unknown".equals(event.deviceContext().tlsJa3Fingerprint())) {
            return "tls_fingerprint_missing";
        }
        return "device_context_verified";
    }

    private String leastPrivilegeState(AgentDecision decision) {
        return switch (decision) {
            case REVOKE_SESSION -> "deny_all_revoke_session";
            case STEP_UP_AUTHENTICATION -> "limit_access_until_mfa";
            case SEAMLESS_REVERIFY -> "maintain_current_access";
        };
    }

    private OptionalDouble parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
