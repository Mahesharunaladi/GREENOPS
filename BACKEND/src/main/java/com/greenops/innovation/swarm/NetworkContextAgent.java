package com.greenops.innovation.swarm;

import com.greenops.workflow.telemetry.TelemetryEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Context signals only; IP reputation and BGP feeds are supplied by a trusted enrichment service. */
@Component
public final class NetworkContextAgent {
    public RiskAssessment assess(TelemetryEvent event, NetworkSignals signals) {
        var device = event.deviceContext();
        double score = (device.automationSuspected() ? .45 : 0)
                + ("unknown".equals(device.tlsJa3Fingerprint()) ? .2 : 0)
                + signals.ipReputationRisk() * .2 + signals.bgpAnomalyRisk() * .15;
        return new RiskAssessment("network", Math.min(1, score), .30,
                Map.of("ipScope", device.ipScope(), "tlsFingerprintPresent", Boolean.toString(!"unknown".equals(device.tlsJa3Fingerprint()))));
    }
    public record NetworkSignals(double ipReputationRisk, double bgpAnomalyRisk) {
        public NetworkSignals { check(ipReputationRisk); check(bgpAnomalyRisk); }
        private static void check(double value) { if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("risk must be in [0,1]"); }
    }
}
