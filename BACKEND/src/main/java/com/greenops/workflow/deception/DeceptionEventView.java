package com.greenops.workflow.deception;

import java.time.Instant;

/** UI-safe action record enriched with an IP only when a matching local fingerprint exists. */
public record DeceptionEventView(
        String logId,
        String compositeHash,
        String ipAddress,
        String attemptedEndpoint,
        String httpMethod,
        String payloadCaptured,
        Instant timestamp,
        int violationCount) {}
