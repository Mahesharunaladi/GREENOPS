package com.vigidock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Client for the official FIRST Exploit Prediction Scoring System API.
 *
 * <p>Scores are cached for twelve hours because EPSS publishes a daily score. API failures return an
 * unavailable result instead of failing vulnerability scanning, allowing CVSS-based triage to
 * continue when the public service cannot be reached.
 */
@Service
public final class EpssService {

    private static final URI EPSS_ENDPOINT = URI.create("https://api.first.org/data/v1/epss");
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedScore> cache = new ConcurrentHashMap<>();

    public EpssService(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper);
    }

    EpssService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Fetches an EPSS probability and percentile for one CVE.
     *
     * @param cveId CVE identifier, for example {@code CVE-2025-1234}
     * @return available EPSS data or an unavailable marker
     */
    public EpssResult lookup(String cveId) {
        if (cveId == null || cveId.isBlank()) {
            return EpssResult.unavailable("unknown");
        }
        String normalizedCve = cveId.trim().toUpperCase();
        CachedScore cached = cache.get(normalizedCve);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.result();
        }
        EpssResult result = requestScore(normalizedCve);
        cache.put(normalizedCve, new CachedScore(result, Instant.now().plus(CACHE_TTL)));
        return result;
    }

    private EpssResult requestScore(String cveId) {
        try {
            String query = "?cve=" + URLEncoder.encode(cveId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(EPSS_ENDPOINT + query))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return EpssResult.unavailable(cveId);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return EpssResult.unavailable(cveId);
            }
            JsonNode item = data.get(0);
            return new EpssResult(
                    cveId,
                    decimal(item.path("epss").asText()),
                    decimal(item.path("percentile").asText()),
                    parseDate(item.path("date").asText()),
                    true);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return EpssResult.unavailable(cveId);
        }
    }

    private static double decimal(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? Math.max(0.0d, Math.min(1.0d, parsed)) : 0.0d;
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }

    private static Instant parseDate(String value) {
        try {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Parsed exploit likelihood response from FIRST.
     */
    public record EpssResult(
            String cveId, double epss, double percentile, Instant publishedAt, boolean available) {

        public EpssResult {
            Objects.requireNonNull(cveId, "cveId must not be null");
            if (!Double.isFinite(epss) || epss < 0.0d || epss > 1.0d) {
                throw new IllegalArgumentException("epss must be between 0 and 1");
            }
            if (!Double.isFinite(percentile) || percentile < 0.0d || percentile > 1.0d) {
                throw new IllegalArgumentException("percentile must be between 0 and 1");
            }
        }

        public static EpssResult unavailable(String cveId) {
            return new EpssResult(cveId, 0.0d, 0.0d, null, false);
        }
    }

    private record CachedScore(EpssResult result, Instant expiresAt) {}
}
