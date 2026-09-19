package com.greenops.workflow.risk;

import com.greenops.scrapper.model.ScanResult;
import com.greenops.workflow.identity.RiskEvaluationResult;
import com.greenops.workflow.identity.RiskEvaluationResult.AgentDecision;
import com.greenops.workflow.security.SecurityThreatAssessment;
import com.greenops.workflow.security.SecurityThreatAssessment.ThreatLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Calculates a weighted composite risk score from cost, security, and identity signals.
 *
 * <p>The calculator accepts output from the GreenOps idle scanner, third-party interference
 * detector, and Zero Trust identity agent. It emits a normalized score from {@code 0} to {@code 100}
 * with component-level metadata so policy engines can explain why remediation was triggered.
 *
 * <p><strong>Thread safety:</strong> this component is stateless. AWS SDK v2 clients are thread-safe;
 * when no client is injected, a CloudWatch client is created lazily only when metrics are emitted.
 */
@Component
public final class RiskScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreCalculator.class);
    private static final String METRIC_NAMESPACE = "GreenOps/ZeroTrust";
    private static final double COST_WEIGHT = 0.20d;
    private static final double SECURITY_WEIGHT = 0.35d;
    private static final double IDENTITY_WEIGHT = 0.45d;

    private final CloudWatchClient cloudWatchClient;

    public RiskScoreCalculator() {
        this(null);
    }

    RiskScoreCalculator(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    /**
     * Combines scanner, interference, and identity outputs into one composite risk score.
     *
     * @param scanResult latest idle-resource scan result; may be {@code null} when unavailable
     * @param securityAssessment latest interference assessment; may be {@code null} when unavailable
     * @param identityEvaluation latest identity AI evaluation; may be {@code null} when unavailable
     * @return auditable composite risk score from {@code 0} to {@code 100}
     */
    public CompositeRiskScore calculateRiskScore(
            ScanResult scanResult,
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation) {
        List<String> reasons = new ArrayList<>();
        double costScore = costRiskScore(scanResult, reasons);
        double securityScore = securityRiskScore(securityAssessment, reasons);
        double identityScore = identityRiskScore(identityEvaluation, reasons);
        double composite = clamp100(
                (costScore * COST_WEIGHT) + (securityScore * SECURITY_WEIGHT) + (identityScore * IDENTITY_WEIGHT));

        RiskBand band = RiskBand.fromScore(composite);
        Map<String, Double> components = new LinkedHashMap<>();
        components.put("costOptimization", costScore);
        components.put("securityInterference", securityScore);
        components.put("identityVerification", identityScore);

        CompositeRiskScore result = new CompositeRiskScore(
                composite,
                band,
                components,
                reasons,
                recommendedAction(band, securityAssessment, identityEvaluation),
                Instant.now());
        emitRiskMetrics(result);
        return result;
    }

    private double costRiskScore(ScanResult scanResult, List<String> reasons) {
        if (scanResult == null || scanResult.totalResources() <= 0) {
            reasons.add("Cost scanner signal unavailable");
            return 0.0d;
        }
        double idleRatio = (double) scanResult.idleResources() / Math.max(1, scanResult.totalResources());
        double savingsPressure = Math.min(1.0d, scanResult.totalEstimatedMonthlySavings() / 1000.0d);
        double unappliedActionPressure = scanResult.findings() == null
                ? 0.0d
            : scanResult.findings().stream()
                .filter(Objects::nonNull)
                .filter(finding -> !finding.actionTaken()).count()
                        / (double) Math.max(1, scanResult.findings().size());
        double score = clamp100((idleRatio * 55.0d) + (savingsPressure * 30.0d) + (unappliedActionPressure * 15.0d));
        if (score > 0.0d) {
            reasons.add("Idle scanner reported " + scanResult.idleResources() + " idle resources");
        }
        if (scanResult.findings() != null) {
            scanResult.findings().stream()
                    .filter(Objects::nonNull)
                    .map(finding -> finding.reason())
                    .filter(Objects::nonNull)
                    .filter(reason -> !reason.isBlank())
                    .limit(3)
                    .forEach(reason -> reasons.add("Cost finding: " + reason));
        }
        return score;
    }

    private double securityRiskScore(SecurityThreatAssessment assessment, List<String> reasons) {
        if (assessment == null) {
            reasons.add("Security interference assessment unavailable");
            return 0.0d;
        }
        if (assessment.detectionReasons() != null) {
            assessment.detectionReasons().forEach(reason -> reasons.add("Security: " + reason));
        }
        return switch (assessment.threatLevel()) {
            case LOW -> 20.0d;
            case MEDIUM -> 55.0d;
            case HIGH -> 80.0d;
            case CRITICAL -> 100.0d;
        };
    }

    private double identityRiskScore(RiskEvaluationResult evaluation, List<String> reasons) {
        if (evaluation == null) {
            reasons.add("Identity AI verification unavailable");
            return 0.0d;
        }
        reasons.add("Identity action: " + evaluation.action());
        return clamp100(evaluation.anomalyScore() * 100.0d);
    }

    private String recommendedAction(
            RiskBand band,
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation) {
        if (securityAssessment != null && securityAssessment.threatLevel() == ThreatLevel.CRITICAL) {
            return "invoke_iam_session_revoker";
        }
        if (identityEvaluation != null && identityEvaluation.decision() == AgentDecision.REVOKE_SESSION) {
            return "invoke_iam_session_revoker";
        }
        return switch (band) {
            case CRITICAL -> "invoke_iam_session_revoker";
            case HIGH -> "step_up_auth_and_reduce_permissions";
            case MEDIUM -> "increase_monitoring_and_reverify";
            case LOW -> "continue_monitoring";
        };
    }

    private void emitRiskMetrics(CompositeRiskScore result) {
        try {
            cloudWatch().putMetricData(PutMetricDataRequest.builder()
                    .namespace(METRIC_NAMESPACE)
                    .metricData(
                            metric("CompositeRiskScore", result.score(), StandardUnit.NONE, "RiskBand", result.riskBand().name()),
                            metric("RiskAssessmentCount", 1.0d, StandardUnit.COUNT, "RiskBand", result.riskBand().name()))
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Unable to emit CloudWatch risk metrics: {}", ex.getMessage());
        }
    }

    private CloudWatchClient cloudWatch() {
        return cloudWatchClient == null ? CloudWatchClient.create() : cloudWatchClient;
    }

    private MetricDatum metric(String name, double value, StandardUnit unit, String dimensionName, String dimensionValue) {
        return MetricDatum.builder()
                .metricName(name)
                .value(value)
                .unit(unit)
                .timestamp(Instant.now())
                .dimensions(Dimension.builder().name(dimensionName).value(dimensionValue).build())
                .build();
    }

    private static double clamp100(double value) {
        if (!Double.isFinite(value)) {
            return 100.0d;
        }
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    /**
     * Composite risk output with auditable component scores.
     */
    public record CompositeRiskScore(
            double score,
            RiskBand riskBand,
            Map<String, Double> componentScores,
            List<String> reasons,
            String recommendedAction,
            Instant calculatedAt) {

        public CompositeRiskScore {
            score = clamp100(score);
            Objects.requireNonNull(riskBand, "riskBand must not be null");
            componentScores = componentScores == null ? Map.of() : Map.copyOf(componentScores);
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            recommendedAction = recommendedAction == null || recommendedAction.isBlank()
                    ? "continue_monitoring"
                    : recommendedAction;
            calculatedAt = calculatedAt == null ? Instant.now() : calculatedAt;
        }
    }

    /**
     * Operational risk band derived from the composite score.
     */
    public enum RiskBand {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL;

        static RiskBand fromScore(double score) {
            if (score >= 85.0d) {
                return CRITICAL;
            }
            if (score >= 70.0d) {
                return HIGH;
            }
            if (score >= 40.0d) {
                return MEDIUM;
            }
            return LOW;
        }
    }
}
