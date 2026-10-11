import type { TelemetryBatch, TelemetryTransport, TelemetryTransportOptions } from "./types/telemetry";

export class WebSocketTransport implements TelemetryTransport {
  private socket: WebSocket | null = null;
  private pending: TelemetryBatch[] = [];
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempt = 0;
  private closed = false;
  private readonly maxPending: number;

  constructor(private readonly options: TelemetryTransportOptions) {
    this.maxPending = options.maxPendingBatches ?? 32;
  }

  send(batch: TelemetryBatch): void {
    if (this.closed) return;
    if (this.options.websocketUrl.length === 0) {
      if (this.pending.length >= this.maxPending) this.pending.shift();
      this.pending.push(batch);
      this.flushBeacon();
      return;
    }
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(batch));
      return;
    }
    if (this.pending.length >= this.maxPending) this.pending.shift();
    this.pending.push(batch);
    this.connect();
  }

  private connect(): void {
    if (this.socket !== null || this.closed || this.options.websocketUrl.length === 0) return;
    try {
      const protocols = this.options.sessionToken === undefined
        ? ["greenops.v1"]
        : ["greenops.v1", this.options.sessionToken];
      this.socket = new WebSocket(this.options.websocketUrl, protocols);
      this.socket.onopen = () => {
        this.reconnectAttempt = 0;
        const pending = this.pending.splice(0);
        pending.forEach((batch) => this.socket?.send(JSON.stringify(batch)));
      };
      this.socket.onclose = () => {
        this.socket = null;
        this.scheduleReconnect();
      };
      this.socket.onerror = () => { this.socket?.close(); };
    } catch {
      this.flushBeacon();
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.closed || this.reconnectTimer !== null || this.pending.length === 0) return;
    const delay = Math.min(10_000, 250 * (2 ** this.reconnectAttempt));
    this.reconnectAttempt += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  private flushBeacon(): void {
    if (this.pending.length === 0 || this.options.beaconUrl.length === 0) return;
    const payload = JSON.stringify(this.pending.splice(0));
    if (typeof navigator.sendBeacon === "function") {
      navigator.sendBeacon(this.options.beaconUrl, new Blob([payload], { type: "application/json" }));
    } else {
      void fetch(this.options.beaconUrl, { method: "POST", body: payload, keepalive: true, headers: { "content-type": "application/json", ...(this.options.sessionToken === undefined ? {} : { authorization: `Bearer ${this.options.sessionToken}` }) } });
    }
  }

  flush(): void { this.flushBeacon(); }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer !== null) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    this.socket?.close();
    this.socket = null;
    this.pending = [];
  }
}
