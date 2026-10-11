"use client";

import type { ReactNode } from "react";
import { Activity, Keyboard, MousePointer2 } from "lucide-react";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TelemetryMetricPoint } from "../lib/greenopsApi";

export function TelemetryActivity({ points }: { points: readonly TelemetryMetricPoint[] }): ReactNode {
  const data = [...points].map((point) => ({ ...point, label: String(point.sequence) }));
  return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">Behavioral telemetry</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Keystroke and mouse activity</h2></div><Activity className="text-indigo-600" size={18} /></div>{data.length === 0 ? <p className="mt-8 rounded-lg bg-slate-50 p-5 text-center text-sm text-slate-500">Waiting for telemetry batches…</p> : <div className="mt-5 grid gap-6 xl:grid-cols-2"><Chart title="Keystroke timing" icon={<Keyboard size={15} />} data={data} firstKey="dwellMeanMs" secondKey="flightMeanMs" firstName="Dwell ms" secondName="Flight ms" /><Chart title="Mouse dynamics" icon={<MousePointer2 size={15} />} data={data} firstKey="mouseVelocityMean" secondKey="mouseCurvatureMean" firstName="Velocity" secondName="Curvature" /></div>}<p className="mt-5 border-t border-slate-100 pt-4 text-xs text-slate-400">Only aggregate timing and kinetic metrics are shown. Raw key text and pointer coordinates are never retained.</p></article>;
}

interface ChartProps { title: string; icon: ReactNode; data: readonly Record<string, number | string>[]; firstKey: string; secondKey: string; firstName: string; secondName: string }
function Chart({ title, icon, data, firstKey, secondKey, firstName, secondName }: ChartProps): ReactNode { return <section className="min-w-0"><h3 className="flex items-center gap-2 text-sm font-semibold text-slate-800">{icon}{title}</h3><div className="mt-3 h-48 w-full"><ResponsiveContainer width="100%" height="100%"><LineChart data={[...data]} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}><XAxis dataKey="label" tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} /><YAxis tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} /><Tooltip /><Line type="monotone" dataKey={firstKey} name={firstName} stroke="#4f46e5" strokeWidth={2} dot={false} /><Line type="monotone" dataKey={secondKey} name={secondName} stroke="#10b981" strokeWidth={2} dot={false} /></LineChart></ResponsiveContainer></div></section>; }
