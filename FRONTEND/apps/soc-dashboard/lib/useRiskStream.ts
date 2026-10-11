"use client";
import { useEffect, useMemo, useState } from "react";
import { isRiskEvent, riskTierForScore, type RiskEvent } from "@greenops/sdk";

export type StreamStatus = "demo" | "connecting" | "live" | "offline";

const sample: RiskEvent = {
  id: "evt-demo", sequence: 1284, timestamp: new Date().toISOString(), score: 0.24, tier: "LOW",
  branches: {
    network: 0.18, biometric: 0.29, variance: 0.041,
    fingerprint: { baselineJa3: "7d9f…a81c", currentJa3: "7d9f…a81c", baselineJa4: "t13d…9b2e", currentJa4: "t13d…9b2e", discrepancies: [] },
    biometricProfile: [
      { metric: "Dwell", baseline: 0.44, current: 0.48 }, { metric: "Flight", baseline: 0.38, current: 0.34 },
      { metric: "Velocity", baseline: 0.62, current: 0.57 }, { metric: "Curvature", baseline: 0.51, current: 0.49 }, { metric: "Variance", baseline: 0.29, current: 0.35 }
    ]
  }, auditId: "audit-demo"
};

const parseEvent = (data: string): RiskEvent | null => {
  try {
    const candidate: unknown = JSON.parse(data);
    if (!isRiskEvent(candidate)) return null;
    return { ...candidate, tier: riskTierForScore(candidate.score) };
  } catch { return null; }
};

export interface RiskStreamState { event: RiskEvent; history: readonly RiskEvent[]; status: StreamStatus; received: number; }

export function useRiskStream(): RiskStreamState {
  const [event, setEvent] = useState<RiskEvent>(sample);
  const [history, setHistory] = useState<readonly RiskEvent[]>([sample]);
  const [status, setStatus] = useState<StreamStatus>("demo");
  const [received, setReceived] = useState(0);
  const streamUrl = process.env.NEXT_PUBLIC_RISK_SSE_URL;
  const websocketUrl = process.env.NEXT_PUBLIC_RISK_WS_URL;

  useEffect(() => {
    if (streamUrl === undefined && websocketUrl === undefined) return undefined;
    let stopped = false;
    let retry: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;
    let source: EventSource | WebSocket | null = null;
    const connect = (): void => {
      if (stopped) return;
      setStatus("connecting");
      const handleData = (data: string): void => {
        const next = parseEvent(data);
        if (next === null) return;
        setEvent(next); setHistory((previous) => [...previous.slice(-19), next]); setReceived((count) => count + 1); setStatus("live"); attempt = 0;
      };
      const reconnect = (): void => {
        source = null;
        if (!stopped) { setStatus("offline"); retry = setTimeout(connect, Math.min(10_000, 500 * (2 ** attempt))); attempt += 1; }
      };
      if (streamUrl !== undefined) {
        const events = new EventSource(streamUrl, { withCredentials: true });
        events.onopen = () => setStatus("live"); events.onmessage = (message) => handleData(message.data); events.onerror = () => { events.close(); reconnect(); }; source = events;
      } else if (websocketUrl !== undefined) {
        const socket = new WebSocket(websocketUrl);
        socket.onopen = () => setStatus("live"); socket.onmessage = (message) => handleData(String(message.data)); socket.onclose = reconnect; socket.onerror = () => socket.close(); source = socket;
      }
    };
    connect();
    return () => { stopped = true; if (retry !== null) clearTimeout(retry); source?.close(); };
  }, [streamUrl, websocketUrl]);

  return useMemo(() => ({ event, history, status, received }), [event, history, status, received]);
}
