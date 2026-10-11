"use client";
import { useEffect, useState } from "react";
import type { RiskEvent } from "../../../packages/sdk/src/types/telemetry";

const sample: RiskEvent = { id: "evt-demo", sequence: 1284, timestamp: new Date().toISOString(), score: 0.24, tier: "LOW", branches: { network: 0.18, biometric: 0.29, variance: 0.041 }, auditId: "audit-demo" };

export function useRiskStream(): RiskEvent {
  const [event, setEvent] = useState<RiskEvent>(sample);
  useEffect(() => {
    const url = process.env.NEXT_PUBLIC_RISK_SSE_URL;
    if (!url) return;
    const source = new EventSource(url, { withCredentials: true });
    source.onmessage = (message) => {
      try { setEvent(JSON.parse(message.data) as RiskEvent); } catch { /* malformed events are ignored */ }
    };
    return () => source.close();
  }, []);
  return event;
}
