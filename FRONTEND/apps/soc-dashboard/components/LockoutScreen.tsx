"use client";

import { useEffect } from "react";

async function purgeBrowserState(): Promise<void> {
  try { localStorage.clear(); } catch { /* storage may be blocked */ }
  try { sessionStorage.clear(); } catch { /* storage may be blocked */ }
  if ("indexedDB" in window && typeof indexedDB.databases === "function") {
    const databases = await indexedDB.databases();
    await Promise.all(databases.flatMap((database) => database.name ? [new Promise<void>((resolve) => { const request = indexedDB.deleteDatabase(database.name as string); request.onsuccess = () => resolve(); request.onerror = () => resolve(); request.onblocked = () => resolve(); })] : []));
  }
}

export function LockoutScreen({ auditId, timestamp }: { auditId: string; timestamp: string }): React.ReactNode {
  useEffect(() => { void purgeBrowserState(); }, []);
  return <div className="fixed inset-0 z-50 grid place-items-center bg-white p-5" role="alertdialog" aria-modal="true" aria-labelledby="lockout-title"><div className="w-full max-w-lg text-center"><span className="inline-block rounded-full bg-rose-50 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-rose-700">Session contained</span><h1 id="lockout-title" className="mt-5 text-3xl font-bold tracking-tight text-slate-950">Access suspended</h1><p className="mt-3 text-sm leading-6 text-slate-500">GREENOPS detected a high-confidence identity anomaly and revoked this session. Contact your security administrator to restore access.</p><dl className="mt-7 grid grid-cols-[auto_1fr] gap-x-5 gap-y-3 rounded-xl border border-slate-200 p-4 text-left text-xs"><dt className="text-slate-400">Audit ID</dt><dd className="break-all font-mono text-slate-700">{auditId}</dd><dt className="text-slate-400">Event time</dt><dd className="font-mono text-slate-700">{new Date(timestamp).toISOString()}</dd></dl><p className="mt-5 text-xs text-slate-400">Browser session data was cleared on containment.</p></div></div>;
}
