package com.greenops.scrapper.telemetry;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** Bounded local telemetry store for development and single-node deployments. */
@Service
public final class TelemetryBatchStore {

    private static final int MAX_BATCHES = 2_000;
    private final Deque<StoredBatch> batches = new ArrayDeque<>();
    private final AtomicLong acceptedCount = new AtomicLong();

    public synchronized StoredBatch append(TelemetryBatch batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        StoredBatch stored = new StoredBatch(batch, Instant.now());
        if (batches.size() >= MAX_BATCHES) {
            batches.removeFirst();
        }
        batches.addLast(stored);
        acceptedCount.incrementAndGet();
        return stored;
    }

    public synchronized TelemetryStatus status() {
        StoredBatch latest = batches.peekLast();
        return new TelemetryStatus(acceptedCount.get(), batches.size(), latest == null ? null : latest.receivedAt(), latest == null ? null : latest.batch().seqId());
    }

    public record StoredBatch(TelemetryBatch batch, Instant receivedAt) {}

    public record TelemetryStatus(long acceptedBatches, int retainedBatches, Instant latestReceivedAt, Long latestSequence) {}

    public record TelemetryBatch(
            long seqId,
            long observedAt,
            String sessionId,
            Map<String, Object> environment,
            int keystrokeCount,
            int mouseFeatureCount) {
        public TelemetryBatch {
            sessionId = sessionId == null || sessionId.isBlank() ? "unknown" : sessionId;
            environment = environment == null ? Map.of() : Map.copyOf(environment);
            if (seqId < 0 || observedAt < 0 || keystrokeCount < 0 || mouseFeatureCount < 0) {
                throw new IllegalArgumentException("telemetry counters must be non-negative");
            }
        }
    }
}
