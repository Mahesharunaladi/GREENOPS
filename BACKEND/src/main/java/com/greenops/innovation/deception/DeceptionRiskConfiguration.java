package com.greenops.innovation.deception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DeceptionRiskConfiguration {
    @Bean @ConditionalOnMissingBean(HoneypotWebFilter.RiskSignalResolver.class)
    HoneypotWebFilter.RiskSignalResolver defaultRiskSignalResolver() {
        return (HttpServletRequest request) -> {
            return 0d; // safe default: wire an authenticated server-side session risk lookup to enable routing.
        };
    }
}
