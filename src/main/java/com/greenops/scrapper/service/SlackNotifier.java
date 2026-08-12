package com.greenops.scrapper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenops.scrapper.config.GreenOpsProperties;
import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.report.SlackReportFormatter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final GreenOpsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SlackNotifier(GreenOpsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String postReport(ScanResult result) {
        String message = SlackReportFormatter.toSlackMarkdown(result);
        String webhookUrl = properties.getSlack().getWebhookUrl();
        if (properties.isDryRun() || webhookUrl == null || webhookUrl.isBlank()) {
            log.info("Dry-run or missing webhook; Slack payload: {}", message);
            return message;
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of("text", message));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Slack webhook returned HTTP " + response.statusCode());
            }
            return message;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to post Slack report", ex);
        }
    }
}
