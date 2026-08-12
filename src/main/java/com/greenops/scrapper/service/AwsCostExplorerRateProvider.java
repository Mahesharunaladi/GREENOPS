package com.greenops.scrapper.service;

import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.DimensionValues;
import com.amazonaws.services.costexplorer.model.Expression;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageRequest;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageResult;
import com.amazonaws.services.costexplorer.model.Granularity;
import com.amazonaws.services.costexplorer.model.GroupDefinition;
import com.amazonaws.services.costexplorer.model.ResultByTime;
import com.amazonaws.services.costexplorer.model.TimePeriod;
import com.greenops.scrapper.config.GreenOpsProperties;
import com.greenops.scrapper.model.ResourceMetrics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AwsCostExplorerRateProvider implements CostRateProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsCostExplorerRateProvider.class);
    private final GreenOpsProperties properties;
    private final FallbackCostRateProvider fallback;

    public AwsCostExplorerRateProvider(GreenOpsProperties properties, FallbackCostRateProvider fallback) {
        this.properties = properties;
        this.fallback = fallback;
    }

    @Override
    public double hourlyRate(ResourceMetrics metrics) {
        try {
            AWSCostExplorer client = AWSCostExplorerClientBuilder.standard()
                    .withRegion(properties.getAws().getRegion())
                    .build();
            GetCostAndUsageRequest request = new GetCostAndUsageRequest()
                    .withTimePeriod(new TimePeriod()
                            .withStart(LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE))
                            .withEnd(LocalDate.now().format(DateTimeFormatter.ISO_DATE)))
                    .withGranularity(Granularity.DAILY)
                    .withMetrics("UnblendedCost")
                    .withGroupBy(List.of(new GroupDefinition().withType("DIMENSION").withKey("INSTANCE_TYPE")))
                    .withFilter(new Expression().withAnd(
                            new Expression().withDimensions(new DimensionValues().withKey("REGION").withValues(metrics.region())),
                            new Expression().withDimensions(new DimensionValues().withKey("INSTANCE_TYPE").withValues(metrics.instanceType()))));
            GetCostAndUsageResult result = client.getCostAndUsage(request);
            double dailyCost = result.getResultsByTime().stream()
                    .mapToDouble(this::extractDailyCost)
                    .sum();
            if (dailyCost > 0.0) {
                return dailyCost / 24.0;
            }
        } catch (Exception ex) {
            log.debug("Falling back to local cost rate for {}: {}", metrics.resourceId(), ex.getMessage());
        }
        return fallback.hourlyRate(metrics);
    }

    private double extractDailyCost(ResultByTime result) {
        String amount = result.getTotal() == null || result.getTotal().get("UnblendedCost") == null
                ? "0"
                : result.getTotal().get("UnblendedCost").getAmount();
        return new BigDecimal(amount).doubleValue();
    }
}
