package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;
import java.util.Map;

public class IdleDetector {

    private final double cpuThreshold;
    private final double networkThresholdKbps;
    private final String requiredTagKey;
    private final String requiredTagValue;
    private final String autostopTagKey;
    private final String autostopTagValue;

    public IdleDetector() {
        this(2.0, 100.0, "Environment", "dev", "AutoStop", "true");
    }

    public IdleDetector(double cpuThreshold,
                        double networkThresholdKbps,
                        String requiredTagKey,
                        String requiredTagValue,
                        String autostopTagKey,
                        String autostopTagValue) {
        this.cpuThreshold = cpuThreshold;
        this.networkThresholdKbps = networkThresholdKbps;
        this.requiredTagKey = requiredTagKey;
        this.requiredTagValue = requiredTagValue;
        this.autostopTagKey = autostopTagKey;
        this.autostopTagValue = autostopTagValue;
    }

    public boolean isIdle(ResourceMetrics metrics) {
        return metrics.cpuUtilization() < cpuThreshold
                && metrics.networkInboundKbps() < networkThresholdKbps
                && hasRequiredTag(metrics.tags())
                && hasAutoStopTag(metrics.tags());
    }

    private boolean hasRequiredTag(Map<String, String> tags) {
        String actual = tags.get(requiredTagKey);
        return actual != null && actual.equalsIgnoreCase(requiredTagValue);
    }

    private boolean hasAutoStopTag(Map<String, String> tags) {
        String actual = tags.get(autostopTagKey);
        return actual != null && actual.equalsIgnoreCase(autostopTagValue);
    }
}
