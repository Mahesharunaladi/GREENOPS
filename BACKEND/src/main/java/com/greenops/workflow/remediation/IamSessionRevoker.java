package com.greenops.workflow.remediation;

import com.greenops.workflow.identity.RiskEvaluationResult;
import com.greenops.workflow.identity.RiskEvaluationResult.AgentDecision;
import com.greenops.workflow.risk.RiskScoreCalculator.CompositeRiskScore;
import com.greenops.workflow.risk.RiskScoreCalculator.RiskBand;
import com.greenops.workflow.security.SecurityThreatAssessment;
import com.greenops.workflow.security.SecurityThreatAssessment.ThreatLevel;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.PutUserPolicyRequest;

/**
 * Applies emergency IAM remediation by attaching an inline explicit {@code DenyAll} policy.
 *
 * <p>The revoker is intended for critical Zero Trust detections where identity verification or
 * interference signals indicate an active compromise. It supports IAM users and IAM roles and emits
 * CloudWatch metrics for every success, skip, and failure for audit-trail monitoring.
 *
 * <p><strong>Thread safety:</strong> the component is stateless. AWS SDK v2 clients are thread-safe;
 * when no clients are injected, they are created lazily during remediation calls.
 */
@Component
public final class IamSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(IamSessionRevoker.class);
    private static final String METRIC_NAMESPACE = "GreenOps/ZeroTrust";
    private static final String POLICY_NAME_PREFIX = "GreenOpsCriticalDenyAll";
    private static final String DENY_ALL_POLICY_DOCUMENT = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Sid": "GreenOpsCriticalThreatDenyAll",
                  "Effect": "Deny",
                  "Action": "*",
                  "Resource": "*"
                }
              ]
            }
            """;

    private final IamClient iamClient;
    private final CloudWatchClient cloudWatchClient;

    public IamSessionRevoker() {
        this(null, null);
    }

    IamSessionRevoker(IamClient iamClient, CloudWatchClient cloudWatchClient) {
        this.iamClient = iamClient;
        this.cloudWatchClient = cloudWatchClient;
    }

    /**
     * Attaches an inline explicit {@code DenyAll} policy when a critical threat is present.
     *
     * @param principal IAM user or role to remediate
     * @param securityAssessment interference assessment
     * @param identityEvaluation identity AI evaluation
     * @param compositeRiskScore weighted composite risk score
     * @return remediation outcome with audit metadata
     */
    public RevocationResult revokeOnCriticalThreat(
            IamPrincipal principal,
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation,
            CompositeRiskScore compositeRiskScore) {
        Objects.requireNonNull(principal, "principal must not be null");
        if (!criticalThreatDetected(securityAssessment, identityEvaluation, compositeRiskScore)) {
            RevocationResult skipped = RevocationResult.skipped(
                    principal,
                    "No critical threat detected; IAM DenyAll policy was not attached");
            emitRevocationMetric("Skipped", principal, 1.0d);
            return skipped;
        }
        return attachDenyAllPolicy(principal, remediationReason(securityAssessment, identityEvaluation, compositeRiskScore));
    }

    /**
     * Forces emergency revocation regardless of calculated severity.
     *
     * @param principal IAM user or role to remediate
     * @param reason human-readable audit reason
     * @return remediation outcome
     */
    public RevocationResult forceRevoke(IamPrincipal principal, String reason) {
        Objects.requireNonNull(principal, "principal must not be null");
        return attachDenyAllPolicy(principal, reason == null || reason.isBlank() ? "manual critical override" : reason);
    }

    private RevocationResult attachDenyAllPolicy(IamPrincipal principal, String reason) {
        String policyName = policyName(principal);
        try {
            if (principal.type() == PrincipalType.USER) {
                iam().putUserPolicy(PutUserPolicyRequest.builder()
                        .userName(principal.name())
                        .policyName(policyName)
                        .policyDocument(DENY_ALL_POLICY_DOCUMENT)
                        .build());
            } else {
                iam().putRolePolicy(PutRolePolicyRequest.builder()
                        .roleName(principal.name())
                        .policyName(policyName)
                        .policyDocument(DENY_ALL_POLICY_DOCUMENT)
                        .build());
            }
            emitRevocationMetric("Succeeded", principal, 1.0d);
            return RevocationResult.success(principal, policyName, reason);
        } catch (IamException ex) {
            log.error("IAM session revocation failed for {} {}: {}", principal.type(), principal.name(), ex.awsErrorDetails().errorMessage());
            emitRevocationMetric("Failed", principal, 1.0d);
            return RevocationResult.failure(principal, policyName, reason, ex.awsErrorDetails().errorMessage());
        } catch (RuntimeException ex) {
            log.error("IAM session revocation failed for {} {}: {}", principal.type(), principal.name(), ex.getMessage());
            emitRevocationMetric("Failed", principal, 1.0d);
            return RevocationResult.failure(principal, policyName, reason, ex.getMessage());
        }
    }

    private boolean criticalThreatDetected(
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation,
            CompositeRiskScore compositeRiskScore) {
        return (securityAssessment != null && securityAssessment.threatLevel() == ThreatLevel.CRITICAL)
                || (identityEvaluation != null && identityEvaluation.decision() == AgentDecision.REVOKE_SESSION)
                || (compositeRiskScore != null && compositeRiskScore.riskBand() == RiskBand.CRITICAL);
    }

    private String remediationReason(
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation,
            CompositeRiskScore compositeRiskScore) {
        if (securityAssessment != null && securityAssessment.threatLevel() == ThreatLevel.CRITICAL) {
            return "critical security interference assessment";
        }
        if (identityEvaluation != null && identityEvaluation.decision() == AgentDecision.REVOKE_SESSION) {
            return "identity AI requested session revocation";
        }
        if (compositeRiskScore != null && compositeRiskScore.riskBand() == RiskBand.CRITICAL) {
            return "critical composite risk score " + compositeRiskScore.score();
        }
        return "critical threat detected";
    }

    private String policyName(IamPrincipal principal) {
        String normalizedName = principal.name().replaceAll("[^A-Za-z0-9+=,.@_-]", "-");
        String suffix = Integer.toHexString(Objects.hash(principal.type(), principal.name()));
        String rawName = POLICY_NAME_PREFIX + "-" + normalizedName + "-" + suffix;
        return rawName.length() <= 128 ? rawName : rawName.substring(0, 128);
    }

    private IamClient iam() {
        return iamClient == null ? IamClient.builder().region(Region.AWS_GLOBAL).build() : iamClient;
    }

    private CloudWatchClient cloudWatch() {
        return cloudWatchClient == null ? CloudWatchClient.create() : cloudWatchClient;
    }

    private void emitRevocationMetric(String status, IamPrincipal principal, double value) {
        try {
            cloudWatch().putMetricData(PutMetricDataRequest.builder()
                    .namespace(METRIC_NAMESPACE)
                    .metricData(MetricDatum.builder()
                            .metricName("IamSessionRevocation")
                            .unit(StandardUnit.COUNT)
                            .value(value)
                            .timestamp(Instant.now())
                            .dimensions(
                                    Dimension.builder().name("Status").value(status).build(),
                                    Dimension.builder().name("PrincipalType").value(principal.type().name()).build())
                            .build())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Unable to emit CloudWatch IAM revocation metric: {}", ex.getMessage());
        }
    }

    /**
     * IAM principal targeted for emergency remediation.
     */
    public record IamPrincipal(PrincipalType type, String name) {
        public IamPrincipal {
            Objects.requireNonNull(type, "type must not be null");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            name = name.trim();
        }
    }

    /**
     * Supported IAM principal types for inline policy attachment.
     */
    public enum PrincipalType {
        USER,
        ROLE
    }

    /**
     * Result of an IAM remediation attempt.
     */
    public record RevocationResult(
            IamPrincipal principal,
            boolean attempted,
            boolean successful,
            String policyName,
            String reason,
            String errorMessage,
            Instant remediatedAt,
            Map<String, String> auditMetadata) {

        public RevocationResult {
            Objects.requireNonNull(principal, "principal must not be null");
            policyName = policyName == null ? "" : policyName;
            reason = reason == null || reason.isBlank() ? "unspecified" : reason;
            errorMessage = errorMessage == null ? "" : errorMessage;
            remediatedAt = remediatedAt == null ? Instant.now() : remediatedAt;
            auditMetadata = auditMetadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(auditMetadata));
        }

        private static RevocationResult success(IamPrincipal principal, String policyName, String reason) {
            return new RevocationResult(
                    principal,
                    true,
                    true,
                    policyName,
                    reason,
                    "",
                    Instant.now(),
                    Map.of("policyEffect", "explicit_deny_all", "auditTrail", "cloudwatch_metric_emitted"));
        }

        private static RevocationResult failure(
                IamPrincipal principal, String policyName, String reason, String errorMessage) {
            return new RevocationResult(
                    principal,
                    true,
                    false,
                    policyName,
                    reason,
                    errorMessage,
                    Instant.now(),
                    Map.of("policyEffect", "not_attached", "auditTrail", "cloudwatch_metric_emitted"));
        }

        private static RevocationResult skipped(IamPrincipal principal, String reason) {
            return new RevocationResult(
                    principal,
                    false,
                    false,
                    "",
                    reason,
                    "",
                    Instant.now(),
                    Map.of("policyEffect", "not_required", "auditTrail", "cloudwatch_metric_emitted"));
        }
    }
}
