package com.vigidock.epss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vigidock.model.ScanRecord;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Enriches CVEs with real-world exploit probability from the public FIRST EPSS API.
 *
 * <p>EPSS probabilities are cached for twelve hours because FIRST publishes them daily. Failure to
 * retrieve a score leaves the vulnerability in the result with an unavailable EPSS value; a network
 * issue never hides a finding or prevents its policy evaluation.
 */
@Service
public final class EpssRiskService {

    private static final URI EPSS_ENDPOINT = URI.create("https://api.first.org/data/v1/epss");
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedScore> cache = new ConcurrentHashMap<>();

    public EpssRiskService(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper);
    }

    EpssRiskService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Enriches each scan finding and orders it by combined CVSS and EPSS priority.
     *
     * @param scanRecord scan data containing CVE findings and optional CVSS scores
     * @return prioritized scan suitable for triage and remediation planning
     */
    public PrioritizedScan enrich(ScanRecord scanRecord) {
        Objects.requireNonNull(scanRecord, "scanRecord must not be null");
        List<PrioritizedVulnerability> vulnerabilities = new ArrayList<>();
        for (ScanRecord.VulnerabilityFinding finding : scanRecord.findings()) {
            EpssScore epssScore = lookup(finding.cveId());
            double priorityScore = priorityScore(finding.cvssScore(), epssScore.probability());
            vulnerabilities.add(new PrioritizedVulnerability(
                    finding, epssScore, priorityScore, priorityFor(priorityScore)));
        }
        vulnerabilities.sort(Comparator.comparingDouble(PrioritizedVulnerability::priorityScore).reversed());
        return new PrioritizedScan(scanRecord, vulnerabilities, Instant.now());
    }

    /**
     * Looks up a single CVE without throwing for unavailable EPSS data.
     *
     * @param cveId CVE identifier
     * @return available score or an unavailable score marker
     */
    public EpssScore lookup(String cveId) {
        if (cveId == null || cveId.isBlank()) {
            return EpssScore.unavailable("unknown");
        }
        String normalizedCve = cveId.trim().toUpperCase();
        CachedScore cachedScore = cache.get(normalizedCve);
        if (cachedScore != null && cachedScore.expiresAt().isAfter(Instant.now())) {
            return cachedScore.score();
        }
        EpssScore score = fetch(normalizedCve);
        cache.put(normalizedCve, new CachedScore(score, Instant.now().plus(CACHE_TTL)));
        return score;
    }

    private EpssScore fetch(String cveId) {
        try {
            String query = "?cve=" + URLEncoder.encode(cveId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(EPSS_ENDPOINT + query))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return EpssScore.unavailable(cveId);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return EpssScore.unavailable(cveId);
            }
            JsonNode item = data.get(0);
            return new EpssScore(
                    cveId,
                    decimal(item, "epss"),
                    decimal(item, "percentile"),
                    parseDate(item.path("date").asText()),
                    true);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return EpssScore.unavailable(cveId);
        }
    }

    private static double priorityScore(Double cvssScore, double epssProbability) {
        double normalizedCvss = cvssScore == null ? 0.0d : cvssScore / 10.0d;
        return clamp((normalizedCvss * 0.55d) + (epssProbability * 0.45d)) * 100.0d;
    }

    private static PrioritizedVulnerability.Priority priorityFor(double score) {
        if (score >= 80.0d) {
            return PrioritizedVulnerability.Priority.CRITICAL;
        }
        if (score >= 60.0d) {
            return PrioritizedVulnerability.Priority.HIGH;
        }
        if (score >= 30.0d) {
            return PrioritizedVulnerability.Priority.MEDIUM;
        }
        return PrioritizedVulnerability.Priority.LOW;
    }

    private static double decimal(JsonNode node, String fieldName) {
        try {
            return clamp(Double.parseDouble(node.path(fieldName).asText("0")));
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

    private static double clamp(double value) {
        return !Double.isFinite(value) ? 0.0d : Math.max(0.0d, Math.min(1.0d, value));
    }

    private record CachedScore(EpssScore score, Instant expiresAt) {}
}
