import type { ReactNode } from "react";
import type { RiskEvent } from "@greenops/sdk";
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer, Tooltip } from "recharts";
import { CheckCircle2, Fingerprint, MousePointer2 } from "lucide-react";

export function DualBranchVisualizer({ event }: { event: RiskEvent }): ReactNode {
      const fingerprint = event.branches.fingerprint;
      const profile = event.branches.biometricProfile ?? [];
      const rows = fingerprint === undefined ? [] : [
        ["JA3", fingerprint.baselineJa3, fingerprint.currentJa3],
        ["JA4", fingerprint.baselineJa4, fingerprint.currentJa4]
      ] as const;
      return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4"><div><p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">Dual-branch AI engine</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Signal explainability</h2></div><span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">Analyzing</span></div>
        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          <section><div className="flex items-center gap-3"><span className="grid h-8 w-8 place-items-center rounded-lg bg-slate-900 text-sm font-bold text-white">A</span><div><h3 className="font-semibold text-slate-900">Transport context</h3><p className="text-xs text-slate-500">JA3 / JA4 fingerprint comparison</p></div><Fingerprint className="ml-auto text-slate-400" size={18} /></div><div className="mt-4 overflow-hidden rounded-xl border border-slate-100"><div className="grid grid-cols-3 bg-slate-50 px-3 py-2 text-[10px] font-semibold uppercase tracking-wide text-slate-400"><span>Hash</span><span>Baseline</span><span>Current</span></div>{rows.map(([name, baseline, current]) => <div className="grid grid-cols-3 items-center border-t border-slate-100 px-3 py-3 text-xs" key={name}><span className="font-medium text-slate-600">{name}</span><code className="text-slate-500">{baseline}</code><code className={baseline === current ? "text-emerald-700" : "font-semibold text-rose-600"}>{current}</code></div>)}</div><p className="mt-3 flex items-center gap-2 text-xs text-slate-500"><CheckCircle2 size={14} className="text-emerald-600" />{fingerprint?.discrepancies.length ?? 0} parameter discrepancies</p></section>
          <section><div className="flex items-center gap-3"><span className="grid h-8 w-8 place-items-center rounded-lg bg-indigo-600 text-sm font-bold text-white">B</span><div><h3 className="font-semibold text-slate-900">Biometric profiler</h3><p className="text-xs text-slate-500">Behavioral profile distance</p></div><MousePointer2 className="ml-auto text-slate-400" size={18} /></div><div className="mt-2 h-48 w-full"><ResponsiveContainer width="100%" height="100%"><RadarChart data={profile}><PolarGrid stroke="#e2e8f0" /><PolarAngleAxis dataKey="metric" tick={{ fill: "#64748b", fontSize: 10 }} /><Radar name="Baseline" dataKey="baseline" stroke="#94a3b8" fill="#94a3b8" fillOpacity={0.12} /><Radar name="Current" dataKey="current" stroke="#4f46e5" fill="#4f46e5" fillOpacity={0.2} /><Tooltip /></RadarChart></ResponsiveContainer></div></section>
        </div>
      </article>;
}
