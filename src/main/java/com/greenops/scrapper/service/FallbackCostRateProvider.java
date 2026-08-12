package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;
import org.springframework.stereotype.Component;

@Component
public class FallbackCostRateProvider implements CostRateProvider {

    @Override
    public double hourlyRate(ResourceMetrics metrics) {
        double baseRate = switch (metrics.instanceType().toLowerCase()) {
            case "t3.micro" -> 0.0104;
            case "t3.small" -> 0.0208;
            case "t3.medium" -> 0.0416;
            case "m5.large", "m6i.large" -> 0.0960;
            case "db.t3.small" -> 0.0340;
            default -> 0.05;
        };
        double regionPremium = switch (metrics.region().toLowerCase()) {
            case "eu-west-1" -> 1.10;
            case "us-west-2" -> 1.05;
            default -> 1.0;
        };
        return baseRate * regionPremium;
    }
}
