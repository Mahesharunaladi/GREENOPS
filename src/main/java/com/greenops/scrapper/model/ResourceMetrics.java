package com.greenops.scrapper.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ResourceMetrics(
        String resourceId,
        String resourceType,
        String region,
        String instanceType,
        double cpuUtilization,
        double networkInboundKbps,
        Map<String, String> tags,
        Instant observedAt) {

    public ResourceMetrics {
        tags = tags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        observedAt = observedAt == null ? Instant.now() : observedAt;
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(instanceType, "instanceType must not be null");
    }
}
