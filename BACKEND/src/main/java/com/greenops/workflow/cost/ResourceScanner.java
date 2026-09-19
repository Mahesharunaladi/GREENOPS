package com.greenops.workflow.cost;

import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.service.ScanOrchestrator;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Workflow adapter for EC2/RDS cost optimization scans.
 *
 * <p>The existing {@link ScanOrchestrator} owns resource telemetry collection, idle policy
 * evaluation, savings estimation, audit persistence, and cost-scan Slack reporting. This adapter
 * gives the central workflow a stable, domain-named port for concurrent Lambda orchestration.
 *
 * <p><strong>Thread safety:</strong> this component is stateless and delegates to Spring-managed
 * collaborators.
 */
@Component
public final class ResourceScanner {

    private final ScanOrchestrator scanOrchestrator;

    public ResourceScanner(ScanOrchestrator scanOrchestrator) {
        this.scanOrchestrator = Objects.requireNonNull(scanOrchestrator, "scanOrchestrator must not be null");
    }

    /**
     * Runs the EC2/RDS idle-resource cost scan.
     *
     * @param dryRun whether remediation actions should be simulated
     * @return cost scan result with idle findings and savings estimates
     */
    public ScanResult scan(boolean dryRun) {
        return scanOrchestrator.runScan(dryRun);
    }
}
