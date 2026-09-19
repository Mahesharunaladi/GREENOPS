package com.greenops.workflow.remediation;

import com.greenops.scrapper.model.ScanFinding;
import com.greenops.scrapper.model.ScanResult;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Delegates idle-resource policy matches to the cost optimization action path.
 *
 * <p>The current cost scanner records whether each finding's remediation action was applied or
 * simulated. This component centralizes the orchestration handoff and returns an auditable summary
 * for the Lambda response.
 *
 * <p><strong>Thread safety:</strong> this component is stateless and safe as a singleton bean.
 */
@Component
public final class ActionExecutor {

    /**
     * Summarizes cost remediation work already selected by the scanner.
     *
     * @param scanResult EC2/RDS idle scan result
     * @return action execution summary
     */
    public ActionExecutionResult executeIdlePolicyActions(ScanResult scanResult) {
        Objects.requireNonNull(scanResult, "scanResult must not be null");
        List<ScanFinding> findings = scanResult.findings() == null ? List.of() : scanResult.findings();
        long matched = findings.size();
        long applied = findings.stream()
            .filter(Objects::nonNull)
            .filter(finding -> finding.actionTaken())
            .count();
        return new ActionExecutionResult(
                matched,
                applied,
                scanResult.dryRun(),
                matched > 0,
                matched > 0
                        ? "Idle policy matched; scanner-selected remediation actions evaluated"
                        : "No idle policy matches detected",
                Instant.now());
    }

    /**
     * Summary of idle-resource remediation handoff.
     */
    public record ActionExecutionResult(
            long matchedFindings,
            long appliedActions,
            boolean dryRun,
            boolean delegated,
            String message,
            Instant executedAt) {

        public ActionExecutionResult {
            if (matchedFindings < 0 || appliedActions < 0) {
                throw new IllegalArgumentException("action counts must be non-negative");
            }
            message = message == null || message.isBlank() ? "No action execution details" : message;
            executedAt = executedAt == null ? Instant.now() : executedAt;
        }
    }
}
