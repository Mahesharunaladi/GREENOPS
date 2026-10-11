"use client";

import { useEffect, useState } from "react";
import { getBackendSnapshot, type BackendSnapshot } from "./greenopsApi";

const initialState: BackendSnapshot = { scan: null, auditCount: 0, telemetry: { acceptedBatches: 0, retainedBatches: 0, latestSequence: null, latestDwellMeanMs: 0, latestFlightMeanMs: 0, latestMouseVelocityMean: 0, latestMouseCurvatureMean: 0 }, connected: false, loading: true, error: null };

export function useBackendSnapshot(): BackendSnapshot {
  const [state, setState] = useState<BackendSnapshot>(initialState);
  useEffect(() => {
    let stopped = false;
    let controller: AbortController | null = null;
    const refresh = (): void => {
      controller?.abort();
      controller = new AbortController();
      void getBackendSnapshot(controller.signal)
        .then((snapshot) => { if (!stopped) setState({ ...snapshot, connected: true, loading: false, error: null }); })
        .catch((cause: unknown) => {
          if (stopped || controller?.signal.aborted) return;
          setState((previous) => ({ ...previous, connected: false, loading: false, error: cause instanceof Error ? cause.message : "Backend unavailable" }));
        });
    };
    refresh();
    const interval = setInterval(refresh, 2000);
    return () => { stopped = true; controller?.abort(); clearInterval(interval); };
  }, []);
  return state;
}
