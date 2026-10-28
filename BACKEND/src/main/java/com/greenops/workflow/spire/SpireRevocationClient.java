package com.greenops.workflow.spire;

import reactor.core.publisher.Mono;

/** Adapter for the deployment's SPIRE Server API/gRPC control plane. */
public interface SpireRevocationClient {
    Mono<Boolean> revokeSvid(String spiffeId, String reason);

    default boolean isConfigured() { return false; }
}
