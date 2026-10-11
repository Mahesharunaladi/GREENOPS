"use client";

import { useState } from "react";

export function StepUpModal({ open, onVerified }: { open: boolean; onVerified: () => void }): React.ReactNode {
  const [busy, setBusy] = useState(false);
  if (!open) return null;
  const verify = async (): Promise<void> => {
    setBusy(true);
    try {
      if (!("credentials" in navigator)) throw new Error("WebAuthn is unavailable in this browser.");
      await navigator.credentials.get({ publicKey: { challenge: new Uint8Array(32), timeout: 60000, userVerification: "required" } });
      onVerified();
    } finally { setBusy(false); }
  };
  return <div className="security-overlay" role="dialog" aria-modal="true" aria-labelledby="step-up-title"><div className="security-dialog"><span className="badge orange">STEP-UP REQUIRED</span><h2 id="step-up-title">Verify it’s you</h2><p>Your session risk increased. Use a passkey or security key to continue without losing your work.</p><button className="primary-action" disabled={busy} onClick={() => void verify()}>{busy ? "Waiting for passkey…" : "Verify with passkey"}</button></div></div>;
}
