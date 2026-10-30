package com.greenops.workflow.enterprise;

import com.greenops.model.BlastRadiusMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.iam.model.SimulatePrincipalPolicyRequest;
import software.amazon.awssdk.services.rds.RdsAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/** Uses IAM policy simulation against discovered resources; dollar exposure is an explicitly configured estimate. */
@Service
public final class BlastRadiusEngine {
    private static final Logger log = LoggerFactory.getLogger(BlastRadiusEngine.class);
    private static final int MAX_RESOURCES_PER_TYPE = 100;
    private final IamAsyncClient iam;
    private final S3AsyncClient s3;
    private final RdsAsyncClient rds;
    private final Ec2AsyncClient ec2;
    private final String region;
    private final double s3Exposure;
    private final double rdsExposure;
    private final double ec2Exposure;

    public BlastRadiusEngine(IamAsyncClient iam, S3AsyncClient s3, RdsAsyncClient rds, Ec2AsyncClient ec2,
            @Value("${greenops.aws.region:us-east-1}") String region,
            @Value("${greenops.enterprise.risk-exposure.s3-bucket:2500}") double s3Exposure,
            @Value("${greenops.enterprise.risk-exposure.rds-database:15000}") double rdsExposure,
            @Value("${greenops.enterprise.risk-exposure.ec2-instance:5000}") double ec2Exposure) {
        this.iam = iam; this.s3 = s3; this.rds = rds; this.ec2 = ec2; this.region = region;
        this.s3Exposure = nonNegative(s3Exposure); this.rdsExposure = nonNegative(rdsExposure); this.ec2Exposure = nonNegative(ec2Exposure);
    }

    public Mono<BlastRadiusMetrics> calculateBlastRadius(String userId, String iamRoleArn) {
        if (userId == null || userId.isBlank() || userId.length() > 256) return Mono.error(new IllegalArgumentException("userId is required"));
        if (iamRoleArn == null || !iamRoleArn.matches("arn:aws(-[a-z]+)?:iam::\\d{12}:role/[A-Za-z0-9+=,.@_/-]{1,512}")) {
            return Mono.error(new IllegalArgumentException("A valid IAM role ARN is required"));
        }
        String account = iamRoleArn.split(":")[4];
        String roleName = iamRoleArn.substring(iamRoleArn.lastIndexOf('/') + 1);
        var buckets = s3.listBuckets().thenApply(result -> result.buckets().stream().limit(MAX_RESOURCES_PER_TYPE)
                .map(bucket -> "arn:aws:s3:::" + bucket.name()).toList());
        var databases = rds.describeDBInstances().thenApply(result -> result.dbInstances().stream().limit(MAX_RESOURCES_PER_TYPE)
                .map(instance -> instance.dbInstanceArn()).toList());
        var instances = ec2.describeInstances().thenApply(result -> result.reservations().stream().flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.state() == null || !"terminated".equalsIgnoreCase(instance.state().nameAsString()))
                .limit(MAX_RESOURCES_PER_TYPE).map(instance -> "arn:aws:ec2:" + region + ":" + account + ":instance/" + instance.instanceId()).toList());
        // Listing both managed and inline policies records the effective role's attached policy surface before simulation.
        var attached = iam.listAttachedRolePolicies(request -> request.roleName(roleName));
        var inline = iam.listRolePolicies(request -> request.roleName(roleName));
        return Mono.fromFuture(CompletableFuture.allOf(buckets, databases, instances, attached, inline)
                .thenCompose(ignored -> {
                    List<String> s3Arns = buckets.join();
                    List<String> rdsArns = databases.join();
                    List<String> ec2Arns = instances.join();
                    var s3Decision = simulate(iamRoleArn, List.of("s3:ListBucket", "s3:GetObject"),
                            s3Arns.stream().flatMap(arn -> java.util.stream.Stream.of(arn, arn + "/*")).toList());
                    var rdsDecision = simulate(iamRoleArn, List.of("rds:DescribeDBInstances"), List.of("*"));
                    var ec2Decision = simulate(iamRoleArn, List.of("ec2:DescribeInstances"), List.of("*"));
                    return CompletableFuture.allOf(s3Decision, rdsDecision, ec2Decision).thenApply(done -> {
                        var s3Allowed = s3Decision.join().stream().filter(result -> "allowed".equalsIgnoreCase(result.evalDecisionAsString()))
                                .map(result -> result.evalResourceName()).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
                        int bucketCount = (int) s3Arns.stream().filter(arn -> s3Allowed.contains(arn) || s3Allowed.contains(arn + "/*")).count();
                        int rdsCount = hasAllowed(rdsDecision.join()) ? rdsArns.size() : 0;
                        int ec2Count = hasAllowed(ec2Decision.join()) ? ec2Arns.size() : 0;
                        List<String> critical = new ArrayList<>();
                        s3Arns.stream().filter(arn -> s3Allowed.contains(arn) || s3Allowed.contains(arn + "/*")).forEach(critical::add);
                        if (rdsCount > 0) critical.addAll(rdsArns);
                        if (ec2Count > 0) critical.addAll(ec2Arns);
                        double exposure = bucketCount * s3Exposure + rdsCount * rdsExposure + ec2Count * ec2Exposure;
                        log.info("IAM blast-radius simulation completed: userId={}, role={}, managedPolicies={}, inlinePolicies={}, resources={}",
                                userId, iamRoleArn, attached.join().attachedPolicies().size(), inline.join().policyNames().size(), critical.size());
                        return new BlastRadiusMetrics(userId, iamRoleArn, bucketCount, rdsCount, ec2Count, exposure,
                                critical.stream().limit(300).toList());
                    });
                }));
    }

    private CompletableFuture<List<software.amazon.awssdk.services.iam.model.EvaluationResult>> simulate(
            String roleArn, List<String> actions, List<String> resources) {
        List<String> bounded = resources.stream().limit(100).toList();
        if (bounded.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return iam.simulatePrincipalPolicy(SimulatePrincipalPolicyRequest.builder().policySourceArn(roleArn)
                .actionNames(actions).resourceArns(bounded).build()).thenApply(response -> response.evaluationResults());
    }

    private static boolean hasAllowed(List<software.amazon.awssdk.services.iam.model.EvaluationResult> results) {
        return results.stream().anyMatch(result -> "allowed".equalsIgnoreCase(result.evalDecisionAsString()));
    }
    private static double nonNegative(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
