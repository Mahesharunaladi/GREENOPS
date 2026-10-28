package com.greenops.workflow.spire;

import java.time.Duration;
import reactor.core.publisher.Mono;

/** Uses SPIRE Workload API to issue or renew short-lived SVIDs; this application never signs SVIDs. */
public interface SpireSvidIssuer {
    Mono<Boolean> issueOrRenew(String spiffeId, Duration ttl);

    default boolean isConfigured() { return false; }
}
