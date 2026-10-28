package com.greenops.workflow.deception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

/** Synthetic-only controller used when the existing application runs on Spring MVC. */
@Controller
@RequestMapping("/api/v1/sandbox")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SyntheticDeceptionMvcController {
    private static final int MAX_CAPTURE_BYTES = 8192;
    private final SyntheticActionLogger actionLogger;
    private final FingerprintRegistry registry;

    public SyntheticDeceptionMvcController(SyntheticActionLogger actionLogger, FingerprintRegistry registry) {
        this.actionLogger = actionLogger;
        this.registry = registry;
    }

    @GetMapping("/events")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<DeceptionEventView> recentEvents(@RequestParam(defaultValue = "100") int limit) {
        return registry.recentActions(Math.max(0, Math.min(200, limit))).stream().map(action -> {
            var fingerprint = registry.fingerprint(action.compositeHash());
            return new DeceptionEventView(action.logId(), action.compositeHash(),
                    fingerprint.map(value -> value.ipAddress()).orElse("unavailable"), action.attemptedEndpoint(),
                    action.httpMethod(), action.payloadCaptured(), action.timestamp(),
                    fingerprint.map(value -> value.violationCount()).orElse(0));
        }).toList();
    }

    @PostMapping("/ec2/stop")
    @org.springframework.web.bind.annotation.ResponseBody
    public Mono<ResponseEntity<Map<String, String>>> fakeEc2Stop(jakarta.servlet.http.HttpServletRequest request) {
        actionLogger.record(request, readBoundedPayload(request));
        return Mono.just(ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Instance i-0fake9912a stopped successfully")));
    }

    @GetMapping(value = "/s3/download-audit", produces = "text/csv")
    @org.springframework.web.bind.annotation.ResponseBody
    public Mono<ResponseEntity<byte[]>> fakeS3Download(jakarta.servlet.http.HttpServletRequest request) {
        actionLogger.record(request, "");
        byte[] csv = "user_id,email,canary_token\n1001,canary_admin@fake-greenops.internal,CNRY-8819-XYZ\n".getBytes(StandardCharsets.UTF_8);
        return Mono.just(ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=download-audit.csv")
                .contentType(new MediaType("text", "csv")).body(csv));
    }

    @RequestMapping(value = {"/synthetic-data", "/**"})
    @org.springframework.web.bind.annotation.ResponseBody
    public Mono<ResponseEntity<Map<String, Object>>> syntheticFallback(jakarta.servlet.http.HttpServletRequest request) {
        actionLogger.record(request, readBoundedPayload(request));
        return Mono.just(ResponseEntity.ok(Map.of("status", "SUCCESS", "data",
                Map.of("workspace", "greenops-demo", "resources", 3, "environment", "sandbox"))));
    }

    private String readBoundedPayload(jakarta.servlet.http.HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_CAPTURE_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "[payload unavailable]";
        }
    }
}
