"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { GreenopsClient, type RiskAction, type RiskEvent } from "@greenops/sdk";

interface GreenopsContextValue {
  telemetryEnabled: boolean;
  setTelemetryEnabled: (enabled: boolean) => void;
  riskAction: RiskAction;
  acknowledgeRisk: (event: RiskEvent) => void;
  clearRiskAction: () => void;
}
const Context = createContext<GreenopsContextValue | null>(null);

export function GreenopsProvider({ children }: { children: ReactNode }): ReactNode {
  const [telemetryEnabled, setTelemetryEnabled] = useState(true);
  const [riskAction, setRiskAction] = useState<RiskAction>("NONE");
  useEffect(() => {
    if (!telemetryEnabled) return;
    const client = new GreenopsClient({
      sessionId: crypto.randomUUID(),
      websocketUrl: process.env.NEXT_PUBLIC_TELEMETRY_WS_URL ?? "",
      beaconUrl: process.env.NEXT_PUBLIC_TELEMETRY_BEACON_URL ?? "",
      sessionToken: process.env.NEXT_PUBLIC_TELEMETRY_SESSION_TOKEN
    });
    return () => client.destroy();
  }, [telemetryEnabled]);
  const acknowledgeRisk = useCallback((event: RiskEvent): void => {
    const action: RiskAction = event.score >= 0.75 ? "LOCKOUT" : event.score >= 0.4 ? "STEP_UP" : "NONE";
    setRiskAction(action);
  }, []);
  const value = useMemo(() => ({ telemetryEnabled, setTelemetryEnabled, riskAction, acknowledgeRisk, clearRiskAction: () => setRiskAction("NONE") }), [telemetryEnabled, riskAction, acknowledgeRisk]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useGreenopsTelemetry(): GreenopsContextValue {
  const value = useContext(Context);
  if (value === null) throw new Error("useGreenopsTelemetry must be used inside GreenopsProvider");
  return value;
}
