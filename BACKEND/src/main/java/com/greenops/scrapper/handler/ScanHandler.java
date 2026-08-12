package com.greenops.scrapper.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenops.scrapper.GreenOpsScrapperApplication;
import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.service.ScanOrchestrator;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class ScanHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static volatile ConfigurableApplicationContext applicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        ScanOrchestrator orchestrator = applicationContext().getBean(ScanOrchestrator.class);
        boolean dryRun = Boolean.parseBoolean(String.valueOf(input.getOrDefault("dryRun", "true")));
        ScanResult result = orchestrator.runScan(dryRun);
        try {
            return Map.of(
                    "statusCode", 200,
                    "body", objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize scan result", ex);
        }
    }

    private ConfigurableApplicationContext applicationContext() {
        if (applicationContext == null) {
            synchronized (ScanHandler.class) {
                if (applicationContext == null) {
                    applicationContext = new SpringApplicationBuilder(GreenOpsScrapperApplication.class)
                            .properties(Map.of("spring.main.web-application-type", "none"))
                            .run();
                }
            }
        }
        return applicationContext;
    }
}
