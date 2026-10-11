"use client";

import { useEffect, useState } from "react";
import { getBackendSnapshot, type BackendSnapshot } from "./greenopsApi";

const initialState: BackendSnapshot = { scan: null, auditCount: 0, connected: false, loading: true, error: null };

export function useBackendSnapshot(): BackendSnapshot {
  const [state, setState] = useState<BackendSnapshot>(initialState);
  useEffect(() => {
    const controller = new AbortController();
    void getBackendSnapshot(controller.signal)
      .then((snapshot) => setState({ ...snapshot, connected: true, loading: false, error: null }))
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setState((previous) => ({ ...previous, connected: false, loading: false, error: cause instanceof Error ? cause.message : "Backend unavailable" }));
      });
    return () => controller.abort();
  }, []);
  return state;
}
