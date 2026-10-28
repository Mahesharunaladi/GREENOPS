package com.greenops.workflow.deception;

import com.greenops.model.AttackerActionLog;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Creates bounded, credential-redacted deception events for both MVC and WebFlux controllers. */
@Component
public final class SyntheticActionLogger {
    private final FingerprintRegistry registry;
    private final ClientAddressResolver addresses;

    public SyntheticActionLogger(FingerprintRegistry registry, ClientAddressResolver addresses) {
        this.registry = registry;
        this.addresses = addresses;
    }

    public void record(ServerWebExchange exchange, String payload) {
        ServerHttpRequest request = exchange.getRequest();
        var remote = request.getRemoteAddress();
        String ip = addresses.resolve(remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress(),
                request.getHeaders().getFirst("X-Forwarded-For"));
        String ja3 = valueOrUnknown(request.getHeaders().getFirst("X-TLS-JA3-Fingerprint"));
        String target = valueOrUnknown(request.getHeaders().getFirst("X-GreenOps-Original-Path"));
        if ("unknown".equals(target)) target = request.getURI().getRawPath();
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        request.getHeaders().toSingleValueMap().forEach((key, value) -> headers.put(key, List.of(value)));
        record(ip, ja3, target, request.getMethod() == null ? "UNKNOWN" : request.getMethod().name(), headers, payload);
    }

    public void record(jakarta.servlet.http.HttpServletRequest request, String payload) {
        String ip = addresses.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        String ja3 = valueOrUnknown(request.getHeader("X-TLS-JA3-Fingerprint"));
        String target = valueOrUnknown(request.getHeader("X-GreenOps-Original-Path"));
        if ("unknown".equals(target)) target = request.getRequestURI();
        Map<String, List<String>> headers = Collections.list(request.getHeaderNames()).stream()
                .collect(java.util.stream.Collectors.toMap(name -> name, name -> Collections.list(request.getHeaders(name))));
        record(ip, ja3, target, request.getMethod(), headers, payload);
    }

    private void record(String ip, String ja3, String target, String method, Map<String, ? extends List<String>> headers, String payload) {
        String hash = FingerprintRegistry.compositeHash(ip, ja3);
        String capture = AuditCapture.capture(headers, payload);
        registry.recordAction(new AttackerActionLog(UUID.randomUUID().toString(), hash, target, method, capture, Instant.now()));
    }

    private String valueOrUnknown(String value) { return value == null || value.isBlank() ? "unknown" : value.trim(); }
}
