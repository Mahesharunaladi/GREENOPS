package com.greenops.scrapper.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "name", "GreenOps Cost-Optimizer",
                "status", "UP",
                "endpoints", Map.of(
                        "scan", "/api/v1/scan",
                        "audits", "/api/v1/audits",
                        "health", "/actuator/health"));
    }
}