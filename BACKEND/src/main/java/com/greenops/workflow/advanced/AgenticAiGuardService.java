package com.greenops.workflow.advanced;

import com.greenops.model.AgenticPromptPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/** Local deterministic prompt guard. It denies detected injection but does not execute tools. */
@Service
public final class AgenticAiGuardService {
    private static final Logger log = LoggerFactory.getLogger(AgenticAiGuardService.class);
    private static final List<Signature> SIGNATURES = List.of(
            new Signature("instruction_override", Pattern.compile("(?is)(ignore|disregard|override|forget)\\s+(all\\s+)?(previous|prior|system|developer)\\s+(instructions|messages|rules)")),
            new Signature("credential_or_data_exfiltration", Pattern.compile("(?is)(send|upload|exfiltrat|forward|email|post)\\w*.{0,100}(secret|credential|access.?key|s3|bucket|database|customer data)")),
            new Signature("destructive_database_operation", Pattern.compile("(?is)\\b(drop|truncate|delete)\\s+(table|database|schema)\\b")),
            new Signature("security_control_bypass", Pattern.compile("(?is)(disable|bypass|turn off|remove)\\w*.{0,80}(guardrail|audit|logging|security|approval|policy)")),
            new Signature("untrusted_tool_dispatch", Pattern.compile("(?is)(call|invoke|execute)\\s+(shell|terminal|aws cli|arbitrary tool|unapproved tool)")));

    private final DynamoDbAsyncClient dynamo;
    private final CloudWatchAsyncClient cloudWatch;
    private final String threatTable;

    public AgenticAiGuardService(DynamoDbAsyncClient dynamo, CloudWatchAsyncClient cloudWatch,
            @Value("${greenops.advanced.threat-table:}") String threatTable) {
        this.dynamo = dynamo; this.cloudWatch = cloudWatch; this.threatTable = threatTable;
    }

    /** Returns true only when this scanner found no known injection pattern. */
    public Mono<Boolean> inspectAgentPrompt(AgenticPromptPayload payload) {
        if (payload == null) return Mono.error(new IllegalArgumentException("Prompt payload is required"));
        if (blank(payload.promptId()) || blank(payload.agentId()) || blank(payload.userPrompt())) {
            return Mono.error(new IllegalArgumentException("promptId, agentId, and userPrompt are required"));
        }
        String content = payload.userPrompt() + "\n" + safe(payload.targetToolCall());
        List<String> findings = new ArrayList<>();
        for (Signature signature : SIGNATURES) {
            if (signature.pattern().matcher(content).find()) findings.add(signature.id());
        }
        if (findings.isEmpty()) return Mono.just(true);

        String eventId = UUID.randomUUID().toString();
        String digest = sha256(content);
        log.error("Agentic prompt injection blocked: eventId={}, promptId={}, agentId={}, signatures={}, indirectFlagUntrusted={}",
                eventId, payload.promptId(), payload.agentId(), findings, payload.containsIndirectInjection());
        Mono<Void> persist = persistThreat(eventId, payload, findings, digest)
                .onErrorResume(error -> {
                    log.error("Prompt threat persistence failed: eventId={}, errorType={}", eventId, error.getClass().getSimpleName());
                    return Mono.empty();
                });
        Mono<Void> metric = Mono.fromFuture(cloudWatch.putMetricData(PutMetricDataRequest.builder()
                .namespace("GreenOps/Advanced").metricData(MetricDatum.builder().metricName("greenops.advanced.prompt_injection.blocked")
                        .unit(StandardUnit.COUNT).value(1.0d).timestamp(Instant.now()).build()).build()))
                .then().onErrorResume(error -> Mono.empty());
        return Mono.whenDelayError(persist, metric).thenReturn(false);
    }

    private Mono<Void> persistThreat(String eventId, AgenticPromptPayload payload, List<String> findings, String digest) {
        if (threatTable.isBlank()) {
            log.error("Advanced threat table is not configured; blocked prompt eventId={} was only written to application logs", eventId);
            return Mono.empty();
        }
        Map<String, AttributeValue> item = Map.of(
                "eventId", s(eventId), "severity", s("HIGH"), "eventType", s("PROMPT_INJECTION_BLOCKED"),
                "promptId", s(bound(payload.promptId(), 256)), "agentId", s(bound(payload.agentId(), 256)),
                "promptSha256", s(digest), "targetToolCall", s(bound(safe(payload.targetToolCall()), 2048)),
                "signatures", AttributeValue.builder().ss(findings).build(), "detectedAt", s(Instant.now().toString()));
        CompletableFuture<?> put = dynamo.putItem(PutItemRequest.builder().tableName(threatTable).item(item).build());
        return Mono.fromFuture(put).then();
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String bound(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
    private record Signature(String id, Pattern pattern) {}
}
