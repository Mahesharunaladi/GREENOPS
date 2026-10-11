import { KeystrokeHarvester } from "./harvesters/keystroke";
import { MouseHarvester } from "./harvesters/mouse";
import { WebSocketTransport } from "./transport";
import type {
  ClientEnvironment,
  RiskEvent,
  TelemetryFeature,
  TelemetryTransportOptions,
  TelemetryWorkerResponse
} from "./types/telemetry";

export interface GreenopsClientOptions {
  sessionId: string;
  websocketUrl: string;
  beaconUrl: string;
  sessionToken?: string;
  enabled?: boolean;
}

export type RiskAction = "NONE" | "STEP_UP" | "LOCKOUT";

const browserFamily = (): string => {
  const agent = navigator.userAgent;
  if (agent.includes("Chrome")) return "Chrome";
  if (agent.includes("Safari")) return "Safari";
  if (agent.includes("Firefox")) return "Firefox";
  return "Other";
};

const webglRenderer = (): string | null => {
  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("webgl") ?? canvas.getContext("experimental-webgl");
    if (context === null || !("getExtension" in context)) return null;
    const debugInfo = context.getExtension("WEBGL_debug_renderer_info");
    return debugInfo === null ? null : String(context.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL));
  } catch {
    return null;
  }
};

export const collectEnvironment = (): ClientEnvironment => ({
  userAgentFamily: browserFamily(),
  webglRenderer: webglRenderer(),
  localeOffsetMinutes: new Date().getTimezoneOffset(),
  language: navigator.language,
  screen: { width: window.innerWidth, height: window.innerHeight, devicePixelRatio: window.devicePixelRatio }
});

export class GreenopsClient {
  private readonly keys = new KeystrokeHarvester();
  private readonly mouse = new MouseHarvester();
  private readonly transport: WebSocketTransport;
  private readonly worker: Worker;
  private drainTimer: ReturnType<typeof setInterval> | null = null;
  private destroyed = false;
  private riskHandler: ((event: RiskEvent) => void) | null = null;

  constructor(private readonly options: GreenopsClientOptions) {
    const transportOptions: TelemetryTransportOptions = {
      websocketUrl: options.websocketUrl,
      beaconUrl: options.beaconUrl,
      sessionToken: options.sessionToken
    };
    this.transport = new WebSocketTransport(transportOptions);
    this.worker = new Worker(new URL("./worker/telemetry.worker.ts", import.meta.url), { type: "module" });
    this.worker.onmessage = (message: MessageEvent<TelemetryWorkerResponse>): void => {
      if (message.data.type === "batch") this.transport.send(message.data.batch);
    };
    this.worker.postMessage({ type: "configure", sessionId: options.sessionId, maxBatches: 32, flushIntervalMs: 150, environment: collectEnvironment() });
    if (options.enabled !== false) this.start();
    window.addEventListener("pagehide", this.flush, { passive: true });
  }

  start(): void {
    if (this.destroyed || this.drainTimer !== null) return;
    this.keys.start();
    this.mouse.start();
    this.drainTimer = setInterval(() => this.drain(), 50);
  }

  stop(): void {
    this.keys.stop();
    this.mouse.stop();
    if (this.drainTimer !== null) clearInterval(this.drainTimer);
    this.drainTimer = null;
    this.drain();
  }

  private drain(): void {
    const features: TelemetryFeature[] = [...this.keys.drain(), ...this.mouse.drain()];
    features.forEach((feature) => this.worker.postMessage({ type: "feature", feature }));
  }

  private readonly flush = (): void => {
    if (this.destroyed) return;
    this.drain();
    this.worker.postMessage({ type: "flush" });
    this.transport.flush();
  };

  onRiskChange(handler: (event: RiskEvent) => void): () => void {
    this.riskHandler = handler;
    return () => { if (this.riskHandler === handler) this.riskHandler = null; };
  }

  handleRiskEvent(event: RiskEvent): RiskAction {
    this.riskHandler?.(event);
    if (event.score >= 0.75) return "LOCKOUT";
    if (event.score >= 0.4) return "STEP_UP";
    return "NONE";
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    window.removeEventListener("pagehide", this.flush);
    this.stop();
    this.worker.postMessage({ type: "terminate" });
    this.worker.terminate();
    this.transport.close();
    this.riskHandler = null;
  }
}
