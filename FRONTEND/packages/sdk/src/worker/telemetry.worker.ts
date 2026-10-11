import type { TelemetryBatch } from "../types/telemetry";

const queue: TelemetryBatch[] = [];
self.onmessage = (event: MessageEvent<TelemetryBatch | { type: "flush" }>): void => {
  if ("type" in event.data && event.data.type === "flush") {
    const batch = queue.splice(0, queue.length);
    self.postMessage(batch);
    return;
  }
  if (queue.length >= 32) queue.shift();
  if ("seq_id" in event.data) queue.push(event.data);
};
