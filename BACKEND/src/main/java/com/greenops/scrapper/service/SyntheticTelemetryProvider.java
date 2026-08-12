package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ResourceMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SyntheticTelemetryProvider implements TelemetryProvider {

    @Override
    public List<ResourceMetrics> snapshot() {
        return List.of(
                new ResourceMetrics("i-0123456789", "ec2", "us-east-1", "t3.medium", 0.7, 22.0,
                        Map.of("Environment", "dev", "AutoStop", "true", "Owner", "platform"), Instant.now()),
                new ResourceMetrics("i-0idleprod", "ec2", "us-east-1", "m6i.large", 0.4, 12.0,
                        Map.of("Environment", "prod", "AutoStop", "false", "Owner", "payments"), Instant.now()),
                new ResourceMetrics("db-001", "rds", "us-west-2", "db.t3.small", 1.2, 5.0,
                        Map.of("Environment", "dev", "AutoStop", "true", "Owner", "analytics"), Instant.now()),
                new ResourceMetrics("eks-node-01", "ec2", "eu-west-1", "m5.large", 15.0, 480.0,
                        Map.of("Environment", "dev", "AutoStop", "true", "Owner", "ml"), Instant.now())
        );
    }
}
