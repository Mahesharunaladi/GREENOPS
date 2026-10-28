package com.greenops.workflow.spire;

import java.util.OptionalDouble;
import reactor.core.publisher.Mono;

/** Bridges the current session's trusted score from the existing ZeroTrustAgent pipeline. */
public interface BiometricSignalProvider {
    Mono<OptionalDouble> confidence(String sessionId, String userId);

    default boolean isConfigured() { return false; }
}
