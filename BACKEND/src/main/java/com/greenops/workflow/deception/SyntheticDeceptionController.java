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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** WebFlux-only synthetic controllers. No repository or AWS service is injected here. */
@RestController
@RequestMapping("/api/v1/sandbox")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SyntheticDeceptionController {
    private static final int MAX_CAPTURE_BYTES = 8192;
    private final SyntheticActionLogger actionLogger;

    public SyntheticDeceptionController(SyntheticActionLogger actionLogger) { this.actionLogger = actionLogger; }

    @PostMapping("/ec2/stop")
    public Mono<ResponseEntity<Map<String, String>>> fakeEc2Stop(ServerWebExchange exchange) {
        return captureBody(exchange).doOnNext(payload -> actionLogger.record(exchange, payload))
                .thenReturn(ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Instance i-0fake9912a stopped successfully")));
    }

    @GetMapping(value = "/s3/download-audit", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> fakeS3Download(ServerWebExchange exchange) {
        actionLogger.record(exchange, "");
        byte[] csv = "user_id,email,canary_token\n1001,canary_admin@fake-greenops.internal,CNRY-8819-XYZ\n".getBytes(StandardCharsets.UTF_8);
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
}
