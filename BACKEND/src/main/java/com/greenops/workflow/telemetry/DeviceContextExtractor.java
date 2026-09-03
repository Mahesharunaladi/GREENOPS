package com.greenops.workflow.telemetry;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extracts coarse, privacy-aware device context from request metadata.
 *
 * <p>The extractor is intentionally deterministic and stateless. It does not perform DNS lookups,
 * persist client identifiers, or parse user-agent strings into high-cardinality fingerprints. The
 * resulting context is suitable for Zero Trust risk scoring where IP drift, TLS JA3 continuity, and
 * broad client traits are more useful than storing raw device fingerprints.
 *
 * <p><strong>Thread safety:</strong> this component is immutable and contains only compiled regular
 * expressions. It is safe to register as a singleton Spring bean and invoke concurrently from AWS
 * Lambda or servlet request threads.
 */
@Component
public final class DeviceContextExtractor {

    private static final Pattern JA3_PATTERN = Pattern.compile("^[a-fA-F0-9]{32}$");
    private static final Pattern MOBILE_PATTERN =
            Pattern.compile(".*(android|iphone|ipad|mobile|windows phone).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOT_PATTERN =
            Pattern.compile(".*(bot|crawler|spider|headless|phantom|selenium).*", Pattern.CASE_INSENSITIVE);

    /**
     * Builds a strongly typed device context from request metadata.
     *
     * @param clientIp source IP address observed by the trusted edge or API gateway
     * @param tlsJa3Fingerprint JA3 TLS client fingerprint as a 32-character MD5 hex digest
     * @param userAgent HTTP User-Agent header
     * @return immutable device context for downstream identity verification and risk scoring
     */
    public TelemetryEvent.DeviceContext extract(String clientIp, String tlsJa3Fingerprint, String userAgent) {
        String normalizedIp = normalizeRequired(clientIp, "clientIp");
        String normalizedJa3 = normalizeJa3(tlsJa3Fingerprint);
        String normalizedUserAgent = normalizeOptional(userAgent);
        return new TelemetryEvent.DeviceContext(
                normalizedIp,
                ipVersion(normalizedIp),
                ipScope(normalizedIp),
                normalizedJa3,
                deviceType(normalizedUserAgent),
                operatingSystem(normalizedUserAgent),
                browserFamily(normalizedUserAgent),
                BOT_PATTERN.matcher(normalizedUserAgent).matches());
    }

    /**
     * Normalizes a client IP for stable comparison during IP drift detection.
     *
     * @param clientIp raw client IP
     * @return trimmed textual address
     */
    public String normalizeClientIp(String clientIp) {
        return normalizeRequired(clientIp, "clientIp");
    }

    /**
     * Validates and normalizes a JA3 fingerprint.
     *
     * @param tlsJa3Fingerprint raw JA3 fingerprint
     * @return lowercase JA3 fingerprint, or {@code unknown} when absent
     */
    public String normalizeJa3(String tlsJa3Fingerprint) {
        String fingerprint = normalizeOptional(tlsJa3Fingerprint).toLowerCase(Locale.ROOT);
        if (fingerprint.isBlank()) {
            return "unknown";
        }
        if (!JA3_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("tlsJa3Fingerprint must be a 32-character hexadecimal JA3 digest");
        }
        return fingerprint;
    }

    /**
     * Extracts a coarse browser family from a User-Agent header.
     *
     * @param userAgent HTTP User-Agent header
     * @return browser family suitable for low-cardinality risk features
     */
    public String browserFamily(String userAgent) {
        String value = normalizeOptional(userAgent).toLowerCase(Locale.ROOT);
        if (value.contains("edg/")) {
            return "edge";
        }
        if (value.contains("chrome/") || value.contains("crios/")) {
            return "chrome";
        }
        if (value.contains("firefox/") || value.contains("fxios/")) {
            return "firefox";
        }
        if (value.contains("safari/")) {
            return "safari";
        }
        if (value.contains("curl/")) {
            return "curl";
        }
        return value.isBlank() ? "unknown" : "other";
    }

    private String ipVersion(String clientIp) {
        return clientIp.contains(":") ? "ipv6" : "ipv4";
    }

    private String ipScope(String clientIp) {
        try {
            InetAddress address = InetAddress.getByName(clientIp);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
                return "local";
            }
            if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return "private";
            }
            return "public";
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("clientIp must be a valid IP address", ex);
        }
    }

    private String deviceType(String userAgent) {
        if (userAgent.isBlank()) {
            return "unknown";
        }
        if (MOBILE_PATTERN.matcher(userAgent).matches()) {
            return "mobile";
        }
        return "desktop";
    }

    private String operatingSystem(String userAgent) {
        String value = userAgent.toLowerCase(Locale.ROOT);
        if (value.contains("windows")) {
            return "windows";
        }
        if (value.contains("mac os") || value.contains("macintosh")) {
            return "macos";
        }
        if (value.contains("android")) {
            return "android";
        }
        if (value.contains("iphone") || value.contains("ipad")) {
            return "ios";
        }
        if (value.contains("linux")) {
            return "linux";
        }
        return value.isBlank() ? "unknown" : "other";
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return Objects.toString(value, "").trim();
    }
}
