package com.greenops.workflow.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Service
public final class CaepEventTransmitter {
    private static final Logger log = LoggerFactory.getLogger(CaepEventTransmitter.class);
    private static final String SESSION_REVOKED = "https://schemas.openid.net/secevent/caep/event-type/session-revoked";
    private final ObjectMapper mapper;
    private final WebClient webClient;
    private final EventBridgeAsyncClient eventBridge;
    private final CloudWatchAsyncClient cloudWatch;
    private final String webhook;
    private final String eventBus;
    private final String issuer;
    private final String audience;
    private final byte[] signingSecret;

    public CaepEventTransmitter(ObjectMapper mapper, EventBridgeAsyncClient eventBridge, CloudWatchAsyncClient cloudWatch,
            @Value("${greenops.enterprise.caep.webhook:}") String webhook,
            @Value("${greenops.enterprise.event-bus:}") String eventBus,
            @Value("${greenops.enterprise.caep.issuer:greenops}") String issuer,
            @Value("${greenops.enterprise.caep.audience:greenops-idp}") String audience,
            @Value("${greenops.enterprise.caep.hmac-secret:}") String signingSecret) {
        this.mapper = mapper; this.webClient = WebClient.create(); this.eventBridge = eventBridge; this.cloudWatch = cloudWatch;
        this.webhook = webhook; this.eventBus = eventBus; this.issuer = issuer; this.audience = audience;
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    public Mono<Void> broadcastSecurityEventToken(String userId, String reason, String riskLevel) {
        if (userId == null || userId.isBlank() || userId.length() > 256) return Mono.error(new IllegalArgumentException("userId is required"));
        if (reason == null || reason.isBlank() || reason.length() > 512) return Mono.error(new IllegalArgumentException("reason is required"));
        if (riskLevel == null || !riskLevel.matches("(?i)low|medium|high|critical")) return Mono.error(new IllegalArgumentException("riskLevel must be LOW, MEDIUM, HIGH, or CRITICAL"));
        if (signingSecret.length < 32) return Mono.error(new IllegalStateException("CAEP HS256 signing secret must be configured with at least 32 bytes"));
        if (webhook.isBlank() && eventBus.isBlank()) return Mono.error(new IllegalStateException("Configure a CAEP receiver webhook or EventBridge bus"));
        String token = signedSet(userId, reason, riskLevel);
        Mono<Void> webhookSend = webhook.isBlank() ? Mono.empty() : webClient.post().uri(webhook)
                .header("Content-Type", "application/secevent+jwt").bodyValue(token).retrieve().toBodilessEntity().then();
        Mono<Void> eventSend = eventBus.isBlank() ? Mono.empty() : Mono.fromFuture(eventBridge.putEvents(PutEventsRequest.builder()
                .entries(PutEventsRequestEntry.builder().eventBusName(eventBus).source("greenops.enterprise")
                        .detailType("CAEP Security Event Token").detail(token).build()).build()))
                .flatMap(response -> response.failedEntryCount() == 0 ? Mono.empty()
                        : Mono.error(new IllegalStateException("EventBridge rejected the CAEP event")));
        return Mono.whenDelayError(webhookSend, eventSend)
                .then(emitBroadcastMetric())
                .doOnSuccess(ignored -> log.warn("CAEP session-revoked event broadcast: userId={}, riskLevel={}, jti={}", userId, riskLevel, tokenId(token)));
    }

    private String signedSet(String userId, String reason, String riskLevel) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "secevent+jwt");
        Map<String, Object> event = Map.of("subject", Map.of("sub_id", Map.of("format", "opaque", "id", userId)),
                "reason_admin", reason, "event_timestamp", now, "risk_level", riskLevel.toUpperCase(java.util.Locale.ROOT));
        Map<String, Object> claims = Map.of("iss", issuer, "iat", now, "jti", UUID.randomUUID().toString(),
                "aud", audience, "events", Map.of(SESSION_REVOKED, event));
        String signingInput = base64Json(header) + "." + base64Json(claims);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception error) { throw new IllegalStateException("Could not sign CAEP SET", error); }
    }

    private String base64Json(Object value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(value)); }
        catch (Exception error) { throw new IllegalStateException("Could not encode CAEP SET", error); }
    }
    private String tokenId(String token) { return token.split("\\.")[1].substring(0, Math.min(16, token.split("\\.")[1].length())); }
    private Mono<Void> emitBroadcastMetric() {
        return Mono.fromFuture(cloudWatch.putMetricData(PutMetricDataRequest.builder().namespace("GreenOps/Enterprise")
                .metricData(MetricDatum.builder().metricName("greenops.caep.broadcasts_sent").unit(StandardUnit.COUNT)
                        .value(1.0d).timestamp(Instant.now()).build()).build())).then()
                .onErrorResume(error -> { log.warn("Could not emit CAEP metric: {}", error.getClass().getSimpleName()); return Mono.empty(); });
    }
}
