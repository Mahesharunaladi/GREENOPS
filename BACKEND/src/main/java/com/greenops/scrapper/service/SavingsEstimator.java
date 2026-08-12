package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;
import com.greenops.scrapper.model.SavingsEstimate;
import org.springframework.stereotype.Service;

@Service
public class SavingsEstimator {

    private final CostRateProvider costRateProvider;

    public SavingsEstimator(CostRateProvider costRateProvider) {
        this.costRateProvider = costRateProvider;
    }

    public SavingsEstimate estimate(ResourceMetrics metrics) {
        double hourlyRate = Math.max(0.0, costRateProvider.hourlyRate(metrics));
        double monthlySavings = hourlyRate * 24.0 * 30.0;
        return new SavingsEstimate("USD", hourlyRate, monthlySavings);
    }
}
