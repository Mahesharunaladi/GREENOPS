package com.greenops.scrapper.model;

import java.time.Instant;
import java.util.List;

public record ScanResult(
        Instant scannedAt,
        boolean dryRun,
        int totalResources,
        int idleResources,
        double totalEstimatedMonthlySavings,
        List<ScanFinding> findings,
        String slackSummary) {
}
