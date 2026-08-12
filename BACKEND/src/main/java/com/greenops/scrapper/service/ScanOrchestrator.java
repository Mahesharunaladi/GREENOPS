package com.greenops.scrapper.service;

import com.greenops.scrapper.config.GreenOpsProperties;
import com.greenops.scrapper.model.RemediationAction;
import com.greenops.scrapper.model.ResourceMetrics;
import com.greenops.scrapper.model.ScanFinding;
import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.model.SavingsEstimate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScanOrchestrator {

    private final TelemetryProvider telemetryProvider;
    private final SavingsEstimator savingsEstimator;
    private final SlackNotifier slackNotifier;
    private final AuditLogRepository auditLogRepository;
    private final GreenOpsProperties properties;

    public ScanOrchestrator(TelemetryProvider telemetryProvider,
                            SavingsEstimator savingsEstimator,
                            SlackNotifier slackNotifier,
                            AuditLogRepository auditLogRepository,
                            GreenOpsProperties properties) {
        this.telemetryProvider = telemetryProvider;
        this.savingsEstimator = savingsEstimator;
        this.slackNotifier = slackNotifier;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    public ScanResult runScan() {
        return runScan(properties.isDryRun());
    }

    public ScanResult runScan(boolean dryRunOverride) {
        IdleDetector detector = new IdleDetector(
                properties.getPolicy().getIdleCpuThreshold(),
                properties.getPolicy().getIdleNetworkThresholdKbps(),
                properties.getPolicy().getRequiredTagKey(),
                properties.getPolicy().getRequiredTagValue(),
                properties.getPolicy().getAutostopTagKey(),
                properties.getPolicy().getAutostopTagValue());

        List<ResourceMetrics> resources = telemetryProvider.snapshot();
        List<ScanFinding> findings = new ArrayList<>();
        double totalSavings = 0.0;
        boolean effectiveDryRun = properties.isDryRun() || dryRunOverride;

        for (ResourceMetrics metrics : resources) {
            if (!detector.isIdle(metrics)) {
                continue;
            }
            SavingsEstimate estimate = savingsEstimator.estimate(metrics);
            RemediationAction action = determineAction(metrics);
            boolean actionTaken = !effectiveDryRun && action != RemediationAction.NONE;
            findings.add(new ScanFinding(
                    metrics.resourceId(),
                    metrics.region(),
                    buildReason(metrics),
                    action,
                    actionTaken,
                    estimate,
                    metrics.tags()));
            totalSavings += estimate.estimatedMonthlySavings();
        }

        ScanResult result = new ScanResult(
                Instant.now(),
                effectiveDryRun,
                resources.size(),
                findings.size(),
                totalSavings,
                List.copyOf(findings),
                null);

        String slackSummary = slackNotifier.postReport(result);
        ScanResult finalResult = new ScanResult(
                result.scannedAt(),
                result.dryRun(),
                result.totalResources(),
                result.idleResources(),
                result.totalEstimatedMonthlySavings(),
                result.findings(),
                slackSummary);

        auditLogRepository.save(finalResult);
        return finalResult;
    }

    private String buildReason(ResourceMetrics metrics) {
        return "CPU " + metrics.cpuUtilization() + "% and network " + metrics.networkInboundKbps()
                + " KB/s are below policy thresholds";
    }

    private RemediationAction determineAction(ResourceMetrics metrics) {
        String resourceType = metrics.resourceType().toLowerCase();
        if (resourceType.contains("ec2")) {
            return RemediationAction.STOP;
        }
        if (resourceType.contains("rds") || resourceType.contains("db")) {
            return RemediationAction.STOP;
        }
        return RemediationAction.NONE;
    }
}
