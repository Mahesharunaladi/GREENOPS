package com.greenops.model;

import java.time.Instant;

/** Synthetic honey credential. The access key format is deliberately invalid for AWS. */
public record CanaryToken(
        String tokenId,
        String fakeAccessKey,
        String associatedSessionId,
        String honeyResourceType,
        Instant createdAt,
        String callbackUrl) {}
