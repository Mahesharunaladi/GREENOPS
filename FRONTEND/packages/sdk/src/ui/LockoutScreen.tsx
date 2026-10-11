import { useEffect, type ReactNode } from "react";

export interface LockoutScreenProps { auditId: string; timestamp: string; onContain?: () => void }

const purgeBrowserState = async (): Promise<void> => {
  try { localStorage.clear(); sessionStorage.clear(); } catch { /* browser storage may be unavailable */ }
  if (typeof indexedDB !== "undefined" && typeof indexedDB.databases === "function") {
    const databases = await indexedDB.databases();
    await Promise.all(databases.flatMap((database) => database.name ? [new Promise<void>((resolve) => { const request = indexedDB.deleteDatabase(database.name as string); request.oncomplete = request.onerror = request.onblocked = () => resolve(); })] : []));
  }
};

export function LockoutScreen({ auditId, timestamp, onContain }: LockoutScreenProps): ReactNode {
  useEffect(() => { void purgeBrowserState().finally(() => onContain?.()); }, [onContain]);
  return <div role="alertdialog" aria-modal="true" aria-labelledby="greenops-lockout-title" style={{ position: "fixed", inset: 0, display: "grid", placeItems: "center", background: "white", padding: 24, zIndex: 50 }}><div style={{ maxWidth: 520, textAlign: "center" }}><p style={{ color: "#be123c", fontSize: 12, fontWeight: 700, letterSpacing: ".12em" }}>SESSION CONTAINED</p><h1 id="greenops-lockout-title">Access suspended</h1><p style={{ color: "#64748b" }}>A high-confidence identity anomaly revoked this session. Contact your security administrator.</p><dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: 12, textAlign: "left", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, fontSize: 12 }}><dt>Audit ID</dt><dd>{auditId}</dd><dt>Event time</dt><dd>{new Date(timestamp).toISOString()}</dd></dl></div></div>;
}
