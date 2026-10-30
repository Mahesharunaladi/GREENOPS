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
import com.greenops.workflow.enterprise.CanaryTokenService;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Synthetic-only controller used when the existing application runs on Spring MVC. */
@Controller
@RequestMapping("/api/v1/sandbox")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SyntheticDeceptionMvcController {
    private static final int MAX_CAPTURE_BYTES = 8192;
    private final SyntheticActionLogger actionLogger;
    private final FingerprintRegistry registry;
    private final CanaryTokenService canaryTokens;

    public SyntheticDeceptionMvcController(SyntheticActionLogger actionLogger, FingerprintRegistry registry, CanaryTokenService canaryTokens) {
        this.actionLogger = actionLogger;
        this.registry = registry;
        this.canaryTokens = canaryTokens;
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
        var token = canaryTokens.generateHoneyCredentials(sessionReference(request));
        String payload = canaryTokens.injectCanaryIntoPayload(
                "user_id,email\n1001,canary_admin@fake-greenops.internal\n", token);
        byte[] csv = payload.getBytes(StandardCharsets.UTF_8);
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

    private static String sessionReference(jakarta.servlet.http.HttpServletRequest request) {
        String value = request.getCookies() == null ? "missing-session" : java.util.Arrays.stream(request.getCookies())
                .filter(cookie -> "greenops_session".equals(cookie.getName())).map(cookie -> cookie.getValue())
                .findFirst().orElse("missing-session");
        try { return "session-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
}
