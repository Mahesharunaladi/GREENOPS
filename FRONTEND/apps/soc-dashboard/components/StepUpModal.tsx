"use client";

import { useState } from "react";
import { Fingerprint, ShieldCheck } from "lucide-react";

export function StepUpModal({ open, onVerified }: { open: boolean; onVerified: () => void }): React.ReactNode {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!open) return null;
  const verify = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      if (!("credentials" in navigator) || !window.PublicKeyCredential) throw new Error("WebAuthn is unavailable in this browser.");
      await navigator.credentials.get({ publicKey: { challenge: new Uint8Array(32).buffer, timeout: 60000, userVerification: "required" } });
      onVerified();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "Verification could not be completed.");
    } finally { setBusy(false); }
  };
  return <div className="fixed inset-0 z-40 grid place-items-center bg-slate-950/30 p-5 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="step-up-title"><div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 text-amber-700"><Fingerprint size={21} /></div><span className="mt-5 inline-block rounded-full bg-amber-50 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-700">Step-up required</span><h2 id="step-up-title" className="mt-3 text-2xl font-bold text-slate-950">Verify it’s you</h2><p className="mt-2 text-sm leading-6 text-slate-500">Your session risk increased. Use a passkey or security key to continue without losing your work.</p>{error ? <p className="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{error}</p> : null}<button className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-3 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-wait disabled:opacity-60" disabled={busy} onClick={() => void verify()} type="button"><ShieldCheck size={16} />{busy ? "Waiting for passkey…" : "Verify with passkey"}</button></div></div>;
}
