package com.greenops.workflow.spire;

import com.greenops.model.SpireEvaluationResult;
import com.greenops.model.SpireReadiness;
import com.greenops.model.WorkloadTelemetry;
import com.greenops.workflow.remediation.IamSessionRevoker;
import com.greenops.workflow.advanced.AttackStorylineService;
import com.greenops.workflow.notification.SecurityNotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/** Reactive SPIFFE attestation workflow with bounded-memory process baselines and enforcement adapters. */
@Service
public final class SpireWorkloadWatcher {
    private static final Logger log = LoggerFactory.getLogger(SpireWorkloadWatcher.class);
    private static final Duration SVID_TTL = Duration.ofMinutes(10);
    private static final Duration MAX_EVENT_AGE = Duration.ofMinutes(5);

    private final SpiffeSvidValidator svidValidator;
    private final ProcessBaselineStore baselineStore;
    private final BiometricSignalProvider biometrics;
    private final SpireRevocationClient spireRevoker;
    private final SpireSvidIssuer svidIssuer;
    private final IamPrincipalResolver principalResolver;
    private final IamSessionRevoker iamSessionRevoker;
    private final CloudWatchClient cloudWatchClient;
    private final AttackStorylineService storyline;
    private final SecurityNotificationService notifications;
    private final SpireAgentSocketProbe agentSocketProbe;
    private final Set<String> trustedNodeAttestationHashes;
    private final AtomicLong attestationsSeen = new AtomicLong();
    private final AtomicReference<Instant> lastAttestationAt = new AtomicReference<>();
    private final AtomicReference<Boolean> lastAttestationVerified = new AtomicReference<>();

