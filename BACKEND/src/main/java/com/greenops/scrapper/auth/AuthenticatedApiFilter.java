package com.greenops.scrapper.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a verified browser session for GreenOps API endpoints. */
@Component
public final class AuthenticatedApiFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "greenops_session";
    private final EmailAuthService authService;

    public AuthenticatedApiFilter(EmailAuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/auth/")
                || "/api/v1/operations/events".equals(path) && "POST".equalsIgnoreCase(request.getMethod())
                || path.matches("/api/v1/enterprise/canary/trigger/[0-9a-fA-F-]{36}");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String sessionToken = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    sessionToken = cookie.getValue();
                    break;
                }
            }
        }

        if (!authService.hasValidSession(sessionToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A verified GreenOps session is required.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
