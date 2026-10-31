package com.greenops.workflow.advanced;

import com.greenops.model.TtcRiskAssessment;
import com.greenops.workflow.deception.UserSessionContext;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/** Transparent weighted heuristic; it is an estimate, not a trained compromise-time model. */
@Service
public final class PredictiveRiskEngine {
    private static final Logger log = LoggerFactory.getLogger(PredictiveRiskEngine.class);
    private final PredictiveRiskSignalProvider signalProvider;
    private final CloudWatchAsyncClient cloudWatch;

    public PredictiveRiskEngine(PredictiveRiskSignalProvider signalProvider, CloudWatchAsyncClient cloudWatch) {
        this.signalProvider = signalProvider; this.cloudWatch = cloudWatch;
    }

    public Mono<TtcRiskAssessment> calculatePredictiveTtc(String userId, UserSessionContext session) {
        if (userId == null || userId.isBlank() || userId.length() > 256) return Mono.error(new IllegalArgumentException("userId is required"));
        if (session == null) return Mono.error(new IllegalArgumentException("Trusted session context is required"));
        return signalProvider.currentSignals(userId, session).flatMap(signals -> {
            double network = signals.networkDrift();
            double hardware = Math.max(signals.hardwareAttestationDelta(), session.spireVerified() ? 0.0d : 1.0d);
            double biometric = Math.max(signals.biometricVarianceVelocity(), 1.0d - session.biometricScore());
            double score = clamp(network * 0.35d + hardware * 0.30d + biometric * 0.35d);
            double ttcMinutes = score <= 0.01d ? 60.0d : Math.max(0.5d, 60.0d * Math.pow(1.0d - score, 2.0d));
            String factor = network >= hardware && network >= biometric ? "NETWORK_DRIFT"
                    : hardware >= biometric ? "HARDWARE_ATTESTATION_DELTA" : "BIOMETRIC_VARIANCE_VELOCITY";
            TtcRiskAssessment assessment = new TtcRiskAssessment(userId, ttcMinutes, score * 100.0d, factor, Instant.now());
            return emitMetric(ttcMinutes).thenReturn(assessment)
                    .doOnSuccess(result -> log.info("Predictive risk estimate calculated: userId={}, ttcMinutes={}, exposurePct={}, factor={}",
                            userId, result.timeToCompromiseMinutes(), result.exposureProbabilityPercentage(), factor));
        });
    }

    private Mono<Void> emitMetric(double ttcMinutes) {
        return Mono.fromFuture(cloudWatch.putMetricData(PutMetricDataRequest.builder().namespace("GreenOps/Advanced")
                .metricData(MetricDatum.builder().metricName("greenops.risk.predictive_ttc").unit(StandardUnit.NONE)
                        .value(ttcMinutes).timestamp(Instant.now()).build()).build())).then()
                .onErrorResume(error -> { log.warn("Predictive TTC metric emission failed: {}", error.getClass().getSimpleName()); return Mono.empty(); });
    }
    private static double clamp(double value) { return Math.max(0.0d, Math.min(1.0d, value)); }
}
