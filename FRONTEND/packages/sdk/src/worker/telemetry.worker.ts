import type { ClientEnvironment, TelemetryBatch, TelemetryFeature, TelemetryWorkerMessage, TelemetryWorkerResponse } from "../types/telemetry";

const features: TelemetryFeature[] = [];
let sequence = 0;
let sessionId = "";
let environment: ClientEnvironment | null = null;
let maxBatches = 32;
let timer: ReturnType<typeof setInterval> | null = null;

const flush = (): void => {
  if (features.length === 0 || environment === null) return;
  const snapshot = features.splice(0);
  const batch: TelemetryBatch = {
    seq_id: ++sequence,
    observedAt: Date.now(),
    sessionId,
    environment,
    keystrokes: snapshot.filter((feature): feature is Extract<TelemetryFeature, { category: unknown }> => "category" in feature),
    mouse: snapshot.filter((feature): feature is Extract<TelemetryFeature, { windowMs: unknown }> => "windowMs" in feature)
  };
  const response: TelemetryWorkerResponse = { type: "batch", batch };
  self.postMessage(response);
};

self.onmessage = (event: MessageEvent<TelemetryWorkerMessage>): void => {
  const message = event.data;
  if (message.type === "configure") {
    sessionId = message.sessionId;
    environment = message.environment;
    maxBatches = Math.max(1, message.maxBatches);
    if (timer !== null) clearInterval(timer);
    timer = setInterval(flush, Math.max(50, message.flushIntervalMs));
    self.postMessage({ type: "ready" } satisfies TelemetryWorkerResponse);
  } else if (message.type === "feature") {
    if (features.length >= maxBatches * 16) features.splice(0, 1);
    features.push(message.feature);
  } else if (message.type === "flush") {
    flush();
  } else if (message.type === "terminate") {
    if (timer !== null) clearInterval(timer);
    timer = null;
    features.length = 0;
    self.close();
  }
};
