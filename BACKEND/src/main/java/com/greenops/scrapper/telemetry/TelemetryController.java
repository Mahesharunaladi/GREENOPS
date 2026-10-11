package com.greenops.scrapper.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public TelemetryController(TelemetryBatchStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"", "/beacon"})
    public ResponseEntity<IngestResponse> ingest(@RequestBody String rawPayload) {
        final JsonNode payload;
        try {
            payload = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("telemetry payload must be valid JSON", ex);
        }
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
                node.path("mouse").isArray() ? node.path("mouse").size() : 0,
                average(node.path("keystrokes"), "dwellMs"),
                average(node.path("keystrokes"), "flightMs"),
                average(node.path("mouse"), "velocity"),
                average(node.path("mouse"), "acceleration"),
                average(node.path("mouse"), "curvature"),
                countPositive(node.path("mouse"), "clickDwellMs"));
    }

    private double average(JsonNode values, String field) {
        if (!values.isArray()) return 0.0d;
        double total = 0.0d;
        int count = 0;
        for (JsonNode value : values) {
            JsonNode item = value.get(field);
            if (item != null && item.isNumber()) { total += item.asDouble(); count++; }
        }
        return count == 0 ? 0.0d : total / count;
    }

    private int countPositive(JsonNode values, String field) {
        if (!values.isArray()) return 0;
        int count = 0;
        for (JsonNode value : values) {
            JsonNode item = value.get(field);
            if (item != null && item.isNumber() && item.asDouble() >= 0.0d) count++;
        }
        return count;
    }

    private List<JsonNode> iterable(JsonNode array) {
        List<JsonNode> entries = new ArrayList<>();
        array.forEach(entries::add);
        return entries;
    }

    public record IngestResponse(int acceptedBatches, TelemetryBatchStore.TelemetryStatus status) {}
}
