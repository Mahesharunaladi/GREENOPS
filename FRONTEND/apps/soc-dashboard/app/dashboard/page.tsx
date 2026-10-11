"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { Activity, ArrowLeft, Cloud, LayoutDashboard, Settings, ShieldAlert, Terminal } from "lucide-react";
import { ErrorBoundary } from "../../components/ErrorBoundary";
import { OperationsPanel } from "../../components/OperationsPanel";
import { LockoutScreen } from "../../components/LockoutScreen";
import { StepUpModal } from "../../components/StepUpModal";
import { useGreenopsTelemetry, GreenopsProvider } from "../../lib/GreenopsProvider";
import { useBackendSnapshot } from "../../lib/useBackendSnapshot";
import { useRiskStream } from "../../lib/useRiskStream";

const nav = [
  { label: "Overview", icon: LayoutDashboard }, { label: "Identity risk", icon: ShieldAlert },
  { label: "Cloud remediation", icon: Cloud }, { label: "Audit timeline", icon: Terminal }, { label: "Settings", icon: Settings }
] as const;

function DashboardView(): ReactNode {
  const [section, setSection] = useState<string>("Overview");
  const [mounted, setMounted] = useState(false);
  const stream = useRiskStream();
  const backend = useBackendSnapshot();
  const { acknowledgeRisk, clearRiskAction, telemetryEnabled, setTelemetryEnabled } = useGreenopsTelemetry();
  useEffect(() => { acknowledgeRisk(stream.event); }, [acknowledgeRisk, stream.event]);
  useEffect(() => { setMounted(true); }, []);
  const contained = stream.event.tier === "HIGH";
  return <main className="min-h-screen bg-slate-50 lg:flex">
    <aside className="border-b border-slate-200 bg-white px-5 py-4 lg:flex lg:min-h-screen lg:w-64 lg:flex-col lg:border-b-0 lg:border-r lg:px-4 lg:py-6">
      <div className="flex items-center justify-between lg:block"><Link href="/" className="flex items-center gap-2 text-sm font-extrabold tracking-[0.16em] text-slate-950"><span className="grid h-8 w-8 place-items-center rounded-lg bg-slate-900 text-white">G</span>GREENOPS</Link><span className="hidden text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400 lg:mt-8 lg:block">Security operations</span></div>
      <nav className="mt-4 flex gap-1 overflow-x-auto lg:mt-5 lg:grid lg:gap-1">{nav.map(({ label, icon: Icon }) => <button className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition ${section === label ? "bg-slate-900 font-medium text-white" : "text-slate-500 hover:bg-slate-100 hover:text-slate-900"}`} key={label} onClick={() => setSection(label)} type="button"><Icon size={16} />{label}</button>)}</nav>
      <div className="mt-6 hidden rounded-xl border border-slate-200 p-3 text-xs text-slate-500 lg:mt-auto lg:block"><span className="mr-2 inline-block h-2 w-2 rounded-full bg-emerald-500" />{stream.status === "live" ? "Event stream connected" : stream.status === "demo" ? "Demo event stream" : "Stream reconnecting"}<small className="mt-1 block text-slate-400">{stream.received} events received</small></div>
      <Link href="/" className="mt-4 hidden items-center gap-2 border-t border-slate-100 pt-4 text-xs text-slate-500 lg:flex"><ArrowLeft size={14} /> Back to GREENOPS</Link>
    </aside>
    <section className="min-w-0 flex-1"><header className="border-b border-slate-200 bg-white px-5 py-5 sm:px-8"><div className="mx-auto flex max-w-7xl items-center justify-between gap-4"><div><p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">Production / {section}</p><h1 className="mt-1 text-xl font-bold tracking-tight text-slate-950 sm:text-2xl">Zero Trust control center</h1></div><Activity className="text-slate-400" size={18} /></div></header>
      {section === "Overview" ? <div className="mx-auto max-w-7xl space-y-5 px-5 py-6 sm:px-8 sm:py-8"><section className="flex flex-col justify-between gap-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-end"><div><span className="rounded-full bg-indigo-50 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-indigo-700">Live risk monitoring</span><h2 className="mt-4 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Continuous authentication is active.</h2><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">Identity, transport context, and autonomous remediation signals are evaluated in real time.</p></div><div className="shrink-0 sm:text-right"><span className="text-xs text-slate-400">Last event</span><strong className="mt-1 block text-sm text-slate-800">{mounted ? new Date(stream.event.timestamp).toLocaleTimeString() : "—"}</strong><span className="text-xs text-slate-400">Sequence {stream.event.sequence}</span></div></section><div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><p className="text-xs text-slate-500">Spring API</p><strong className={backend.connected ? "text-emerald-700" : "text-slate-700"}>{backend.loading ? "Connecting…" : backend.connected ? "Connected" : "Unavailable"}</strong><p className="mt-2 text-xs text-slate-500">{backend.telemetry.acceptedBatches} telemetry batches · latest sequence {backend.telemetry.latestSequence ?? "—"}</p></div></div> : <div className="mx-auto max-w-7xl px-5 py-6 sm:px-8 sm:py-8"><OperationsPanel section={section} event={stream.event} backend={backend} telemetryEnabled={telemetryEnabled} setTelemetryEnabled={setTelemetryEnabled} /></div>}
    </section>
    <StepUpModal open={stream.event.tier === "MEDIUM"} onVerified={clearRiskAction} />
    {contained ? <LockoutScreen auditId={stream.event.auditId ?? stream.event.id} timestamp={stream.event.timestamp} /> : null}
  </main>;
}

export default function DashboardPage(): ReactNode { return <ErrorBoundary><GreenopsProvider><DashboardView /></GreenopsProvider></ErrorBoundary>; }
