import type { TelemetryBatch, TelemetryTransport } from "./types/telemetry";

export class WebSocketTransport implements TelemetryTransport {
  private socket: WebSocket | null = null;
  private pending: TelemetryBatch[] = [];
  constructor(private readonly url: string, private readonly beaconUrl: string) {}
  send(batch: TelemetryBatch): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(batch));
      return;
    }
    this.pending.push(batch);
    this.connect();
  }
  private connect(): void {
    if (this.socket !== null) return;
    try {
      this.socket = new WebSocket(this.url);
      this.socket.onopen = () => {
        const pending = this.pending.splice(0);
        pending.forEach((batch) => this.socket?.send(JSON.stringify(batch)));
      };
      this.socket.onclose = () => { this.socket = null; };
      this.socket.onerror = () => { this.socket?.close(); };
    } catch {
      this.flushBeacon();
    }
  }
  private flushBeacon(): void {
    const payload = JSON.stringify(this.pending.splice(0));
    if (navigator.sendBeacon) navigator.sendBeacon(this.beaconUrl, payload);
    else void fetch(this.beaconUrl, { method: "POST", body: payload, keepalive: true, headers: { "content-type": "application/json" } });
  }
  close(): void { this.socket?.close(); this.socket = null; this.pending = []; }
}
