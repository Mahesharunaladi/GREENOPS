package com.greenops.workflow.deception;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated read-only endpoint for the console's recent deception activity panel. */
@RestController
@RequestMapping("/api/v1/sandbox/events")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DeceptionEventsController {
    private final FingerprintRegistry registry;

    public DeceptionEventsController(FingerprintRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<DeceptionEventView> recent(@RequestParam(defaultValue = "100") int limit) {
        return registry.recentActions(Math.max(0, Math.min(200, limit))).stream().map(action -> {
            var fingerprint = registry.fingerprint(action.compositeHash());
            return new DeceptionEventView(action.logId(), action.compositeHash(),
                    fingerprint.map(value -> value.ipAddress()).orElse("unavailable"), action.attemptedEndpoint(),
                    action.httpMethod(), action.payloadCaptured(), action.timestamp(),
                    fingerprint.map(value -> value.violationCount()).orElse(0));
        }).toList();
    }
}
