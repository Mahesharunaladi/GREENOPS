package com.greenops.scrapper.service;

import com.greenops.scrapper.config.GreenOpsProperties;
import com.greenops.scrapper.model.ResourceMetrics;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.DimensionValues;
import software.amazon.awssdk.services.costexplorer.model.Expression;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

@Component
@Primary
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
        try (CostExplorerClient client = CostExplorerClient.builder()
                .region(Region.of(properties.getAws().getRegion()))
                .build()) {
        GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder()
                        .start(LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE))
                        .end(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                        .build())
                .granularity(Granularity.DAILY)
                .metrics("UnblendedCost")
                .groupBy(List.of(GroupDefinition.builder()
                        .type(GroupDefinitionType.DIMENSION)
                        .key("INSTANCE_TYPE")
                        .build()))
                .filter(Expression.builder()
                        .and(
                                Expression.builder()
                                        .dimensions(DimensionValues.builder()
                                                .key(Dimension.REGION)
                                                .values(metrics.region())
                                                .build())
                                        .build(),
                                Expression.builder()
                                        .dimensions(DimensionValues.builder()
                                                .key(Dimension.INSTANCE_TYPE)
                                                .values(metrics.instanceType())
                                                .build())
                                        .build())
                        .build())
                .build();
        GetCostAndUsageResponse result = client.getCostAndUsage(request);
        double dailyCost = result.resultsByTime().stream()
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
        String amount = result.total() == null || result.total().get("UnblendedCost") == null
                ? "0"
                : result.total().get("UnblendedCost").amount();
        return new BigDecimal(amount).doubleValue();
}
}
