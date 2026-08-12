package com.greenops.scrapper.model;

import java.util.Map;

public record ScanFinding(
        String resourceId,
        String region,
        String reason,
        RemediationAction action,
        boolean actionTaken,
        SavingsEstimate savingsEstimate,
        Map<String, String> tags) {
}
