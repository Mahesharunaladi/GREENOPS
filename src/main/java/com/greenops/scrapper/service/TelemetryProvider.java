package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;
import java.util.List;

public interface TelemetryProvider {
    List<ResourceMetrics> snapshot();
}
