package com.greenops.workflow.advanced;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class AdvancedRiskDefaults {
    @Bean
    @ConditionalOnMissingBean(PredictiveRiskSignalProvider.class)
    PredictiveRiskSignalProvider disabledPredictiveRiskSignalProvider() {
        return new PredictiveRiskSignalProvider() {
            @Override public Mono<RiskSignals> currentSignals(String userId, com.greenops.workflow.deception.UserSessionContext session) {
                return Mono.error(new IllegalStateException("Trusted network, hardware, and biometric velocity signal adapter is not configured"));
            }
            @Override public boolean isConfigured() { return false; }
        };
    }
}
