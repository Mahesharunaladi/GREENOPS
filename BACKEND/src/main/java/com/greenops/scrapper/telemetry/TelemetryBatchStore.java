package com.greenops.scrapper.telemetry;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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
        TelemetryBatch batch = latest == null ? null : latest.batch();
        return new TelemetryStatus(
            acceptedCount.get(),
            batches.size(),
            latest == null ? null : latest.receivedAt(),
            batch == null ? null : batch.seqId(),
            batch == null ? 0.0d : batch.dwellMeanMs(),
            batch == null ? 0.0d : batch.flightMeanMs(),
            batch == null ? 0.0d : batch.mouseVelocityMean(),
            batch == null ? 0.0d : batch.mouseCurvatureMean());
    }

        public synchronized List<TelemetryMetricPoint> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BATCHES));
        return batches.stream()
            .skip(Math.max(0, batches.size() - safeLimit))
            .map(stored -> new TelemetryMetricPoint(
                stored.batch().seqId(),
                stored.receivedAt(),
                stored.batch().keystrokeCount(),
                stored.batch().mouseFeatureCount(),
                stored.batch().dwellMeanMs(),
                stored.batch().flightMeanMs(),
                stored.batch().mouseVelocityMean(),
                stored.batch().mouseCurvatureMean()))
            .toList();
        }

    public record StoredBatch(TelemetryBatch batch, Instant receivedAt) {}

        public record TelemetryMetricPoint(
            long sequence,
            Instant receivedAt,
            int keystrokeCount,
            int mouseFeatureCount,
            double dwellMeanMs,
            double flightMeanMs,
            double mouseVelocityMean,
            double mouseCurvatureMean) {}

        public record TelemetryStatus(
            long acceptedBatches,
            int retainedBatches,
            Instant latestReceivedAt,
            Long latestSequence,
            double latestDwellMeanMs,
            double latestFlightMeanMs,
            double latestMouseVelocityMean,
            double latestMouseCurvatureMean) {}

    public record TelemetryBatch(
            long seqId,
            long observedAt,
            String sessionId,
            Map<String, Object> environment,
            int keystrokeCount,
            int mouseFeatureCount,
            double dwellMeanMs,
            double flightMeanMs,
            double mouseVelocityMean,
            double mouseAccelerationMean,
            double mouseCurvatureMean,
            int clickCount) {
        public TelemetryBatch {
            sessionId = sessionId == null || sessionId.isBlank() ? "unknown" : sessionId;
            environment = environment == null ? Map.of() : Map.copyOf(environment);
                if (seqId < 0 || observedAt < 0 || keystrokeCount < 0 || mouseFeatureCount < 0 || clickCount < 0
                    || !Double.isFinite(dwellMeanMs) || !Double.isFinite(flightMeanMs)
                    || !Double.isFinite(mouseVelocityMean) || !Double.isFinite(mouseAccelerationMean)
                    || !Double.isFinite(mouseCurvatureMean)) {
                throw new IllegalArgumentException("telemetry counters must be non-negative");
            }
        }
    }
}
