package com.greenops.workflow.deception;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.greenops.workflow.enterprise.CanaryTokenService;
import java.security.MessageDigest;
import java.util.HexFormat;

/** WebFlux-only synthetic controllers. No repository or AWS service is injected here. */
@RestController
@RequestMapping("/api/v1/sandbox")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SyntheticDeceptionController {
    private static final int MAX_CAPTURE_BYTES = 8192;
    private final SyntheticActionLogger actionLogger;
    private final FingerprintRegistry registry;
    private final CanaryTokenService canaryTokens;

    public SyntheticDeceptionController(SyntheticActionLogger actionLogger, FingerprintRegistry registry, CanaryTokenService canaryTokens) {
        this.actionLogger = actionLogger;
        this.registry = registry;
        this.canaryTokens = canaryTokens;
    }

    @GetMapping("/events")
    public Mono<java.util.List<DeceptionEventView>> recentEvents(@RequestParam(defaultValue = "100") int limit) {
        var recent = registry.recentActions(Math.max(0, Math.min(200, limit))).stream().map(action -> {
            var fingerprint = registry.fingerprint(action.compositeHash());
            return new DeceptionEventView(action.logId(), action.compositeHash(),
                    fingerprint.map(value -> value.ipAddress()).orElse("unavailable"), action.attemptedEndpoint(),
                    action.httpMethod(), action.payloadCaptured(), action.timestamp(),
                    fingerprint.map(value -> value.violationCount()).orElse(0));
        }).toList();
        return Mono.just(recent);
    }

    @PostMapping("/ec2/stop")
    public Mono<ResponseEntity<Map<String, String>>> fakeEc2Stop(ServerWebExchange exchange) {
        return captureBody(exchange).doOnNext(payload -> actionLogger.record(exchange, payload))
                .thenReturn(ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Instance i-0fake9912a stopped successfully")));
    }

    @GetMapping(value = "/s3/download-audit", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> fakeS3Download(ServerWebExchange exchange) {
        actionLogger.record(exchange, "");
        var token = canaryTokens.generateHoneyCredentials(sessionReference(exchange.getRequest().getHeaders().getFirst("Cookie")));
        String payload = canaryTokens.injectCanaryIntoPayload(
                "user_id,email\n1001,canary_admin@fake-greenops.internal\n", token);
        byte[] csv = payload.getBytes(StandardCharsets.UTF_8);
        return Mono.just(ResponseEntity.ok().contentType(new MediaType("text", "csv")).body(csv));
    }

    @RequestMapping(value = {"/synthetic-data", "/**"})
    public Mono<ResponseEntity<Map<String, Object>>> syntheticFallback(ServerWebExchange exchange) {
        return captureBody(exchange).doOnNext(payload -> actionLogger.record(exchange, payload))
                .then(Mono.fromSupplier(() -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "SUCCESS", "data", Map.of("workspace", "greenops-demo", "resources", 3, "environment", "sandbox")))));
    }

    private Mono<String> captureBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_CAPTURE_BYTES)
                .map(this::decodeAndRelease)
                .defaultIfEmpty("")
                .onErrorResume(org.springframework.core.io.buffer.DataBufferLimitException.class, error -> Mono.just("[payload truncated at limit]"));
    }

    private String decodeAndRelease(DataBuffer buffer) {
        try {
            int count = Math.min(buffer.readableByteCount(), MAX_CAPTURE_BYTES);
            byte[] bytes = new byte[count];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static String sessionReference(String cookieHeader) {
        String value = cookieHeader == null ? "missing-session" : java.util.Arrays.stream(cookieHeader.split(";"))
                .map(cookie -> cookie.trim()).filter(cookie -> cookie.startsWith("greenops_session="))
                .map(cookie -> cookie.substring("greenops_session=".length())).findFirst().orElse("missing-session");
        try { return "session-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
}
