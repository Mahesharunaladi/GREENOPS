package com.greenops.workflow.spire;

import com.greenops.model.SpireEvaluationResult;
import com.greenops.model.SpireReadiness;
import com.greenops.model.WorkloadTelemetry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Reactive workload-attestation endpoint. */
@RestController
@RequestMapping("/api/v1/spire")
public final class SpireAttestationController {
    private final SpireWorkloadWatcher watcher;

    public SpireAttestationController(SpireWorkloadWatcher watcher) {
        this.watcher = watcher;
    }

    @PostMapping(value = "/attest", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SpireEvaluationResult> attest(@Valid @RequestBody WorkloadTelemetry telemetry) {
        return watcher.evaluate(telemetry);
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public SpireReadiness status() {
        return watcher.readiness();
    }
}
