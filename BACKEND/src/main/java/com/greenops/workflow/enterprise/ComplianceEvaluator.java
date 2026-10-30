package com.greenops.workflow.enterprise;

import com.greenops.model.ComplianceEvaluationResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.rds.RdsAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;

@Service
public final class ComplianceEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ComplianceEvaluator.class);
    private final S3AsyncClient s3;
    private final Ec2AsyncClient ec2;
    private final RdsAsyncClient rds;
    private final IamAsyncClient iam;
    private final CloudWatchAsyncClient cloudWatch;
    private final String region;

    public ComplianceEvaluator(S3AsyncClient s3, Ec2AsyncClient ec2, RdsAsyncClient rds,
            IamAsyncClient iam, CloudWatchAsyncClient cloudWatch,
            @Value("${greenops.aws.region:us-east-1}") String region) {
        this.s3 = s3; this.ec2 = ec2; this.rds = rds; this.iam = iam; this.cloudWatch = cloudWatch; this.region = region;
    }

    public Mono<ComplianceEvaluationResult> evaluateCompliancePosture(String awsAccountId) {
        if (awsAccountId == null || !awsAccountId.matches("\\d{12}")) {
            return Mono.error(new IllegalArgumentException("awsAccountId must contain 12 digits"));
        }
        CompletableFuture<List<String>> s3Findings = s3.listBuckets().thenCompose(list -> {
            List<CompletableFuture<String>> checks = list.buckets().stream().map(bucket ->
                    s3.getPublicAccessBlock(GetPublicAccessBlockRequest.builder().bucket(bucket.name()).build())
                            .thenApply(block -> block.publicAccessBlockConfiguration().blockPublicAcls()
                                    && block.publicAccessBlockConfiguration().ignorePublicAcls()
                                    && block.publicAccessBlockConfiguration().blockPublicPolicy()
                                    && block.publicAccessBlockConfiguration().restrictPublicBuckets()
                                    ? null : "SOC_2_CC6.1/NIST_AC-3 arn:aws:s3:::" + bucket.name())
                            .exceptionally(error -> {
                                Throwable cause = unwrap(error);
                                if (cause instanceof software.amazon.awssdk.services.s3.model.S3Exception s3Error && s3Error.statusCode() == 404) {
                                    return "SOC_2_CC6.1/NIST_AC-3 arn:aws:s3:::" + bucket.name();
                                }
                                throw new CompletionException(cause);
                            })).toList();
            return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> checks.stream().map(CompletableFuture::join).filter(value -> value != null).toList());
        });
        CompletableFuture<List<String>> ebsFindings = ec2.describeVolumes().thenApply(result -> result.volumes().stream()
                .filter(volume -> !Boolean.TRUE.equals(volume.encrypted()))
                .map(volume -> "SOC_2_CC6.6/NIST_SC-28 arn:aws:ec2:" + region + ":" + awsAccountId + ":volume/" + volume.volumeId()).toList());
        CompletableFuture<List<String>> rdsFindings = rds.describeDBInstances().thenApply(result -> result.dbInstances().stream()
                .filter(database -> !Boolean.TRUE.equals(database.storageEncrypted()))
                .map(database -> "SOC_2_CC6.6/NIST_SC-28 " + database.dbInstanceArn()).toList());
        CompletableFuture<Boolean> rootMfa = iam.getAccountSummary().thenApply(result ->
                result.summaryMap().getOrDefault("AccountMFAEnabled", 0) > 0);

        return Mono.fromFuture(CompletableFuture.allOf(s3Findings, ebsFindings, rdsFindings, rootMfa)
                .thenApply(ignored -> buildResult(s3Findings.join(), ebsFindings.join(), rdsFindings.join(), rootMfa.join())))
                .flatMap(result -> emitComplianceMetric(result.complianceScorePercentage()).thenReturn(result))
                .doOnSuccess(result -> log.info("Compliance evaluation completed: account={}, framework={}, score={}, violations={}",
                        awsAccountId, result.framework(), result.complianceScorePercentage(), result.violations().size()));
    }

    /** Supports the safe, reversible S3 public-access-block remediation. Other mutations require an approved remediation workflow. */
    public Mono<Void> autoRemediateViolation(String resourceArn, String ruleId) {
        if (resourceArn == null || !resourceArn.matches("arn:aws:s3:::[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
            return Mono.error(new IllegalArgumentException("Only a bucket ARN can be remediated by this operation"));
        }
        if (!"SOC_2_CC6.1".equals(ruleId) && !"NIST_AC-3".equals(ruleId)) {
            return Mono.error(new IllegalArgumentException("Unsupported remediation rule"));
        }
        String bucket = resourceArn.substring("arn:aws:s3:::".length());
        var block = PublicAccessBlockConfiguration.builder().blockPublicAcls(true).ignorePublicAcls(true)
                .blockPublicPolicy(true).restrictPublicBuckets(true).build();
        return Mono.fromFuture(s3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                .bucket(bucket).publicAccessBlockConfiguration(block).build())).then()
                .doOnSuccess(ignored -> log.warn("Applied S3 public access block remediation: bucket={}", bucket));
    }

    private ComplianceEvaluationResult buildResult(List<String> s3Findings, List<String> ebsFindings,
            List<String> rdsFindings, boolean rootMfa) {
        List<String> passed = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        collect("SOC_2_CC6.1/NIST_AC-3", s3Findings, passed, violations);
        collect("SOC_2_CC6.6/NIST_SC-28 (EBS)", ebsFindings, passed, violations);
        collect("SOC_2_CC6.6/NIST_SC-28 (RDS)", rdsFindings, passed, violations);
        if (rootMfa) passed.add("SOC_2_CC6.2/NIST_IA-2 (root account MFA)");
        else violations.add("SOC_2_CC6.2/NIST_IA-2 root account MFA is not enabled");
        double score = passed.size() * 100.0d / 4.0d;
        return new ComplianceEvaluationResult("SOC_2", score, passed, violations, Instant.now());
    }

    private static void collect(String rule, List<String> findings, List<String> passed, List<String> violations) {
        if (findings.isEmpty()) passed.add(rule + " compliant");
        else violations.addAll(findings);
    }
    private Mono<Void> emitComplianceMetric(double score) {
        return Mono.fromFuture(cloudWatch.putMetricData(PutMetricDataRequest.builder().namespace("GreenOps/Enterprise")
                .metricData(MetricDatum.builder().metricName("greenops.enterprise.compliance_score")
                        .unit(StandardUnit.PERCENT).value(score).timestamp(Instant.now()).build()).build()))
                .then().onErrorResume(error -> { log.warn("Could not emit compliance metric: {}", error.getClass().getSimpleName()); return Mono.empty(); });
    }
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) current = current.getCause();
        return current;
    }
}
