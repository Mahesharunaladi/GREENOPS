package com.greenops.workflow.advanced;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenops.model.PreAttackSnapshot;
import com.greenops.scrapper.config.GreenOpsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.Ipv6Range;
import software.amazon.awssdk.services.ec2.model.PrefixListId;
import software.amazon.awssdk.services.ec2.model.RevokeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Captures and restores only S3 bucket policies and EC2 security-group ingress rules. */
@Service
public final class SelfHealingInfrastructureService {
    private static final Logger log = LoggerFactory.getLogger(SelfHealingInfrastructureService.class);
    private static final int MAX_SNAPSHOTS = 10_000;
    private static final Duration SNAPSHOT_TTL = Duration.ofHours(24);
    private final S3AsyncClient s3;
    private final Ec2AsyncClient ec2;
    private final ObjectMapper mapper;
    private final GreenOpsProperties properties;
    private final String configuredRegion;
    private final Map<String, PreAttackSnapshot> issuedSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> snapshotOrder = new ConcurrentLinkedDeque<>();

    public SelfHealingInfrastructureService(S3AsyncClient s3, Ec2AsyncClient ec2, ObjectMapper mapper,
            GreenOpsProperties properties, @Value("${greenops.aws.region:us-east-1}") String configuredRegion) {
        this.s3 = s3; this.ec2 = ec2; this.mapper = mapper; this.properties = properties; this.configuredRegion = configuredRegion;
    }

    public Mono<PreAttackSnapshot> captureResourceSnapshot(String resourceArn) {
        ResourceRef resource = parse(resourceArn);
        CompletableFuture<String> config = resource.type().equals("S3_BUCKET_POLICY")
                ? captureBucketPolicy(resource.name()) : captureSecurityGroupIngress(resource.name());
        return Mono.fromFuture(config).map(json -> {
            PreAttackSnapshot snapshot = new PreAttackSnapshot(UUID.randomUUID().toString(), resourceArn, json, Instant.now());
            issuedSnapshots.put(snapshot.snapshotId(), snapshot);
            snapshotOrder.addLast(snapshot.snapshotId());
            trimSnapshots();
            log.info("Infrastructure snapshot captured: snapshotId={}, resourceArn={}, type={}", snapshot.snapshotId(), resourceArn, resource.type());
            return snapshot;
        });
    }

    public Mono<Void> rollbackInfrastructure(String resourceArn, PreAttackSnapshot snapshot) {
        if (snapshot == null || resourceArn == null || !resourceArn.equals(snapshot.resourceArn())) {
            return Mono.error(new IllegalArgumentException("Snapshot must match the requested resource ARN"));
        }
        PreAttackSnapshot issued = issuedSnapshots.get(snapshot.snapshotId());
        if (issued == null || !issued.equals(snapshot) || issued.capturedAt().isBefore(Instant.now().minus(SNAPSHOT_TTL))) {
            return Mono.error(new IllegalArgumentException("Snapshot is unknown, expired, or was not issued by this service instance"));
        }
        ResourceRef resource = parse(resourceArn);
        SnapshotData data;
        try { data = mapper.readValue(snapshot.originalConfigJson(), SnapshotData.class); }
        catch (Exception error) { return Mono.error(new IllegalArgumentException("Snapshot configuration is invalid", error)); }
        if (!resource.type().equals(data.resourceType())) return Mono.error(new IllegalArgumentException("Snapshot resource type does not match ARN"));
        if (properties.isDryRun()) {
            log.warn("Infrastructure rollback dry-run: snapshotId={}, resourceArn={}", snapshot.snapshotId(), resourceArn);
            return Mono.empty();
        }
        Mono<Void> restore = resource.type().equals("S3_BUCKET_POLICY")
                ? restoreBucketPolicy(resource.name(), data) : restoreSecurityGroupIngress(resource.name(), data.ingress());
        return restore.doOnSuccess(ignored -> {
            issuedSnapshots.remove(snapshot.snapshotId(), issued);
            log.warn("Infrastructure rollback completed: snapshotId={}, resourceArn={}", snapshot.snapshotId(), resourceArn);
        });
    }

