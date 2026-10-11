"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { KeystrokeHarvester, MouseHarvester } from "@greenops/sdk";

interface GreenopsContextValue { telemetryEnabled: boolean; setTelemetryEnabled: (enabled: boolean) => void; }
const Context = createContext<GreenopsContextValue | null>(null);

export function GreenopsProvider({ children }: { children: ReactNode }): ReactNode {
  const [telemetryEnabled, setTelemetryEnabled] = useState(true);
  useEffect(() => {
    if (!telemetryEnabled) return;
    const keys = new KeystrokeHarvester();
    const mouse = new MouseHarvester();
    keys.start(); mouse.start();
    return () => { keys.stop(); mouse.stop(); };
  }, [telemetryEnabled]);
  const value = useMemo(() => ({ telemetryEnabled, setTelemetryEnabled }), [telemetryEnabled]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useGreenopsTelemetry(): GreenopsContextValue {
  const value = useContext(Context);
  if (value === null) throw new Error("useGreenopsTelemetry must be used inside GreenopsProvider");
  return value;
}
