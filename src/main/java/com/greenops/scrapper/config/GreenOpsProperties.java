package com.greenops.scrapper.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "greenops")
public class GreenOpsProperties {

    private boolean dryRun = true;

    @Valid
    private final Slack slack = new Slack();

    @Valid
    private final Aws aws = new Aws();

    @Valid
    private final Policy policy = new Policy();

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Slack getSlack() {
        return slack;
    }

    public Aws getAws() {
        return aws;
    }

    public Policy getPolicy() {
        return policy;
    }

    public static class Slack {
        @NotBlank
        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class Aws {
        @NotBlank
        private String region = "us-east-1";

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class Policy {
        private double idleCpuThreshold = 2.0;
        private double idleNetworkThresholdKbps = 100.0;
        @NotBlank
        private String requiredTagKey = "Environment";
        @NotBlank
        private String requiredTagValue = "dev";
        @NotBlank
        private String autostopTagKey = "AutoStop";
        @NotBlank
        private String autostopTagValue = "true";

        public double getIdleCpuThreshold() {
            return idleCpuThreshold;
        }

        public void setIdleCpuThreshold(double idleCpuThreshold) {
            this.idleCpuThreshold = idleCpuThreshold;
        }

        public double getIdleNetworkThresholdKbps() {
            return idleNetworkThresholdKbps;
        }

        public void setIdleNetworkThresholdKbps(double idleNetworkThresholdKbps) {
            this.idleNetworkThresholdKbps = idleNetworkThresholdKbps;
        }

        public String getRequiredTagKey() {
            return requiredTagKey;
        }

        public void setRequiredTagKey(String requiredTagKey) {
            this.requiredTagKey = requiredTagKey;
        }

        public String getRequiredTagValue() {
            return requiredTagValue;
        }

        public void setRequiredTagValue(String requiredTagValue) {
            this.requiredTagValue = requiredTagValue;
        }

        public String getAutostopTagKey() {
            return autostopTagKey;
        }

        public void setAutostopTagKey(String autostopTagKey) {
            this.autostopTagKey = autostopTagKey;
        }

        public String getAutostopTagValue() {
            return autostopTagValue;
        }

        public void setAutostopTagValue(String autostopTagValue) {
            this.autostopTagValue = autostopTagValue;
        }
    }
}