    public Mono<Void> rollbackInfrastructure(String resourceArn, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) return Mono.error(new IllegalArgumentException("snapshotId is required"));
        PreAttackSnapshot snapshot = issuedSnapshots.get(snapshotId);
        if (snapshot == null) return Mono.error(new IllegalArgumentException("Snapshot is unknown or expired"));
        return rollbackInfrastructure(resourceArn, snapshot);
    }

    public boolean isDryRun() { return properties.isDryRun(); }

    private CompletableFuture<String> captureBucketPolicy(String bucket) {
        return s3.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucket).build())
                .thenApply(response -> writeJson(new SnapshotData("S3_BUCKET_POLICY", true, response.policy(), List.of(), "")))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof S3Exception serviceError && serviceError.statusCode() == 404) {
                        return writeJson(new SnapshotData("S3_BUCKET_POLICY", false, "", List.of(), ""));
                    }
                    throw new java.util.concurrent.CompletionException(cause);
                });
    }

    private CompletableFuture<String> captureSecurityGroupIngress(String groupId) {
        return ec2.describeSecurityGroups(DescribeSecurityGroupsRequest.builder().groupIds(groupId).build())
                .thenApply(response -> {
                    if (response.securityGroups().size() != 1) throw new IllegalStateException("Security group was not found");
                    List<PermissionSnapshot> ingress = response.securityGroups().get(0).ipPermissions().stream()
                            .map(this::toSnapshot).toList();
                    return writeJson(new SnapshotData("EC2_SECURITY_GROUP_INGRESS", false, "", ingress, groupId));
                });
    }

    private Mono<Void> restoreBucketPolicy(String bucket, SnapshotData snapshot) {
        if (!snapshot.policyPresent()) {
            return Mono.fromFuture(s3.deleteBucketPolicy(DeleteBucketPolicyRequest.builder().bucket(bucket).build())).then();
        }
        if (snapshot.policy() == null || snapshot.policy().isBlank()) return Mono.error(new IllegalArgumentException("Snapshot has no bucket policy document"));
        return Mono.fromFuture(s3.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(snapshot.policy()).build())).then();
    }

    private Mono<Void> restoreSecurityGroupIngress(String groupId, List<PermissionSnapshot> original) {
        return Mono.fromFuture(ec2.describeSecurityGroups(DescribeSecurityGroupsRequest.builder().groupIds(groupId).build()))
                .flatMap(response -> {
                    if (response.securityGroups().size() != 1) return Mono.error(new IllegalStateException("Security group was not found"));
                    List<IpPermission> current = response.securityGroups().get(0).ipPermissions();
                    List<PermissionSnapshot> currentSnapshots = current.stream().map(this::toSnapshot).toList();
                    List<IpPermission> revokeRules = current.stream()
                            .filter(permission -> !original.contains(toSnapshot(permission))).toList();
                    List<IpPermission> restoreRules = original.stream()
                            .filter(permission -> !currentSnapshots.contains(permission)).map(this::fromSnapshot).toList();
                    Mono<Void> revoke = revokeRules.isEmpty() ? Mono.empty() : Mono.fromFuture(ec2.revokeSecurityGroupIngress(
                            RevokeSecurityGroupIngressRequest.builder().groupId(groupId).ipPermissions(revokeRules).build())).then();
                    Mono<Void> authorize = restoreRules.isEmpty() ? Mono.empty() : Mono.fromFuture(ec2.authorizeSecurityGroupIngress(
                            AuthorizeSecurityGroupIngressRequest.builder().groupId(groupId).ipPermissions(restoreRules).build())).then();
                    return revoke.then(authorize);
                });
    }

    private PermissionSnapshot toSnapshot(IpPermission permission) {
        return new PermissionSnapshot(permission.ipProtocol(), permission.fromPort(), permission.toPort(),
                permission.ipRanges().stream().map(range -> new Ipv4Snapshot(range.cidrIp(), range.description())).toList(),
                permission.ipv6Ranges().stream().map(range -> new Ipv6Snapshot(range.cidrIpv6(), range.description())).toList(),
                permission.prefixListIds().stream().map(prefix -> new PrefixSnapshot(prefix.prefixListId(), prefix.description())).toList(),
                permission.userIdGroupPairs().stream().map(pair -> new GroupSnapshot(pair.groupId(), pair.userId(), pair.vpcId(), pair.description())).toList());
    }

    private IpPermission fromSnapshot(PermissionSnapshot snapshot) {
        return IpPermission.builder().ipProtocol(snapshot.protocol()).fromPort(snapshot.fromPort()).toPort(snapshot.toPort())
                .ipRanges(snapshot.ipv4().stream().map(value -> IpRange.builder().cidrIp(value.cidr()).description(value.description()).build()).toList())
                .ipv6Ranges(snapshot.ipv6().stream().map(value -> Ipv6Range.builder().cidrIpv6(value.cidr()).description(value.description()).build()).toList())
                .prefixListIds(snapshot.prefixes().stream().map(value -> PrefixListId.builder().prefixListId(value.id()).description(value.description()).build()).toList())
                .userIdGroupPairs(snapshot.groups().stream().map(value -> UserIdGroupPair.builder().groupId(value.groupId()).userId(value.userId())
                        .vpcId(value.vpcId()).description(value.description()).build()).toList()).build();
    }

    private ResourceRef parse(String arn) {
        if (arn == null || arn.length() > 1024) throw new IllegalArgumentException("A supported resource ARN is required");
        var bucket = java.util.regex.Pattern.compile("arn:aws(?:-[a-z]+)*:s3:::(?<bucket>[a-z0-9][a-z0-9.-]{1,61}[a-z0-9])").matcher(arn);
        if (bucket.matches()) return new ResourceRef("S3_BUCKET_POLICY", bucket.group("bucket"));
        var group = java.util.regex.Pattern.compile("arn:aws(?:-[a-z]+)*:ec2:(?<region>[a-z0-9-]+):\\d{12}:security-group/(?<id>sg-[a-f0-9]+)").matcher(arn);
        if (group.matches()) {
            if (!configuredRegion.equals(group.group("region"))) throw new IllegalArgumentException("Security group ARN region does not match configured AWS region");
            return new ResourceRef("EC2_SECURITY_GROUP_INGRESS", group.group("id"));
        }
        throw new IllegalArgumentException("Supported ARNs are S3 bucket policies and EC2 security groups");
    }

    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Could not serialize resource snapshot", error); }
    }
    private void trimSnapshots() {
        while (issuedSnapshots.size() > MAX_SNAPSHOTS) {
            String oldest = snapshotOrder.pollFirst();
            if (oldest == null) break;
            issuedSnapshots.remove(oldest);
        }
    }
    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private record ResourceRef(String type, String name) {}
    private record SnapshotData(String resourceType, boolean policyPresent, String policy, List<PermissionSnapshot> ingress, String resourceId) {}
    private record PermissionSnapshot(String protocol, Integer fromPort, Integer toPort, List<Ipv4Snapshot> ipv4,
            List<Ipv6Snapshot> ipv6, List<PrefixSnapshot> prefixes, List<GroupSnapshot> groups) {}
    private record Ipv4Snapshot(String cidr, String description) {}
    private record Ipv6Snapshot(String cidr, String description) {}
    private record PrefixSnapshot(String id, String description) {}
    private record GroupSnapshot(String groupId, String userId, String vpcId, String description) {}
}
