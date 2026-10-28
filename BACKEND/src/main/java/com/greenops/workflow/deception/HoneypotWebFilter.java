package com.greenops.workflow.deception;

import java.net.InetSocketAddress;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Routes requests with trusted server-side risk signals into a synthetic sandbox. */
@Component("adaptiveDeceptionWebFilter")
@Order(-1)
public final class HoneypotWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(HoneypotWebFilter.class);
    private static final String SANDBOX_PATH = "/api/v1/sandbox/synthetic-data";
    private final FingerprintRegistry registry;
    private final ClientAddressResolver addresses;

    public HoneypotWebFilter(FingerprintRegistry registry, ClientAddressResolver addresses) {
        this.registry = Objects.requireNonNull(registry);
        this.addresses = Objects.requireNonNull(addresses);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path.startsWith("/api/v1/sandbox")) return chain.filter(exchange);
        String ip = clientIp(exchange.getRequest());
        String ja3 = valueOrUnknown(exchange.getRequest().getHeaders().getFirst("X-TLS-JA3-Fingerprint"));
        String userAgent = valueOrUnknown(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        UserSessionContext context = exchange.getAttribute(UserSessionContext.REQUEST_ATTRIBUTE);
        return registry.isBlacklistedReactive(ip, ja3).onErrorReturn(false).flatMap(blacklisted -> {
            boolean suspicious = blacklisted || context != null && (context.blacklisted() || !context.spireVerified() || context.biometricScore() < 0.80d);
            if (!suspicious) return chain.filter(exchange);
            double score = context == null ? 0.0d : context.biometricScore();
            String spiffeId = context == null ? "unknown" : context.spiffeSvid();
            registry.registerThreat(ip, ja3, userAgent, spiffeId, score);
            log.warn("Request diverted to synthetic environment method={} path={} fingerprint={}", exchange.getRequest().getMethod(), path, FingerprintRegistry.compositeHash(ip, ja3));
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .path(SANDBOX_PATH)
                    .headers(headers -> {
                        headers.set("X-GreenOps-Deception-Mode", "ACTIVE");
                        headers.set("X-GreenOps-Original-Path", path);
                    }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    private String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return addresses.resolve(remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress(),
                request.getHeaders().getFirst("X-Forwarded-For"));
    }

    private String valueOrUnknown(String value) { return value == null || value.isBlank() ? "unknown" : value.trim(); }
}
