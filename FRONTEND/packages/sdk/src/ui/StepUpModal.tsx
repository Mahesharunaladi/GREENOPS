import { useState, type ReactNode } from "react";
import type { WebAuthnChallenge, WebAuthnResult } from "../types/telemetry";

export interface StepUpModalProps {
  open: boolean;
  challenge: WebAuthnChallenge;
  onResult: (result: WebAuthnResult) => void;
}

export function StepUpModal({ open, challenge, onResult }: StepUpModalProps): ReactNode {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!open) return null;
  const verify = async (): Promise<void> => {
    setBusy(true); setError(null);
    try {
      const credential = await navigator.credentials.get({ publicKey: challenge.publicKey });
      if (credential === null) throw new Error("No credential was returned.");
      onResult({ challengeId: challenge.challengeId, verified: true });
    } catch (cause: unknown) {
      const message = cause instanceof Error ? cause.message : "Verification failed.";
      setError(message); onResult({ challengeId: challenge.challengeId, verified: false, error: message });
    } finally { setBusy(false); }
  };
  return <div role="dialog" aria-modal="true" aria-labelledby="greenops-step-up-title" style={{ position: "fixed", inset: 0, display: "grid", placeItems: "center", background: "#0f172a33", zIndex: 40 }}><div style={{ width: "min(420px, calc(100% - 32px))", borderRadius: 16, background: "white", padding: 24, boxShadow: "0 24px 64px #0f172a33" }}><p style={{ color: "#b45309", fontSize: 12, fontWeight: 700, letterSpacing: ".12em" }}>STEP-UP REQUIRED</p><h2 id="greenops-step-up-title">Verify it’s you</h2><p style={{ color: "#64748b" }}>Use a passkey or security key to continue.</p>{error ? <p role="alert" style={{ color: "#be123c" }}>{error}</p> : null}<button disabled={busy} onClick={() => void verify()} type="button">{busy ? "Waiting for passkey…" : "Verify with passkey"}</button></div></div>;
}
