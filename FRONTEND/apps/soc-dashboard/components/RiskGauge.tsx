import type { RiskEvent } from "@greenops/sdk";
import type { ReactNode } from "react";
import { Activity, ShieldAlert } from "lucide-react";

const tierStyles = {
  LOW: { label: "Low risk", badge: "bg-emerald-50 text-emerald-700", ring: "border-emerald-500", icon: "text-emerald-600" },
  MEDIUM: { label: "Step-up active", badge: "bg-amber-50 text-amber-700", ring: "border-amber-500", icon: "text-amber-600" },
  HIGH: { label: "Lockout executed", badge: "bg-rose-50 text-rose-700", ring: "border-rose-500", icon: "text-rose-600" }
} as const;

export function RiskGauge({ event }: { event: RiskEvent }): ReactNode {
    const style = tierStyles[event.tier];
    return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div><p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">Composite risk score</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Session confidence</h2></div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${style.badge}`}>{style.label}</span>
      </div>
      <div className="mt-6 flex items-center gap-6">
        <div className={`grid h-32 w-32 shrink-0 place-items-center rounded-full border-[12px] ${style.ring}`} aria-label={`Risk score ${event.score.toFixed(2)} out of 1.00`}>
          <div className="text-center"><strong className="block text-3xl font-bold tracking-tight text-slate-950">{event.score.toFixed(2)}</strong><span className="text-xs text-slate-400">/ 1.00</span></div>
        </div>
        <div className="min-w-0"><ShieldAlert className={style.icon} size={19} /><p className="mt-2 font-medium text-slate-800">{event.tier === "LOW" ? "Identity signals align with baseline." : event.tier === "MEDIUM" ? "Additional verification is required." : "Session containment is in effect."}</p><p className="mt-2 text-sm text-slate-500">Moving variance <strong className="text-slate-700">{event.branches.variance.toFixed(3)}</strong></p></div>
      </div>
      <div className="mt-6 grid grid-cols-3 gap-2 border-t border-slate-100 pt-4 text-xs"><span className="text-slate-500">Network <b className="ml-1 text-slate-800">{event.branches.network.toFixed(2)}</b></span><span className="text-slate-500">Biometric <b className="ml-1 text-slate-800">{event.branches.biometric.toFixed(2)}</b></span><span className="flex items-center justify-end gap-1 text-slate-500"><Activity size={13} className="text-emerald-600" /> Live</span></div>
    </article>;
}
