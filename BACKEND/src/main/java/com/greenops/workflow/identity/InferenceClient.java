package com.greenops.workflow.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;

/**
 * AWS SDK v2 inference adapter for behavioral biometric anomaly scoring.
 *
 * <p>The adapter can invoke either AWS Bedrock Runtime or SageMaker Runtime based on the
 * {@code GREENOPS_INFERENCE_PROVIDER} environment variable. Supported values are {@code bedrock} and
 * {@code sagemaker}. Bedrock requires {@code GREENOPS_BEDROCK_MODEL_ID}; SageMaker requires
 * {@code GREENOPS_SAGEMAKER_ENDPOINT}. When neither is configured, the client returns a deterministic
 * local heuristic score so development environments can compile and exercise the workflow without
 * cloud credentials.
 *
 * <p><strong>Thread safety:</strong> AWS SDK v2 clients and Jackson's {@link ObjectMapper} are
 * thread-safe after construction. This component stores only immutable collaborator references and
 * is safe as a singleton Spring bean or warm AWS Lambda singleton.
 */
@Component
public final class InferenceClient {

    private static final String PROVIDER_BEDROCK = "bedrock";
    private static final String PROVIDER_SAGEMAKER = "sagemaker";

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final SageMakerRuntimeClient sageMakerRuntimeClient;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String bedrockModelId;
    private final String sageMakerEndpoint;

    @Autowired
    public InferenceClient(ObjectMapper objectMapper) {
        this(
                BedrockRuntimeClient.create(),
                SageMakerRuntimeClient.create(),
                objectMapper,
                getenv("GREENOPS_INFERENCE_PROVIDER"),
                getenv("GREENOPS_BEDROCK_MODEL_ID"),
                getenv("GREENOPS_SAGEMAKER_ENDPOINT"));
    }

    InferenceClient(
            BedrockRuntimeClient bedrockRuntimeClient,
            SageMakerRuntimeClient sageMakerRuntimeClient,
            ObjectMapper objectMapper,
            String provider,
            String bedrockModelId,
            String sageMakerEndpoint) {
        this.bedrockRuntimeClient = Objects.requireNonNull(bedrockRuntimeClient, "bedrockRuntimeClient must not be null");
        this.sageMakerRuntimeClient = Objects.requireNonNull(sageMakerRuntimeClient, "sageMakerRuntimeClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.provider = normalize(provider);
        this.bedrockModelId = normalizeIdentifier(bedrockModelId);
        this.sageMakerEndpoint = normalizeIdentifier(sageMakerEndpoint);
    }

    /**
     * Scores an identity verification request using configured AWS inference runtime.
     *
     * @param request normalized model input derived from telemetry and baseline features
     * @return model response with anomaly probability and model metadata
     */
    public InferenceResponse evaluateBehavioralRisk(InferenceRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            if (PROVIDER_BEDROCK.equals(provider) && !"unknown".equals(bedrockModelId)) {
                return invokeBedrock(request);
            }
            if (PROVIDER_SAGEMAKER.equals(provider) && !"unknown".equals(sageMakerEndpoint)) {
                return invokeSageMaker(request);
            }
            return localHeuristic(request);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize or parse inference payload", ex);
        }
    }

    private InferenceResponse invokeBedrock(InferenceRequest request) throws IOException {
        String payload = objectMapper.writeValueAsString(request);
        var response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                .modelId(bedrockModelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload))
                .build());
        return parseResponse(response.body().asUtf8String(), "bedrock:" + bedrockModelId);
    }

    private InferenceResponse invokeSageMaker(InferenceRequest request) throws IOException {
        String payload = objectMapper.writeValueAsString(request);
        var response = sageMakerRuntimeClient.invokeEndpoint(InvokeEndpointRequest.builder()
                .endpointName(sageMakerEndpoint)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload))
                .build());
        return parseResponse(response.body().asUtf8String(), "sagemaker:" + sageMakerEndpoint);
    }

    private InferenceResponse parseResponse(String payload, String modelReference) throws IOException {
        JsonNode root = objectMapper.readTree(payload);
        double score = firstNumeric(root, "anomalyScore", "anomaly_score", "probability", "score")
                .orElseThrow(() -> new IllegalStateException("Inference response did not include an anomaly score"));
        return new InferenceResponse(clamp(score), modelReference, "remote", Instant.now(), Map.of());
    }

    private InferenceResponse localHeuristic(InferenceRequest request) {
        double keystrokeScore = request.keystrokeFeatures().stream()
            .mapToDouble(value -> Math.abs(value.doubleValue()))
                .average()
                .orElse(0.0d);
        double mouseScore = Math.max(
                request.interactionFeatures().getOrDefault("directionChangeRate", 0.0d),
                request.interactionFeatures().getOrDefault("idleRatio", 0.0d));
        double contextScore = request.contextFeatures().values().stream()
                .mapToDouble(value -> "true".equalsIgnoreCase(value) ? 0.25d : 0.0d)
                .sum();
        double anomalyScore = clamp((Math.min(keystrokeScore, 4.0d) / 4.0d * 0.55d) + (mouseScore * 0.25d) + contextScore);
        return new InferenceResponse(
                anomalyScore,
                "local-behavioral-heuristic",
                "local",
                Instant.now(),
                Map.of("inferenceProvider", "local"));
    }

    private static java.util.Optional<Double> firstNumeric(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node != null && node.isNumber()) {
                return java.util.Optional.of(node.asDouble());
            }
        }
        return java.util.Optional.empty();
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static String getenv(String key) {
        return System.getenv(key);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "local" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIdentifier(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    /**
     * Model input assembled by {@link ZeroTrustAgent}.
     */
    public record InferenceRequest(
            String eventId,
            String sessionTokenHash,
            String subject,
            List<Double> keystrokeFeatures,
            Map<String, Double> interactionFeatures,
            Map<String, String> contextFeatures,
            Map<String, Double> baselineFeatures,
            Instant observedAt) {

        public InferenceRequest {
            Objects.requireNonNull(eventId, "eventId must not be null");
            Objects.requireNonNull(sessionTokenHash, "sessionTokenHash must not be null");
            subject = subject == null || subject.isBlank() ? "unknown" : subject;
            keystrokeFeatures = keystrokeFeatures == null ? List.of() : List.copyOf(keystrokeFeatures);
            interactionFeatures = interactionFeatures == null ? Map.of() : Map.copyOf(interactionFeatures);
            contextFeatures = contextFeatures == null ? Map.of() : Map.copyOf(contextFeatures);
            baselineFeatures = baselineFeatures == null ? Map.of() : Map.copyOf(baselineFeatures);
            observedAt = observedAt == null ? Instant.now() : observedAt;
        }
    }

    /**
     * Parsed inference output consumed by the agent policy.
     */
    public record InferenceResponse(
            double anomalyScore,
            String modelReference,
            String inferenceMode,
            Instant inferredAt,
            Map<String, String> attributes) {

        public InferenceResponse {
            anomalyScore = clamp(anomalyScore);
            modelReference = modelReference == null || modelReference.isBlank() ? "unknown" : modelReference;
            inferenceMode = inferenceMode == null || inferenceMode.isBlank() ? "unknown" : inferenceMode;
            inferredAt = inferredAt == null ? Instant.now() : inferredAt;
            attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        }
    }
}
