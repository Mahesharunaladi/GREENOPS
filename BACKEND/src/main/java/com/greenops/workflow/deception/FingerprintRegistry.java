package com.greenops.workflow.deception;

import com.greenops.model.AttackerActionLog;
import com.greenops.model.ThreatFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/** Bounded concurrent threat cache with optional reactive durable-repository hooks. */
@Service
public final class FingerprintRegistry {
    private static final Logger log = LoggerFactory.getLogger(FingerprintRegistry.class);
    private static final int MAX_FINGERPRINTS = 20_000;
    private static final int MAX_ACTIONS = 50_000;
    private final Map<String, ThreatFingerprint> fingerprints = new ConcurrentHashMap<>();
    private final Map<String, AttackerActionLog> actions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> fingerprintOrder = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<String> actionOrder = new ConcurrentLinkedDeque<>();
    private final ReactiveThreatRepository repository;
    private final CloudWatchClient cloudWatch;

    public FingerprintRegistry(ReactiveThreatRepository repository, CloudWatchClient cloudWatch) {
        this.repository = repository;
        this.cloudWatch = cloudWatch;
    }

    public boolean isBlacklisted(String clientIp, String tlsJa3) {
        return fingerprints.containsKey(compositeHash(clientIp, tlsJa3));
    }

    public Mono<Boolean> isBlacklistedReactive(String clientIp, String tlsJa3) {
        String hash = compositeHash(clientIp, tlsJa3);
        ThreatFingerprint local = fingerprints.get(hash);
        if (local != null) return Mono.just(true);
        return repository.findFingerprint(hash)
                .defaultIfEmpty(Optional.empty())
                .map(found -> found.map(fingerprint -> {
                    cacheFingerprint(fingerprint);
                    return true;
                }).orElse(false));
    }

    public void registerThreat(String clientIp, String tlsJa3, String userAgent, String spiffeId, double bioScore) {
        String hash = compositeHash(clientIp, tlsJa3);
        Instant now = Instant.now();
        ThreatFingerprint updated = fingerprints.compute(hash, (key, previous) -> new ThreatFingerprint(
                key, bounded(clientIp, 64), bounded(tlsJa3, 256), bounded(userAgent, 512), safeSpiffeReference(spiffeId), clamp(bioScore),
                previous == null ? now : previous.firstDetectedAt(), now,
                previous == null ? 1 : previous.violationCount() == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.violationCount() + 1));
        if (updated.violationCount() == 1) fingerprintOrder.addLast(hash);
        trim(fingerprints, fingerprintOrder, MAX_FINGERPRINTS);
        repository.saveFingerprint(updated).subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {}, error -> log.warn("Threat fingerprint persistence failed: {}", error.getClass().getSimpleName()));
        Mono.fromRunnable(this::emitTrappedMetric).subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {}, error -> log.warn("Deception metric scheduling failed"));
        log.warn("Threat fingerprint registered hash={} violations={}", hash, updated.violationCount());
    }

    public Mono<ThreatFingerprint> registerThreatReactive(String clientIp, String tlsJa3, String userAgent, String spiffeId, double bioScore) {
        registerThreat(clientIp, tlsJa3, userAgent, spiffeId, bioScore);
        return Mono.just(fingerprints.get(compositeHash(clientIp, tlsJa3)));
    }

    public void recordAction(AttackerActionLog action) {
        actions.put(action.logId(), action);
        actionOrder.addLast(action.logId());
        trim(actions, actionOrder, MAX_ACTIONS);
        repository.saveAction(action).subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {}, error -> log.warn("Attacker action persistence failed: {}", error.getClass().getSimpleName()));
    }

    public List<AttackerActionLog> recentActions(int limit) {
        int boundedLimit = Math.max(0, Math.min(500, limit));
        java.util.ArrayList<AttackerActionLog> recent = new java.util.ArrayList<>(boundedLimit);
        var iterator = actionOrder.descendingIterator();
        while (iterator.hasNext() && recent.size() < boundedLimit) {
            AttackerActionLog action = actions.get(iterator.next());
            if (action != null) recent.add(action);
        }
        return List.copyOf(recent);
    }

    public Optional<ThreatFingerprint> fingerprint(String compositeHash) {
        return Optional.ofNullable(fingerprints.get(compositeHash));
    }

    private void cacheFingerprint(ThreatFingerprint fingerprint) {
        if (fingerprints.putIfAbsent(fingerprint.compositeHash(), fingerprint) == null) {
            fingerprintOrder.addLast(fingerprint.compositeHash());
            trim(fingerprints, fingerprintOrder, MAX_FINGERPRINTS);
        }
    }

    public static String compositeHash(String clientIp, String tlsJa3) {
        try {
            String canonical = bounded(normalize(clientIp), 64) + ":" + bounded(normalize(tlsJa3), 256);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void emitTrappedMetric() {
        try {
            cloudWatch.putMetricData(PutMetricDataRequest.builder().namespace("GreenOps/Deception")
                    .metricData(MetricDatum.builder().metricName("greenops.deception.trapped_attackers")
                            .unit(StandardUnit.COUNT).value(1.0d).timestamp(Instant.now()).build()).build());
        } catch (RuntimeException error) {
            log.warn("Unable to emit deception metric: {}", error.getClass().getSimpleName());
        }
    }

    private static <T> void trim(Map<String, T> map, ConcurrentLinkedDeque<String> order, int max) {
        while (map.size() > max) {
            String oldest = order.pollFirst();
            if (oldest == null) return;
            map.remove(oldest);
        }
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private static String bounded(String value, int max) { if (value == null) return "unknown"; return value.length() <= max ? value : value.substring(0, max); }
    private static String safeSpiffeReference(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String trimmed = value.trim();
        if (trimmed.startsWith("spiffe://") && !trimmed.contains("\n")) return bounded(trimmed, 1024);
        try {
            return "svid-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(trimmed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            return "unavailable";
        }
    }
    private static double clamp(double value) { return !Double.isFinite(value) ? 0 : Math.max(0, Math.min(1, value)); }
}
