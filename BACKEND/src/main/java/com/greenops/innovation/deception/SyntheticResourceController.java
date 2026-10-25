package com.greenops.innovation.deception;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Isolated synthetic responses. Never connect these handlers to production data adapters. */
@RestController
@RequestMapping("/__deception")
public final class SyntheticResourceController {
    @GetMapping({"/**", ""})
    public Map<String, Object> syntheticRecords() {
        return Map.of("records", java.util.List.of(
                Map.of("id", "demo-1042", "name", "Synthetic Account", "status", "inactive"),
                Map.of("id", "demo-2087", "name", "Example Resource", "status", "pending")),
                "source", "synthetic", "requestId", java.util.UUID.randomUUID().toString());
    }
}
