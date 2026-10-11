package com.greenops.scrapper.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives privacy-reduced SDK batches without persisting raw input or key text. */
@RestController
@RequestMapping("/api/v1/telemetry")
public final class TelemetryController {

    private final TelemetryBatchStore store;

    public TelemetryController(TelemetryBatchStore store) {
        this.store = store;
    }

    @PostMapping({"", "/beacon"})
    public ResponseEntity<IngestResponse> ingest(@RequestBody JsonNode payload) {
        List<JsonNode> entries = payload != null && payload.isArray() ? iterable(payload) : List.of(payload);
        int accepted = 0;
        for (JsonNode entry : entries) {
            if (entry == null || !entry.isObject()) {
                continue;
            }
            store.append(toBatch(entry));
            accepted++;
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IngestResponse(accepted, store.status()));
    }

    @GetMapping("/status")
    public TelemetryBatchStore.TelemetryStatus status() {
        return store.status();
    }

    private TelemetryBatchStore.TelemetryBatch toBatch(JsonNode node) {
        JsonNode environment = node.path("environment");
        return new TelemetryBatchStore.TelemetryBatch(
                node.path("seq_id").asLong(0),
                node.path("observedAt").asLong(0),
                node.path("sessionId").asText("unknown"),
                environment.isObject() ? Map.of("userAgentFamily", environment.path("userAgentFamily").asText("unknown")) : Map.of(),
                node.path("keystrokes").isArray() ? node.path("keystrokes").size() : 0,
                node.path("mouse").isArray() ? node.path("mouse").size() : 0);
    }

    private List<JsonNode> iterable(JsonNode array) {
        List<JsonNode> entries = new ArrayList<>();
        array.forEach(entries::add);
        return entries;
    }

    public record IngestResponse(int acceptedBatches, TelemetryBatchStore.TelemetryStatus status) {}
}
