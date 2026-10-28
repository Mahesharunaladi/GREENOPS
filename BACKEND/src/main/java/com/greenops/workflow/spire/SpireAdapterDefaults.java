package com.greenops.workflow.spire;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

/** Explicit development-safe defaults. Replace these adapters with SPIRE/DynamoDB/session integrations in production. */
@Configuration
public class SpireAdapterDefaults {
    private static final Logger log = LoggerFactory.getLogger(SpireAdapterDefaults.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CloudWatchClient.class)
    CloudWatchClient spireCloudWatchClient(@Value("${AWS_REGION:us-east-1}") String region) {
        return CloudWatchClient.builder().region(Region.of(region)).build();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessBaselineStore.class)
    ProcessBaselineStore processBaselineStore(
            @Value("${greenops.spire.baseline.allowed-binaries:}") String configured,
            @Value("${greenops.spire.baseline.user-profiles:}") String userProfiles) {
        Set<String> allowed = Arrays.stream(configured.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        Map<String, Set<String>> configuredProfiles = new HashMap<>();
        for (String profile : userProfiles.split(";")) {
            int separator = profile.indexOf('=');
            if (separator <= 0) continue;
            Set<String> binaries = Arrays.stream(profile.substring(separator + 1).split("\\|"))
                    .map(String::trim).filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            configuredProfiles.put(profile.substring(0, separator).trim(), binaries);
        }
        Map<String, Set<String>> cache = new ConcurrentHashMap<>();
        Map<String, LongAdder> observations = new ConcurrentHashMap<>();
        return new ProcessBaselineStore() {
            @Override
            public Set<String> authorizedBinaries(String userId) {
                return cache.computeIfAbsent(userId, id -> configuredProfiles.getOrDefault(id, allowed));
            }

            @Override
            public long historicalExecutions(String userId, String binaryName) {
                LongAdder count = observations.get(userId + "\u0000" + binaryName);
                return count == null ? 0 : count.sum();
            }

            @Override
            public void recordAuthorizedExecution(String userId, String binaryName) {
                if (authorizedBinaries(userId).contains(binaryName)) {
                    observations.computeIfAbsent(userId + "\u0000" + binaryName, ignored -> new LongAdder()).increment();
                }
            }

            @Override
            public boolean isConfigured() {
                return !allowed.isEmpty() || !configuredProfiles.isEmpty();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(BiometricSignalProvider.class)
    BiometricSignalProvider unavailableBiometricSignalProvider() {
        return (sessionId, userId) -> Mono.just(java.util.OptionalDouble.empty());
    }

    @Bean
    @ConditionalOnMissingBean(SpireRevocationClient.class)
    SpireRevocationClient disabledSpireRevocationClient() {
        return (spiffeId, reason) -> {
            log.error("SPIRE revocation requested but no SPIRE Server adapter is configured for {}", spiffeId);
            return Mono.just(false);
        };
    }

    @Bean
    @ConditionalOnMissingBean(SpireSvidIssuer.class)
    SpireSvidIssuer disabledSpireSvidIssuer() {
        return (spiffeId, ttl) -> Mono.just(false);
    }

    @Bean
    @ConditionalOnMissingBean(IamPrincipalResolver.class)
    IamPrincipalResolver unmappedIamPrincipalResolver() {
        return (sessionId, userId) -> java.util.Optional.empty();
    }
}
