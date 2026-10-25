package com.greenops.innovation.deception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet equivalent of a WebFilter, matching this application's Spring MVC runtime. */
@Component
public final class HoneypotWebFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HoneypotWebFilter.class);
    private final RiskSignalResolver riskSignals;

    public HoneypotWebFilter(RiskSignalResolver riskSignals) { this.riskSignals = Objects.requireNonNull(riskSignals); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        double risk = riskSignals.score(request);
        if (risk < .60 || request.getRequestURI().startsWith("/__deception")) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Deception route selected requestId={} method={} path={} risk={}",
                request.getHeader("X-Request-Id"), request.getMethod(), request.getRequestURI(), risk);
        request.getRequestDispatcher("/__deception" + request.getRequestURI()).forward(request, response);
    }

    @FunctionalInterface public interface RiskSignalResolver { double score(HttpServletRequest request); }
}
