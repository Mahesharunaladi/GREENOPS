"use client";

import { useEffect } from "react";

async function purgeBrowserState(): Promise<void> {
  localStorage.clear();
  sessionStorage.clear();
  if ("indexedDB" in window && indexedDB.databases) {
    const databases = await indexedDB.databases();
    await Promise.all(databases.flatMap((database) => database.name ? [new Promise<void>((resolve) => { const request = indexedDB.deleteDatabase(database.name as string); request.onsuccess = () => resolve(); request.onerror = () => resolve(); request.onblocked = () => resolve(); })] : []));
  }
}

export function LockoutScreen({ auditId, timestamp }: { auditId: string; timestamp: string }): React.ReactNode {
  useEffect(() => { void purgeBrowserState(); }, []);
  return <div className="lockout" role="alertdialog" aria-modal="true"><div><span className="badge high">SESSION CONTAINED</span><h1>Access suspended</h1><p>GREENOPS detected a high-confidence identity anomaly and revoked this session. Contact your security administrator to restore access.</p><dl><dt>Audit ID</dt><dd>{auditId}</dd><dt>Event time</dt><dd>{new Date(timestamp).toISOString()}</dd></dl></div></div>;
}
