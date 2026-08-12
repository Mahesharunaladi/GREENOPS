package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;

public interface CostRateProvider {
    double hourlyRate(ResourceMetrics metrics);
}
