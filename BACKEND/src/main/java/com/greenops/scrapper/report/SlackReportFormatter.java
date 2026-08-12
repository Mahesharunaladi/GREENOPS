package com.greenops.scrapper.report;

import com.greenops.scrapper.model.ScanFinding;
import com.greenops.scrapper.model.ScanResult;
import java.text.DecimalFormat;
import java.util.stream.Collectors;

public final class SlackReportFormatter {

    private static final DecimalFormat SAVINGS = new DecimalFormat("0.00");

    private SlackReportFormatter() {
    }

    public static String toSlackMarkdown(ScanResult result) {
        String header = ":seedling: *GreenOps scan complete*\n"
                + "Resources checked: *" + result.totalResources() + "*\n"
                + "Idle resources: *" + result.idleResources() + "*\n"
                + "Estimated monthly savings: *$" + SAVINGS.format(result.totalEstimatedMonthlySavings()) + "*\n";

        String findings = result.findings().isEmpty()
                ? "No idle resources met the current policy."
                : result.findings().stream().map(SlackReportFormatter::renderFinding).collect(Collectors.joining("\n"));

        return header + "\n" + findings;
    }

    private static String renderFinding(ScanFinding finding) {
        return "• `" + finding.resourceId() + "` [" + finding.action() + "] - " + finding.reason()
                + " (~$" + SAVINGS.format(finding.savingsEstimate().estimatedMonthlySavings()) + "/mo)";
    }
}
