package com.greenops.workflow.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenops.model.CanaryToken;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Service
public final class CanaryTokenService {
    private static final Logger log = LoggerFactory.getLogger(CanaryTokenService.class);
    private static final int MAX_TOKENS = 50_000;
    private final Map<String, CanaryToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> tokenOrder = new ConcurrentLinkedQueue<>();
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper;
    private final EventBridgeAsyncClient eventBridge;
    private final WebClient webClient;
    private final String eventBus;
    private final String alertWebhook;
    private final String callbackBaseUrl;

    public CanaryTokenService(ObjectMapper mapper, EventBridgeAsyncClient eventBridge,
            @Value("${greenops.enterprise.event-bus:}") String eventBus,
            @Value("${greenops.enterprise.canary-alert-webhook:}") String alertWebhook,
            @Value("${greenops.enterprise.canary-callback-url:}") String callbackBaseUrl) {
        this.mapper = mapper; this.eventBridge = eventBridge; this.webClient = WebClient.create();
        this.eventBus = eventBus; this.alertWebhook = alertWebhook; this.callbackBaseUrl = callbackBaseUrl;
        if (!callbackBaseUrl.isBlank() && !callbackBaseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Canary callback base URL must use HTTPS");
        }
    }

    public CanaryToken generateHoneyCredentials(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 256) {
            throw new IllegalArgumentException("A valid session ID is required");
        }
        byte[] entropy = new byte[16]; random.nextBytes(entropy);
        String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy).toUpperCase(java.util.Locale.ROOT);
        String tokenId = java.util.UUID.randomUUID().toString();
        // Never use a valid AWS key-shaped credential: this is an inert canary marker only.
        CanaryToken token = new CanaryToken(tokenId, "AKIA-CANARY-" + suffix.substring(0, 12),
                sessionId, "SYNTHETIC_CLOUD_CREDENTIAL", Instant.now(),
                callbackBaseUrl.isBlank() ? "" : callbackBaseUrl.replaceAll("/+$", "") + "/" + tokenId);
        tokens.put(tokenId, token);
        tokenOrder.add(tokenId);
        while (tokens.size() > MAX_TOKENS) {
            String oldest = tokenOrder.poll();
            if (oldest == null) break;
            tokens.remove(oldest);
        }
        return token;
    }

    public String injectCanaryIntoPayload(String payload, CanaryToken token) {
        if (payload == null || token == null) throw new IllegalArgumentException("Payload and canary token are required");
        try {
            if (payload.stripLeading().startsWith("{") || payload.stripLeading().startsWith("[")) {
                var root = mapper.readTree(payload);
                if (root instanceof ObjectNode object) {
                    object.put("access_key_id", token.fakeAccessKey());
                    object.put("canary_token_id", token.tokenId());
                    object.put("canary_callback_url", token.callbackUrl());
                    return mapper.writeValueAsString(object);
                }
                ObjectNode wrapper = mapper.createObjectNode();
                wrapper.set("data", root);
                wrapper.put("access_key_id", token.fakeAccessKey());
                wrapper.put("canary_token_id", token.tokenId());
                wrapper.put("canary_callback_url", token.callbackUrl());
                return mapper.writeValueAsString(wrapper);
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("Payload is not valid JSON", error);
        }
        String[] rows = payload.split("\\r?\\n", -1);
        if (rows.length == 0 || rows[0].isBlank()) throw new IllegalArgumentException("CSV payload must include a header row");
        rows[0] += ",canary_access_key,canary_token_id,canary_callback_url";
        if (rows.length == 1) {
            return rows[0] + "\n," + token.fakeAccessKey() + "," + token.tokenId() + "," + token.callbackUrl();
        }
        int lastRow = rows.length - 1;
        if (lastRow > 0 && rows[lastRow].isEmpty()) lastRow--;
        if (lastRow == 0) rows[1] = "," + token.fakeAccessKey() + "," + token.tokenId() + "," + token.callbackUrl();
        else rows[lastRow] += "," + token.fakeAccessKey() + "," + token.tokenId() + "," + token.callbackUrl();
        return String.join("\n", rows);
    }

    public Mono<Void> handleCanaryTrigger(String tokenId, String sourceIp) {
        CanaryToken token = tokens.get(tokenId);
        if (token == null) return Mono.error(new IllegalArgumentException("Unknown canary token"));
        if (!isPublicAddress(sourceIp)) return Mono.error(new IllegalArgumentException("Canary callback source must be a public IP address"));
        Map<String, Object> alert = Map.of("type", "CANARY_CREDENTIAL_USED", "severity", "HIGH",
                "tokenId", tokenId, "sessionId", token.associatedSessionId(), "sourceIp", sourceIp, "observedAt", Instant.now().toString());
        Mono<Void> bus = eventBus.isBlank() ? Mono.empty() : Mono.fromFuture(eventBridge.putEvents(PutEventsRequest.builder()
                .entries(PutEventsRequestEntry.builder().eventBusName(eventBus).source("greenops.enterprise")
                        .detailType("Canary credential triggered").detail(writeJson(alert)).build()).build()))
                .flatMap(response -> response.failedEntryCount() == 0 ? Mono.empty()
                        : Mono.error(new IllegalStateException("EventBridge rejected canary alert")));
        Mono<Void> webhook = alertWebhook.isBlank() ? Mono.empty() : webClient.post().uri(alertWebhook)
                .bodyValue(alert).retrieve().toBodilessEntity().then();
        if (eventBus.isBlank() && alertWebhook.isBlank()) return Mono.error(new IllegalStateException("No canary alert sink is configured"));
        return Mono.whenDelayError(bus, webhook).doOnSuccess(ignored -> log.error("Canary credential triggered: tokenId={}, sourceIp={}", tokenId, sourceIp));
    }

    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Could not encode canary alert", error); }
    }
    private static boolean isPublicAddress(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress address = InetAddress.getByName(value);
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress());
        } catch (Exception error) { return false; }
    }
}
