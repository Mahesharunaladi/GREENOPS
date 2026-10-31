package com.greenops.workflow.advanced;

import com.greenops.workflow.deception.UserSessionContext;
import reactor.core.publisher.Mono;

/** Supplies trusted, normalized signals from the identity and device telemetry pipeline. */
public interface PredictiveRiskSignalProvider {
    Mono<RiskSignals> currentSignals(String userId, UserSessionContext session);
    boolean isConfigured();

    record RiskSignals(double networkDrift, double hardwareAttestationDelta, double biometricVarianceVelocity) {
        public RiskSignals {
            requireRatio(networkDrift, "networkDrift");
            requireRatio(hardwareAttestationDelta, "hardwareAttestationDelta");
            requireRatio(biometricVarianceVelocity, "biometricVarianceVelocity");
        }
        private static void requireRatio(double value, String name) {
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