    public SpireWorkloadWatcher(
            SpiffeSvidValidator svidValidator,
            ProcessBaselineStore baselineStore,
            BiometricSignalProvider biometrics,
            SpireRevocationClient spireRevoker,
            SpireSvidIssuer svidIssuer,
            IamPrincipalResolver principalResolver,
            IamSessionRevoker iamSessionRevoker,
            CloudWatchClient cloudWatchClient,
            AttackStorylineService storyline,
            SecurityNotificationService notifications,
            SpireAgentSocketProbe agentSocketProbe,
            @Value("${greenops.spire.trusted-node-attestation-hashes:}") String trustedNodeHashes) {
        this.svidValidator = Objects.requireNonNull(svidValidator);
        this.baselineStore = Objects.requireNonNull(baselineStore);
        this.biometrics = Objects.requireNonNull(biometrics);
        this.spireRevoker = Objects.requireNonNull(spireRevoker);
        this.svidIssuer = Objects.requireNonNull(svidIssuer);
        this.principalResolver = Objects.requireNonNull(principalResolver);
        this.iamSessionRevoker = Objects.requireNonNull(iamSessionRevoker);
        this.cloudWatchClient = Objects.requireNonNull(cloudWatchClient);
        this.storyline = Objects.requireNonNull(storyline);
        this.notifications = Objects.requireNonNull(notifications);
        this.agentSocketProbe = Objects.requireNonNull(agentSocketProbe);
        this.trustedNodeAttestationHashes = Arrays.stream(trustedNodeHashes.split(","))
                .map(value -> value.trim()).filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Mono<SpireEvaluationResult> evaluate(WorkloadTelemetry event) {
        Objects.requireNonNull(event, "event must not be null");
        SpiffeSvidValidator.Validation svid = svidValidator.validate(event.spiffeSvid());
        ProcessAssessment process = assessProcess(event);
        if (!svid.valid()) {
            return enforceFailure(event, svid.spiffeId(), svid.reason(), 0.0d, false);
        }
        if (trustedNodeAttestationHashes.isEmpty()
                || !trustedNodeAttestationHashes.contains(event.hostNodeAttestationHash().toLowerCase(Locale.ROOT))) {
            return enforceFailure(event, svid.spiffeId(), "Host node attestation hash is not trusted", process.score(), true);
        }
        if (!fresh(event.timestamp())) {
            return enforceFailure(event, svid.spiffeId(), "Telemetry timestamp is stale or in the future", process.score(), true);
        }
        return biometrics.confidence(event.sessionId(), event.userId())
                .defaultIfEmpty(OptionalDouble.empty())
                .onErrorResume(error -> {
                    log.warn("Biometric confidence lookup failed for session {}", event.sessionId());
                    return Mono.just(OptionalDouble.empty());
                })
                .flatMap(confidence -> {
                    if (confidence.isEmpty() || !Double.isFinite(confidence.getAsDouble()) || confidence.getAsDouble() <= 0.85d) {
                        return enforceFailure(event, svid.spiffeId(), "Biometric confidence is unavailable or below 0.85", process.score(), true);
                    }
                    if (!process.authorized()) {
                        return enforceFailure(event, svid.spiffeId(), process.reason(), process.score(), true);
                    }
                    return svidIssuer.issueOrRenew(svid.spiffeId(), SVID_TTL)
                            .defaultIfEmpty(false)
                            .onErrorReturn(false)
                            .flatMap(issued -> {
                                if (!issued) return enforceFailure(event, svid.spiffeId(), "SPIRE Workload API did not issue or renew the short-lived SVID", process.score(), true);
                                baselineStore.recordAuthorizedExecution(event.userId(), event.binaryName().toLowerCase(Locale.ROOT));
                                return result(true, "LOW", svid.spiffeId(), "SVID verified; process authorized; biometric confidence above threshold", process.score());
                            });
                });
    }

    public SpireReadiness readiness() {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("svidTrustMaterial", svidValidator.isConfigured());
        checks.put("spireAgentSocketReachable", agentSocketProbe.isReachable());
        checks.put("trustedNodeAttestationHashes", !trustedNodeAttestationHashes.isEmpty());
        checks.put("processBaseline", baselineStore.isConfigured());
        checks.put("biometricSignalAdapter", biometrics.isConfigured());
        checks.put("spireSvidIssuer", svidIssuer.isConfigured());
        checks.put("spireRevocationAdapter", spireRevoker.isConfigured());
        checks.put("iamPrincipalMapping", principalResolver.isConfigured());
        boolean ready = true;
        for (Boolean configured : checks.values()) {
            if (!Boolean.TRUE.equals(configured)) {
                ready = false;
                break;
            }
        }
        Instant lastSeen = lastAttestationAt.get();
        boolean recentActivity = lastSeen != null && lastSeen.isAfter(Instant.now().minusSeconds(60));
        String status = !ready ? "INACTIVE" : recentActivity ? "WATCHING" : "READY";
        return new SpireReadiness(ready, status,
                !ready ? "SPIRE is not watching yet. Configure the missing attestation integrations."
                        : recentActivity ? "Recent workload attestation requests are being evaluated."
                        : "Attestation integrations are configured; the endpoint processes requests on demand.",
                checks, attestationsSeen.get(), lastSeen, lastAttestationVerified.get(), Instant.now());
    }

    private ProcessAssessment assessProcess(WorkloadTelemetry event) {
        String name = event.binaryName().toLowerCase(Locale.ROOT);
        String path = event.binaryPath().toLowerCase(Locale.ROOT);
        String leafName = path.substring(path.lastIndexOf('/') + 1);
        if (!name.equals(leafName)) return new ProcessAssessment(false, 0.0d, "Binary name does not match the reported executable path");
        if (Set.of("nmap", "nc", "netcat", "masscan", "xmrig", "minerd", "cryptominer").contains(name)) {
            return new ProcessAssessment(false, 0.0d, "Known high-risk network or mining binary is not authorized");
        }
        Set<String> authorized = baselineStore.authorizedBinaries(event.userId());
        if (authorized == null || !authorized.contains(name)) return new ProcessAssessment(false, 0.0d, "Binary is absent from the user's authorized process baseline");
        int utcHour = event.timestamp().atZone(ZoneOffset.UTC).getHour();
        if (utcHour < 5 || utcHour >= 23) return new ProcessAssessment(false, 0.5d, "Process execution occurred outside the configured baseline window");
        double match = baselineStore.historicalExecutions(event.userId(), name) > 0 ? 1.0d : 0.8d;
        return new ProcessAssessment(true, match, match == 1.0d
                ? "Process matches the user's historical baseline"
                : "Process is allowlisted; no successful historical executions are cached yet");
    }

    private boolean fresh(Instant timestamp) {
        Instant now = Instant.now();
        return !timestamp.isBefore(now.minus(MAX_EVENT_AGE)) && !timestamp.isAfter(now.plusSeconds(30));
    }

    private Mono<SpireEvaluationResult> enforceFailure(
            WorkloadTelemetry event, String spiffeId, String reason, double baselineScore, boolean trustedSvid) {
        Mono<Boolean> revokeSvid = trustedSvid
                ? spireRevoker.revokeSvid(spiffeId, reason).onErrorReturn(false)
                : Mono.just(false);
        return revokeSvid.flatMap(revoked -> {
            String stage = reason.toLowerCase(Locale.ROOT).contains("biometric") ? "BIOMETRIC_ANOMALY"
                    : trustedSvid ? "SPIRE_REVOCATION" : "INITIAL_AUTH";
            Mono<Void> storylineWrite = storyline.isConfigured()
                    ? storyline.recordStorylineEvent(event.sessionId(), stage,
                            "SPIRE workload decision denied; SVID revocation attempted=" + trustedSvid + ", completed=" + revoked + ", reason=" + reason,
                            "unknown").onErrorResume(error -> {
                                log.warn("Could not append SPIRE storyline event: {}", error.getClass().getSimpleName());
                                return Mono.empty();
                            })
                    : Mono.empty();
            return storylineWrite.then(Mono.fromCallable(() -> {
                    // Resolver must bind the submitted session/user pair to a server-trusted IAM principal.
                    principalResolver.resolve(event.sessionId(), event.userId()).ifPresent(principal -> {
                        var outcome = iamSessionRevoker.forceRevoke(principal, "SPIRE workload attestation failure: " + reason);
                        if (!outcome.successful()) log.error("IAM deny policy failed for mapped principal type {}", principal.type());
                    });
                    log.warn("Workload attestation denied: session={}, svidRevoked={}, reason={}", event.sessionId(), revoked, reason);
                    return evaluation(false, tierFor(reason), spiffeId, reason, baselineScore);
                }).subscribeOn(Schedulers.boundedElastic()))
                    .flatMap(evaluation -> notifications.notifyUser(event.userId(), "SPIRE", tierFor(reason),
                            "Workload attestation denied: " + reason).thenReturn(evaluation));
        });
    }

    private Mono<SpireEvaluationResult> result(boolean verified, String tier, String spiffeId, String reason, double score) {
        return Mono.fromCallable(() -> evaluation(verified, tier, spiffeId, reason, score))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private SpireEvaluationResult evaluation(boolean verified, String tier, String spiffeId, String reason, double score) {
        attestationsSeen.incrementAndGet();
        lastAttestationAt.set(Instant.now());
        lastAttestationVerified.set(verified);
        emitMetric(verified);
        if (verified) log.info("Workload attestation verified: spiffeId={}, riskTier={}, baselineMatch={}", spiffeId, tier, score);
        return new SpireEvaluationResult(verified, tier, spiffeId, reason, score, Instant.now());
    }

    private String tierFor(String reason) {
        return reason.contains("SPIRE") || reason.contains("SVID") || reason.contains("Biometric") ? "CRITICAL" : "HIGH";
    }

    private void emitMetric(boolean success) {
        String metricName = success ? "greenops.spire.attestation.success" : "greenops.spire.attestation.failure";
        try {
            cloudWatchClient.putMetricData(PutMetricDataRequest.builder().namespace("GreenOps/SPIRE")
                    .metricData(MetricDatum.builder().metricName(metricName).unit(StandardUnit.COUNT).value(1.0d)
                            .timestamp(Instant.now()).dimensions(Dimension.builder().name("Outcome").value(success ? "success" : "failure").build()).build())
                    .build());
        } catch (RuntimeException error) {
            log.warn("Unable to emit SPIRE attestation metric {}", metricName);
        }
    }

    private record ProcessAssessment(boolean authorized, double score, String reason) {}
}
