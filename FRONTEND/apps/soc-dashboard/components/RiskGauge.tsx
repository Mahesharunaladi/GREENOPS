import type { RiskEvent } from "../../../packages/sdk/src/types/telemetry";
import type { ReactNode } from "react";
import type { CSSProperties } from "react";
import { Activity, ShieldAlert } from "lucide-react";

export function RiskGauge({ event }: { event: RiskEvent }): ReactNode {
  const tierClass = event.tier.toLowerCase();
  return <article className="card gauge-card"><div className="card-heading"><div><p className="eyebrow">COMPOSITE RISK SCORE</p><h3>Session confidence</h3></div><span className={`badge ${tierClass}`}>{event.tier}</span></div><div className="gauge-row"><div className={`gauge ${tierClass}`} style={{ "--score": `${event.score * 100}%` } as CSSProperties}><div><strong>{event.score.toFixed(2)}</strong><small>/ 1.00</small></div></div><div className="risk-copy"><ShieldAlert size={18} /><strong>{event.tier === "LOW" ? "Low risk posture" : event.tier === "MEDIUM" ? "Step-up verification ready" : "Lockout recommended"}</strong><p>Moving variance {event.branches.variance.toFixed(3)}</p></div></div><div className="score-list"><span>Network branch <b>{event.branches.network.toFixed(2)}</b></span><span>Biometric branch <b>{event.branches.biometric.toFixed(2)}</b></span><span><Activity size={14} /> EWMA stream <b>sub-second</b></span></div></article>;
}
