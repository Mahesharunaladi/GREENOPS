"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Activity, Cloud, LayoutDashboard, ShieldAlert, ShieldCheck, Settings, Terminal } from "lucide-react";
import { RiskGauge } from "../components/RiskGauge";
import { DualBranchVisualizer } from "../components/DualBranchVisualizer";
import { AuditTimeline } from "../components/AuditTimeline";
import { useRiskStream } from "../lib/useRiskStream";
import { useGreenopsTelemetry } from "../lib/GreenopsProvider";
import { useBackendSnapshot } from "../lib/useBackendSnapshot";
import { LockoutScreen } from "../components/LockoutScreen";
import { StepUpModal } from "../components/StepUpModal";

export default function DashboardPage(): ReactNode {
  const [section, setSection] = useState("Overview");
  const [mounted, setMounted] = useState(false);
    const stream = useRiskStream();
    const backend = useBackendSnapshot();
    const { acknowledgeRisk, clearRiskAction } = useGreenopsTelemetry();
    useEffect(() => { acknowledgeRisk(stream.event); }, [acknowledgeRisk, stream.event]);
    useEffect(() => { setMounted(true); }, []);
    const contained = stream.event.tier === "HIGH";
  const nav = [
    { label: "Overview", icon: LayoutDashboard },
    { label: "Identity risk", icon: ShieldAlert },
    { label: "Cloud remediation", icon: Cloud },
    { label: "Audit timeline", icon: Terminal },
    { label: "Settings", icon: Settings }
  ];
    return <main className="min-h-screen bg-slate-50 lg:flex">
      <aside className="border-b border-slate-200 bg-white px-5 py-4 lg:flex lg:min-h-screen lg:w-64 lg:flex-col lg:border-b-0 lg:border-r lg:px-4 lg:py-6">
        <div className="flex items-center justify-between lg:block"><div className="flex items-center gap-2 text-sm font-extrabold tracking-[0.16em] text-slate-950"><span className="grid h-8 w-8 place-items-center rounded-lg bg-slate-900 text-white">G</span>GREENOPS</div><span className="hidden text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400 lg:mt-8 lg:block">Security operations</span></div>
        <nav className="mt-4 flex gap-1 overflow-x-auto lg:mt-5 lg:grid lg:gap-1">{nav.map(({ label, icon: Icon }) => <button className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition ${section === label ? "bg-slate-900 font-medium text-white" : "text-slate-500 hover:bg-slate-100 hover:text-slate-900"}`} key={label} onClick={() => setSection(label)} type="button"><Icon size={16} />{label}</button>)}</nav>
        <div className="mt-6 hidden rounded-xl border border-slate-200 p-3 text-xs text-slate-500 lg:mt-auto lg:block"><span className="mr-2 inline-block h-2 w-2 rounded-full bg-emerald-500" />{stream.status === "live" ? "Event stream connected" : stream.status === "demo" ? "Demo event stream" : "Stream reconnecting"}<small className="mt-1 block text-slate-400">{stream.received} events received</small></div>
        <div className="mt-4 hidden items-center gap-2 border-t border-slate-100 pt-4 text-xs lg:flex"><span className="grid h-8 w-8 place-items-center rounded-full bg-indigo-100 font-semibold text-indigo-700">AM</span><span><strong className="block text-slate-800">Alex Morgan</strong><small className="text-slate-400">Platform engineer</small></span></div>
      </aside>
      <section className="min-w-0 flex-1"><header className="border-b border-slate-200 bg-white px-5 py-5 sm:px-8"><div className="mx-auto flex max-w-7xl items-center justify-between gap-4"><div><p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">Production / {section}</p><h1 className="mt-1 text-xl font-bold tracking-tight text-slate-950 sm:text-2xl">Zero Trust control center</h1></div><div className="flex items-center gap-3"><span className="hidden items-center gap-1.5 text-xs font-semibold text-emerald-700 sm:flex"><ShieldCheck size={15} /> Protected</span><button className="rounded-lg border border-slate-200 p-2 text-slate-500 hover:bg-slate-50" aria-label="Activity notifications" type="button"><Activity size={17} /></button></div></div></header>
        <div className="mx-auto max-w-7xl space-y-5 px-5 py-6 sm:px-8 sm:py-8"><section className="flex flex-col justify-between gap-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-end"><div><span className="rounded-full bg-indigo-50 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-indigo-700">Live risk monitoring</span><h2 className="mt-4 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Continuous authentication is active.</h2><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">Identity, transport context, and autonomous remediation signals are evaluated in real time.</p></div><div className="shrink-0 sm:text-right"><span className="text-xs text-slate-400">Last event</span><strong className="mt-1 block text-sm text-slate-800">{mounted ? new Date(stream.event.timestamp).toLocaleTimeString() : "—"}</strong><span className="text-xs text-slate-400">Sequence {stream.event.sequence}</span></div></section><section className="grid gap-3 sm:grid-cols-3"><div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-xs text-slate-500">Spring API</p><p className={`mt-1 text-sm font-semibold ${backend.connected ? "text-emerald-700" : "text-slate-700"}`}>{backend.loading ? "Connecting…" : backend.connected ? "Connected" : "Unavailable"}</p>{backend.error ? <p className="mt-1 truncate text-[11px] text-slate-400" title={backend.error}>Start the backend on port 8080</p> : null}</div><div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-xs text-slate-500">Latest idle scan</p><p className="mt-1 text-sm font-semibold text-slate-800">{backend.scan === null ? "Waiting for scan" : `${backend.scan.idleResources} idle resources`}</p></div><div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><p className="text-xs text-slate-500">Audit records</p><p className="mt-1 text-sm font-semibold text-slate-800">{backend.loading ? "—" : backend.auditCount}</p></div></section><section className="grid gap-5 xl:grid-cols-2"><RiskGauge event={stream.event} /><DualBranchVisualizer event={stream.event} /></section><AuditTimeline /></div>
      </section>
      <StepUpModal open={stream.event.tier === "MEDIUM"} onVerified={clearRiskAction} />
      {contained ? <LockoutScreen auditId={stream.event.auditId ?? stream.event.id} timestamp={stream.event.timestamp} /> : null}
    </main>;
}
