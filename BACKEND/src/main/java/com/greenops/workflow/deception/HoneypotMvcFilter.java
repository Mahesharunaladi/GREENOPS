package com.greenops.workflow.deception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet-runtime bridge so the same router works while this application runs Spring MVC. */
@Component
@Order(-1)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class HoneypotMvcFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HoneypotMvcFilter.class);
    private static final String SANDBOX_PATH = "/api/v1/sandbox/synthetic-data";
    private final FingerprintRegistry registry;
    private final ClientAddressResolver addresses;

    public HoneypotMvcFilter(FingerprintRegistry registry, ClientAddressResolver addresses) {
        this.registry = registry;
        this.addresses = addresses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/sandbox");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = addresses.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        String ja3 = valueOrUnknown(request.getHeader("X-TLS-JA3-Fingerprint"));
        String userAgent = valueOrUnknown(request.getHeader("User-Agent"));
        Object candidate = request.getAttribute(UserSessionContext.REQUEST_ATTRIBUTE);
        UserSessionContext context = candidate instanceof UserSessionContext trusted ? trusted : null;
        boolean suspicious = registry.isBlacklisted(ip, ja3)
                || context != null && (context.blacklisted() || !context.spireVerified() || context.biometricScore() < 0.80d);
        if (!suspicious) {
            chain.doFilter(request, response);
            return;
        }
        registry.registerThreat(ip, ja3, userAgent, context == null ? "unknown" : context.spiffeSvid(), context == null ? 0.0d : context.biometricScore());
        String originalPath = request.getRequestURI();
        log.warn("Request diverted to synthetic environment method={} path={} fingerprint={}", request.getMethod(), originalPath, FingerprintRegistry.compositeHash(ip, ja3));
        HttpServletRequestWrapper wrapped = new DeceptionRequestWrapper(request, originalPath);
        RequestDispatcher dispatcher = request.getRequestDispatcher(SANDBOX_PATH);
        dispatcher.forward(wrapped, response);
    }

    private String valueOrUnknown(String value) { return value == null || value.isBlank() ? "unknown" : value.trim(); }

    private static final class DeceptionRequestWrapper extends HttpServletRequestWrapper {
        private final String originalPath;
        DeceptionRequestWrapper(HttpServletRequest request, String originalPath) { super(request); this.originalPath = originalPath; }
        @Override public String getHeader(String name) {
            if ("X-GreenOps-Deception-Mode".equalsIgnoreCase(name)) return "ACTIVE";
            if ("X-GreenOps-Original-Path".equalsIgnoreCase(name)) return originalPath;
            return super.getHeader(name);
        }
        @Override public Enumeration<String> getHeaders(String name) {
            if ("X-GreenOps-Deception-Mode".equalsIgnoreCase(name)) return Collections.enumeration(java.util.List.of("ACTIVE"));
            if ("X-GreenOps-Original-Path".equalsIgnoreCase(name)) return Collections.enumeration(java.util.List.of(originalPath));
            return super.getHeaders(name);
        }
        @Override public Enumeration<String> getHeaderNames() {
            java.util.ArrayList<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(name -> name.equalsIgnoreCase("X-GreenOps-Deception-Mode") || name.equalsIgnoreCase("X-GreenOps-Original-Path"));
            names.add("X-GreenOps-Deception-Mode");
            names.add("X-GreenOps-Original-Path");
            return Collections.enumeration(names);
        }
    }
}
