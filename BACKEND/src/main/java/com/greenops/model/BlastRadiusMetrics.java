package com.greenops.model;

import java.util.List;

public record BlastRadiusMetrics(
        String userId,
        String compromisedRole,
        int accessibleS3Buckets,
        int accessibleRdsDatabases,
        int accessibleEc2Instances,
        double quantifiedRiskExposureUsd,
        List<String> criticalResourceArns) {
    public BlastRadiusMetrics {
        criticalResourceArns = List.copyOf(criticalResourceArns);
    }
}
