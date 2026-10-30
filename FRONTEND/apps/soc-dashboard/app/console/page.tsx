"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Activity, AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight, BarChart3, Bell, ClipboardCheck,
  Check, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight, CircleDollarSign, Cloud,
  CloudCog, Cpu, Database, Fingerprint, Gauge, Globe2, HardDrive, LayoutDashboard,
  Leaf, LockKeyhole, Menu, MoreHorizontal, Network, Radio, RefreshCw, Search,
  Server, Shield, ShieldAlert, ShieldCheck, Sparkles, Terminal, Timer, UserRound,
  Users, Waves, X, Zap, KeyRound, Send,
} from "lucide-react";

type TabId = "overview" | "finops" | "identity" | "ai-swarm" | "deception" | "eco-security" | "enterprise";
type ResourceStatus = "IDLE" | "SIMULATED" | "REMEDIATED";
type Resource = { id: string; name: string; service: string; spec: string; telemetry: string; savings: number; action: string; status: ResourceStatus };
type LogEntry = { time: string; level: "INFO" | "SUCCESS" | "WARN" | "AUTH" | "SIMULATION"; message: string };
type DeceptionEvent = { logId: string; compositeHash: string; ipAddress: string; attemptedEndpoint: string; httpMethod: string; payloadCaptured: string; timestamp: string; violationCount: number };
type ComplianceResult = { framework: string; complianceScorePercentage: number; passedRules: string[]; violations: string[]; evaluatedAt: string };
type BlastRadiusResult = { userId: string; compromisedRole: string; accessibleS3Buckets: number; accessibleRdsDatabases: number; accessibleEc2Instances: number; quantifiedRiskExposureUsd: number; criticalResourceArns: string[] };
type CanaryResult = { tokenId: string; fakeAccessKey: string; associatedSessionId: string; honeyResourceType: string; createdAt: string; callbackUrl: string };

const tabs: { id: TabId; label: string; path: string; icon: typeof LayoutDashboard }[] = [
  { id: "overview", label: "Executive Overview", path: "/overview", icon: LayoutDashboard },
  { id: "finops", label: "Cloud Cost Scanner", path: "/finops", icon: Cloud },
  { id: "identity", label: "Continuous Identity", path: "/identity", icon: Fingerprint },
  { id: "ai-swarm", label: "Multi-Agent Swarm", path: "/ai-swarm", icon: Network },
  { id: "deception", label: "Honeypot Sandbox", path: "/deception", icon: ShieldAlert },
  { id: "eco-security", label: "Eco-Security Scaler", path: "/eco-security", icon: Leaf },
  { id: "enterprise", label: "Enterprise Governance", path: "/enterprise", icon: ShieldCheck },
];

const initialResources: Resource[] = [
  { id: "i-0a8bf213a9", name: "staging-api-node-03", service: "EC2", spec: "t3.large · us-east-1a", telemetry: "CPU < 0.8% · 14 days", savings: 92.4, action: "Stop resource", status: "IDLE" },
  { id: "vol-091a1e8c22", name: "legacy-build-volume", service: "EBS", spec: "gp3 · 500 GiB", telemetry: "Unattached · 32 days", savings: 40, action: "Delete snapshot", status: "IDLE" },
  { id: "rds-prod-read-replica-2", name: "prod-read-replica-2", service: "RDS", spec: "db.r6g.large · MySQL", telemetry: "Connections < 1 · 7 days", savings: 642.8, action: "Stop resource", status: "IDLE" },
  { id: "alb-stg-external-v2", name: "staging-external-v2", service: "ELB", spec: "Application · internet-facing", telemetry: "0 requests · 21 days", savings: 18.2, action: "Delete snapshot", status: "IDLE" },
];

const baseLogs: LogEntry[] = [
  { time: "14:32:08.421", level: "INFO", message: "Sample telemetry event · 24 signals · session usr_mahesh_aladi_91a" },
  { time: "14:32:08.438", level: "AUTH", message: "Sample behavioral confidence 98.2% · identity continuously verified" },
  { time: "14:32:08.502", level: "SUCCESS", message: "Sample swarm consensus · 3/3 signals benign · score 0.08" },
  { time: "14:32:09.117", level: "SIMULATION", message: "Dry-run enabled · demo actions remain local to this page" },
  { time: "14:31:54.891", level: "WARN", message: "Sample idle candidate · i-0a8bf213a9 · estimated savings $92.40/mo" },
];

const streamEvents: { level: LogEntry["level"]; message: string }[] = [
  { level: "AUTH", message: "Demo session heartbeat · sample hardware attestation valid" },
  { level: "INFO", message: "Demo network context · sample JA3 fingerprint consistent" },
  { level: "SUCCESS", message: "Demo swarm consensus · no sample action required" },
  { level: "WARN", message: "Demo cost telemetry · 4 sample candidates in us-east-1" },
  { level: "SIMULATION", message: "Demo policy preview · no cloud resources modified" },
];

const money = (n: number) => n.toLocaleString("en-US", { style: "currency", currency: "USD", minimumFractionDigits: 2 });

type SpireUiStatus = "CHECKING" | "WATCHING" | "READY" | "INACTIVE" | "UNAVAILABLE";

export default function GreenOpsConsole() {
  const [activeTab, setActiveTab] = useState<TabId>("overview");
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const dryRun = true;
  const [resources, setResources] = useState(initialResources);
  const [logs, setLogs] = useState(baseLogs);
  const [searchTerm, setSearchTerm] = useState("");
  const [notice, setNotice] = useState("");
  const [region, setRegion] = useState("us-east-1");
  const [regionOpen, setRegionOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationsUnread, setNotificationsUnread] = useState(true);
  const [accountOpen, setAccountOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [spireStatus, setSpireStatus] = useState<SpireUiStatus>("CHECKING");
  const [spireMissingChecks, setSpireMissingChecks] = useState<string[]>([]);
  const [spireActivity, setSpireActivity] = useState("Checking SPIRE attestation status.");
  const [spirePanelOpen, setSpirePanelOpen] = useState(false);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const event = streamEvents[Math.floor(Math.random() * streamEvents.length)] ?? streamEvents[0]!;
      const time = new Date().toLocaleTimeString("en-US", { hour12: false });
      setLogs((current) => [{ ...event, time: `${time}.${String(Math.floor(Math.random() * 900) + 100)}` }, ...current].slice(0, 12));
    }, 4600);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const syncHash = () => {
      const path = window.location.hash.slice(1);
      const matchingTab = tabs.find((tab) => tab.path === path);
      if (matchingTab) setActiveTab(matchingTab.id);
    };
    syncHash();
    window.addEventListener("popstate", syncHash);
    return () => window.removeEventListener("popstate", syncHash);
  }, []);

  useEffect(() => {
    let active = true;
    const refreshSpireStatus = async () => {
      try {
        const response = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080"}/api/v1/spire/status`, {
          credentials: "include",
          cache: "no-store",
          headers: { accept: "application/json" },
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const status = await response.json() as { status?: SpireUiStatus; checks?: Record<string, boolean>; attestationsSeen?: number; lastAttestationAt?: string | null; lastAttestationVerified?: boolean | null };
        if (!active) return;
        setSpireStatus(status.status === "WATCHING" ? "WATCHING" : status.status === "READY" ? "READY" : "INACTIVE");
        setSpireMissingChecks(Object.entries(status.checks ?? {}).filter(([, configured]) => !configured).map(([name]) => name));
        setSpireActivity(status.attestationsSeen ? `${status.attestationsSeen} requests seen · latest ${status.lastAttestationVerified ? "verified" : "denied"} at ${status.lastAttestationAt ?? "unknown time"}` : "No attestation requests received yet.");
      } catch {
        if (active) setSpireStatus("UNAVAILABLE");
      }
    };
    void refreshSpireStatus();
    const timer = window.setInterval(refreshSpireStatus, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 3200);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const currentTab = tabs.find((tab) => tab.id === activeTab) ?? tabs[0]!;
  const filteredResources = useMemo(() => resources.filter((resource) =>
    `${resource.id} ${resource.name} ${resource.service}`.toLowerCase().includes(searchTerm.toLowerCase())), [resources, searchTerm]);
  const totalSavings = resources.filter((resource) => resource.status !== "REMEDIATED").reduce((sum, resource) => sum + resource.savings, 0);

  function navigate(tab: TabId) {
    setActiveTab(tab);
    const destination = tabs.find((item) => item.id === tab)?.path ?? "/overview";
    window.history.pushState(null, "", `/console#${destination}`);
    setMobileOpen(false);
  }

  function remediate(id: string) {
    const selected = resources.find((item) => item.id === id);
    if (!selected || selected.status !== "IDLE") return;
    const status: ResourceStatus = dryRun ? "SIMULATED" : "REMEDIATED";
    const actionLevel: LogEntry["level"] = dryRun ? "SIMULATION" : "SUCCESS";
    setResources((current) => current.map((item) => item.id === id ? { ...item, status } : item));
    setLogs((current) => [{
      time: new Date().toLocaleTimeString("en-US", { hour12: false }),
      level: actionLevel,
      message: dryRun
        ? `Simulation preview · ${id} · ${selected.action.toLowerCase()}`
        : `Demo AWS SDK v2 call event · ${selected.action.toLowerCase()} · ${id} · no request sent`,
    }, ...current].slice(0, 12));
    setNotice(`${dryRun ? "Simulation preview" : "Local demo status"} updated for ${id}; no AWS request was sent`);
  }

  function resetCandidates() {
    setResources(initialResources);
    setNotice("Sample cloud candidates restored");
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080"}/api/v1/auth/logout`, { method: "POST", credentials: "include" });
    } catch {
      // The browser should still return to sign-in if the backend is unavailable.
    } finally {
      document.cookie = "greenops_session=; Max-Age=0; path=/; SameSite=Lax";
      window.location.assign("/login");
    }
  }

  return (
    <main className="min-h-screen bg-slate-50 text-slate-900 antialiased">
      <div className="flex min-h-screen">
        {mobileOpen && <button aria-label="Close navigation" onClick={() => setMobileOpen(false)} className="fixed inset-0 z-20 bg-slate-950/20 lg:hidden" />}
        <aside className={`fixed inset-y-0 left-0 z-30 flex flex-col border-r border-slate-200 bg-white transition-[width,transform] duration-200 lg:static lg:translate-x-0 ${mobileOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"} ${collapsed ? "w-[76px]" : "w-[264px]"}`}>
          <div className={`flex h-[76px] items-center border-b border-slate-100 ${collapsed ? "justify-center px-3" : "justify-between px-5"}`}>
            <div className="flex min-w-0 items-center gap-3">
              <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-600 text-white shadow-sm shadow-emerald-200"><Leaf size={20} strokeWidth={2.4} /></div>
              {!collapsed && <div className="min-w-0"><div className="text-[13px] font-extrabold leading-4 tracking-tight">GREENOPS</div><div className="mt-1 truncate text-[10px] font-semibold uppercase tracking-[.15em] text-slate-400">Zero Trust · FinOps</div></div>}
            </div>
            {!collapsed && <button className="hidden rounded-md p-1.5 text-slate-400 hover:bg-slate-100 lg:block" onClick={() => setCollapsed(true)} aria-label="Collapse sidebar"><ChevronLeft size={16} /></button>}
          </div>
          {collapsed && <button className="mx-auto mt-4 hidden rounded-md p-2 text-slate-400 hover:bg-slate-100 lg:block" onClick={() => setCollapsed(false)} aria-label="Expand sidebar"><ChevronRight size={16} /></button>}
          <div className={`px-3 pt-6 ${collapsed ? "px-2" : ""}`}>
            {!collapsed && <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[.16em] text-slate-400">Workspace</p>}
            <nav aria-label="Main navigation" className="space-y-1">
              {tabs.map(({ id, label, icon: Icon }) => {
                const selected = activeTab === id;
                return <button key={id} onClick={() => navigate(id)} title={collapsed ? label : undefined} aria-current={selected ? "page" : undefined}
                  className={`group flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[12px] font-semibold transition ${selected ? "bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-100" : "text-slate-500 hover:bg-slate-50 hover:text-slate-900"} ${collapsed ? "justify-center px-2" : ""}`}>
                  <Icon size={17} strokeWidth={selected ? 2.2 : 1.8} className={selected ? "text-emerald-600" : "text-slate-400 group-hover:text-slate-600"} />{!collapsed && <span>{label}</span>}
                  {!collapsed && id === "deception" && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-rose-500" />}
                </button>;
              })}
            </nav>
          </div>
          <div className={`mt-auto p-3 ${collapsed ? "hidden" : ""}`}>
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
              <div className="flex items-center justify-between"><span className="text-[10px] font-bold uppercase tracking-[.12em] text-slate-500">Workspace mode</span><span className="h-2 w-2 rounded-full bg-amber-400" /></div>
              <div className="mt-2 text-xs font-semibold text-slate-800">Illustrative workspace</div>
              <div className="mt-1.5 flex items-center gap-1.5 text-[10px] text-slate-400"><Radio size={11} className="text-sky-600" />Sample telemetry · not connected</div>
            </div>
            <div className="mt-4 flex items-center gap-2.5 border-t border-slate-100 pt-4">
              <div className="grid h-8 w-8 place-items-center rounded-full bg-slate-100 text-slate-600"><UserRound size={15} /></div>
              <div className="min-w-0 flex-1"><div className="truncate text-[11px] font-semibold">Mahesh Aladi</div><div className="text-[10px] text-slate-400">Security administrator</div></div>
              <div className="relative"><button type="button" className="rounded-md p-1 text-slate-400 hover:bg-slate-100" aria-label="Account menu" aria-expanded={accountOpen} onClick={() => setAccountOpen((open) => !open)}><MoreHorizontal size={17} /></button>{accountOpen && <div className="absolute bottom-full right-0 z-40 mb-2 w-44 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg"><div className="border-b border-slate-100 px-3 py-2"><p className="truncate text-[11px] font-semibold text-slate-800">Mahesh Aladi</p><p className="text-[10px] text-slate-400">Security administrator</p></div><button type="button" onClick={logout} disabled={loggingOut} className="mt-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"><UserRound size={14} />{loggingOut ? "Signing out…" : "Log out"}</button></div>}</div>
            </div>
          </div>
        </aside>

        <div className="min-w-0 flex-1">
          <header className="sticky top-0 z-20 flex h-[76px] items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-7">
            <div className="flex min-w-0 items-center gap-3">
              <button className="rounded-md p-2 text-slate-500 hover:bg-slate-100 lg:hidden" aria-label="Toggle sidebar" onClick={() => setMobileOpen((open) => !open)}><Menu size={18} /></button>
              {collapsed && <button className="hidden rounded-md p-2 text-slate-400 hover:bg-slate-100 lg:block" onClick={() => setCollapsed(false)} aria-label="Expand sidebar"><ChevronRight size={16} /></button>}
              <div className="min-w-0"><div className="hidden items-center gap-2 text-[10px] font-medium text-slate-400 sm:flex"><span>GreenOps Console</span><ChevronRight size={12} /><span className="text-slate-500">{currentTab.label}</span></div><h1 className="truncate text-sm font-bold tracking-tight sm:mt-1 sm:text-base">{currentTab.label}</h1></div>
            </div>
            <div className="flex shrink-0 items-center gap-2 sm:gap-4">
              <div className="relative hidden sm:block"><button type="button" aria-expanded={spirePanelOpen} onClick={() => setSpirePanelOpen((open) => !open)} title={spireActivity} className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1.5 text-[9px] font-bold uppercase tracking-wide ${spireStatus === "WATCHING" ? "bg-emerald-50 text-emerald-700" : spireStatus === "READY" ? "bg-sky-50 text-sky-700" : spireStatus === "INACTIVE" ? "bg-amber-50 text-amber-700" : "bg-slate-100 text-slate-500"}`}><span className={`h-1.5 w-1.5 rounded-full ${spireStatus === "WATCHING" ? "bg-emerald-500" : spireStatus === "READY" ? "bg-sky-500" : spireStatus === "INACTIVE" ? "bg-amber-500" : "bg-slate-400"}`} />SPIRE {spireStatus === "CHECKING" ? "checking" : spireStatus === "WATCHING" ? "recent activity" : spireStatus === "READY" ? "configured" : spireStatus === "INACTIVE" ? "inactive" : "unavailable"}<ChevronDown size={11}/></button>{spirePanelOpen && <div className="absolute right-0 top-full z-40 mt-2 w-72 rounded-xl border border-slate-200 bg-white p-4 shadow-lg"><p className="text-xs font-bold text-slate-800">SPIRE attestation</p><p className="mt-1 text-[10px] leading-4 text-slate-500">{spireActivity}</p>{spireStatus === "READY" && <p className="mt-2 text-[10px] text-sky-700">Configured; waiting for an attestation request.</p>}{spireStatus === "WATCHING" && <p className="mt-2 text-[10px] text-emerald-700">Recent request activity detected. This endpoint evaluates incoming requests, not a background process.</p>}{spireStatus === "INACTIVE" && <><p className="mt-3 text-[10px] font-semibold text-amber-700">Missing integrations</p><ul className="mt-1 space-y-1">{spireMissingChecks.map((item) => <li key={item} className="text-[10px] text-slate-500">• {item}</li>)}</ul></>}{spireStatus === "UNAVAILABLE" && <p className="mt-2 text-[10px] text-rose-600">Could not retrieve status from the backend.</p>}</div>}</div>
              <div className="relative">
                <button type="button" aria-label={`AWS region ${region}`} aria-expanded={regionOpen} onClick={() => { setRegionOpen((open) => !open); setNotificationsOpen(false); }} className="flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-emerald-100 sm:gap-2 sm:px-2.5 sm:py-2 sm:text-[11px]"><CloudCog size={14} className="shrink-0 text-slate-400" /><span>{region}</span><ChevronDown size={13} className={`shrink-0 text-slate-400 transition-transform ${regionOpen ? "rotate-180" : ""}`} /></button>
                {regionOpen && <div className="absolute right-0 top-full z-40 mt-2 w-48 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg" role="listbox" aria-label="Select AWS region">{["us-east-1", "us-west-2", "eu-west-1", "ap-south-1"].map((item) => <button key={item} type="button" role="option" aria-selected={region === item} onClick={() => { setRegion(item); setRegionOpen(false); setNotice(`Region view changed to ${item} · sample data only`); }} className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-xs font-medium hover:bg-slate-50 ${region === item ? "text-emerald-700" : "text-slate-600"}`}><span className="font-mono">{item}</span>{region === item && <Check size={14} />}</button>)}</div>}
              </div>
              <div className="relative">
                <button type="button" className="relative rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Notifications" aria-expanded={notificationsOpen} onClick={() => { setNotificationsOpen((open) => !open); setRegionOpen(false); }}><Bell size={17} />{notificationsUnread && <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-rose-500 ring-2 ring-white" />}</button>
                {notificationsOpen && <div className="absolute right-0 top-full z-40 mt-2 w-[min(21rem,calc(100vw-2rem))] rounded-xl border border-slate-200 bg-white shadow-lg"><div className="flex items-center justify-between border-b border-slate-100 px-4 py-3"><div><p className="text-xs font-bold text-slate-800">Notifications</p><p className="mt-0.5 text-[10px] text-slate-400">Workspace activity</p></div><button type="button" onClick={() => setNotificationsUnread(false)} className="text-[10px] font-semibold text-emerald-700 hover:text-emerald-800">Mark all read</button></div><div className="p-3"><div className="flex gap-3 rounded-lg bg-amber-50/70 p-3"><span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-amber-500"/><div><p className="text-[11px] font-semibold text-slate-800">Sample workspace is in illustrative mode</p><p className="mt-1 text-[10px] leading-4 text-slate-500">Cloud actions and identity signals shown here are demo data; live adapters are not connected.</p><p className="mt-2 text-[9px] text-slate-400">Just now</p></div></div><p className="px-1 py-3 text-center text-[10px] text-slate-400">You’re all caught up.</p></div></div>}
              </div>
            </div>
          </header>

          <div className="mx-auto max-w-[1500px] space-y-5 p-4 sm:p-7">
            {activeTab === "overview" && <Overview logs={logs} navigate={navigate} />}
            {activeTab === "finops" && <FinOps resources={filteredResources} search={searchTerm} setSearch={setSearchTerm} remediate={remediate} totalSavings={totalSavings} dryRun={dryRun} resetCandidates={resetCandidates} />}
            {activeTab === "identity" && <Identity />}
            {activeTab === "ai-swarm" && <Swarm />}
            {activeTab === "deception" && <Deception />}
            {activeTab === "eco-security" && <EcoSecurity />}
            {activeTab === "enterprise" && <EnterpriseGovernance />}
          </div>
        </div>
      </div>
      {notice && <div role="status" className="fixed bottom-5 right-5 z-50 flex max-w-[calc(100vw-2.5rem)] items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs font-semibold text-slate-700 shadow-lg"><CheckCircle2 size={16} className="shrink-0 text-emerald-600" />{notice}<button onClick={() => setNotice("")} aria-label="Dismiss" className="ml-2 text-slate-400"><X size={14} /></button></div>}
    </main>
  );
}

function Overview({ logs, navigate }: { logs: LogEntry[]; navigate: (tab: TabId) => void }) {
  return <>
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.16em] text-emerald-700"><span className="grid h-5 w-5 place-items-center rounded-md bg-emerald-100"><Sparkles size={12} /></span>Operations overview</div><h2 className="mt-2 text-xl font-bold tracking-tight sm:text-2xl">GreenOps command center</h2><p className="mt-1 text-xs text-slate-500">Illustrative project metrics · live service adapters are not connected.</p></div>
      <div className="flex items-center gap-2 text-[10px] text-slate-400"><RefreshCw size={12} />Updated just now <span className="mx-1 h-3 border-l border-slate-200" /><span>Last 30 days</span><ChevronDown size={12} /></div>
    </div>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <Kpi icon={CircleDollarSign} label="Monthly savings" value="$14,250.00" change="12.8%" tone="emerald" note="vs. previous month" />
      <Kpi icon={Users} label="Active verified sessions" value="1,420" change="Sample" tone="sky" note="illustrative session count" />
      <Kpi icon={ShieldCheck} label="Threats intercepted" value="3 revoked" change="Sample" tone="rose" note="illustrative count" />
      <Kpi icon={Timer} label="Avg. IAM response" value="340 ms" change="Sample" tone="amber" note="illustrative response time" />
    </div>
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1.55fr)_minmax(330px,.85fr)]">
      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3.5 sm:px-5"><div><div className="flex items-center gap-2 text-xs font-bold"><Activity size={15} className="text-sky-600" />System execution log</div><p className="mt-1 text-[10px] text-slate-400">Demo events · generated locally</p></div><span className="flex items-center gap-1.5 rounded-full bg-amber-50 px-2 py-1 text-[9px] font-bold uppercase tracking-wider text-amber-700"><span className="h-1.5 w-1.5 rounded-full bg-amber-500" />Demo</span></div>
        <div className="bg-[#0c1220] p-4 font-mono text-[10px] leading-5 sm:p-5 sm:text-[11px]">{logs.map((entry, index) => <div key={`${entry.time}-${index}`} className="flex gap-2 border-b border-white/[.035] py-1 last:border-0"><span className="shrink-0 text-slate-500">{entry.time}</span><span className={`w-[76px] shrink-0 font-bold ${levelColor(entry.level)}`}>[{entry.level}]</span><span className="min-w-0 break-words text-slate-300">{entry.message}</span></div>)}<div className="mt-2 flex items-center gap-2 text-slate-500"><span className="h-3.5 w-1.5 animate-pulse bg-emerald-400" />Awaiting next event…</div></div>
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-2.5 text-[10px] text-slate-400"><span>Demo log stream · generated in this browser</span><button onClick={() => navigate("ai-swarm")} className="inline-flex items-center gap-1 font-semibold text-sky-700 hover:text-sky-800">View event pipeline <ArrowRight size={12} /></button></div>
      </section>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between"><div><p className="text-[10px] font-bold uppercase tracking-[.14em] text-slate-400">Identity assurance</p><p className="mt-1 text-xs font-semibold text-slate-800">Sample trust posture</p></div><ShieldCheck size={17} className="text-emerald-600" /></div><div className="mt-4 flex items-end justify-between"><div><span className="text-3xl font-bold tracking-tight">98.2</span><span className="ml-1 text-xs font-semibold text-slate-400">%</span><p className="mt-1 text-[10px] text-slate-500">Illustrative behavioral confidence</p></div><MiniSparkline /></div><div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-100"><div className="h-full w-[98.2%] rounded-full bg-emerald-500" /></div><div className="mt-2 flex justify-between text-[9px] text-slate-400"><span>0%</span><span>Example threshold 85%</span><span>100%</span></div></section>
        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-center justify-between"><div><p className="text-[10px] font-bold uppercase tracking-[.14em] text-slate-400">Cloud efficiency</p><p className="mt-1 text-xs font-semibold text-slate-800">Optimization opportunity</p></div><BarChart3 size={17} className="text-emerald-600" /></div><div className="mt-4 flex items-end justify-between"><div><span className="text-3xl font-bold tracking-tight">$3,186</span><p className="mt-1 text-[10px] text-slate-500">Monthly recoverable spend</p></div><span className="mb-1 inline-flex items-center gap-1 rounded-md bg-emerald-50 px-2 py-1 text-[9px] font-bold text-emerald-700"><ArrowDownRight size={11} /> 18.4%</span></div><div className="mt-4 flex h-8 items-end gap-1">{[32,45,38,60,48,70,57,82,65,78,55,95,72,86,67,100,78,90,73,96,80,100,88,100].map((height, index) => <span key={index} style={{ height: `${height}%` }} className={`flex-1 rounded-t-[2px] ${index > 18 ? "bg-emerald-500" : "bg-emerald-200"}`} />)}</div><button onClick={() => navigate("finops")} className="mt-3 text-[10px] font-semibold text-emerald-700 hover:text-emerald-800">Review cost opportunities <ArrowRight size={12} className="ml-1 inline" /></button></section>
      </div>
    </div>
    <div className="grid gap-4 lg:grid-cols-3">
      <QuickCard icon={Fingerprint} title="Continuous identity" status="1,420 sample sessions" detail="Illustrative identity score · no live decision" tone="sky" action={() => navigate("identity")} />
      <QuickCard icon={Network} title="Swarm consensus" status="ALLOW ACTIVE SESSION" detail="Sample votes · risk score 0.08" tone="emerald" action={() => navigate("ai-swarm")} />
      <QuickCard icon={ShieldAlert} title="Deception monitor" status="1 sample session" detail="Synthetic environments isolated" tone="rose" action={() => navigate("deception")} />
    </div>
  </>;
}

function FinOps({ resources, search, setSearch, remediate, totalSavings, dryRun, resetCandidates }: { resources: Resource[]; search: string; setSearch: (value: string) => void; remediate: (id: string) => void; totalSavings: number; dryRun: boolean; resetCandidates: () => void }) {
  return <>
    <PageIntro eyebrow="FinOps / Optimization" title="Cloud cost scanner" detail="Illustrative idle resource candidates · actions update this demo only and do not call AWS." action={<button onClick={resetCandidates} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-[11px] font-semibold text-slate-600 shadow-sm hover:bg-slate-50"><RefreshCw size={13} />Reset sample</button>} />
    <div className="grid gap-3 sm:grid-cols-3"><StatCard title="Recoverable monthly spend" value={money(totalSavings)} icon={CircleDollarSign} tone="emerald" footnote="Across current candidates" /><StatCard title="Idle candidates" value={`${resources.filter((r) => r.status === "IDLE").length}`} icon={Search} tone="amber" footnote="Policy eligible resources" /><StatCard title="Actions this scan" value={`${resources.filter((r) => r.status !== "IDLE").length}`} icon={CheckCircle2} tone="sky" footnote={dryRun ? "Simulated only" : "Enforce preview · demo only"} /></div>
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col justify-between gap-3 border-b border-slate-100 px-4 py-4 sm:flex-row sm:items-center sm:px-5"><div><h3 className="text-xs font-bold">Optimization candidates</h3><p className="mt-1 text-[10px] text-slate-400">Illustrative candidate data · no AWS scan has run</p></div><label className="relative block"><Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search resources…" className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-[11px] outline-none placeholder:text-slate-400 focus:border-sky-400 focus:ring-2 focus:ring-sky-100 sm:w-56" /></label></div>
      <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left"><thead className="bg-slate-50 text-[9px] font-bold uppercase tracking-[.12em] text-slate-400"><tr><th className="px-5 py-3">Resource</th><th className="px-4 py-3">Service / class</th><th className="px-4 py-3">Utilization</th><th className="px-4 py-3">Savings / mo</th><th className="px-4 py-3">Status</th><th className="px-5 py-3 text-right">Action</th></tr></thead>
        <tbody className="divide-y divide-slate-100">{resources.map((resource) => <tr key={resource.id} className="transition hover:bg-slate-50/70"><td className="px-5 py-3.5"><div className="flex items-center gap-3"><ResourceIcon service={resource.service} /><div><div className="font-mono text-[10px] font-semibold text-slate-800">{resource.id}</div><div className="mt-1 text-[10px] text-slate-400">{resource.name}</div></div></div></td><td className="px-4 py-3.5"><div className="text-[10px] font-semibold text-slate-700">{resource.service}</div><div className="mt-1 text-[9px] text-slate-400">{resource.spec}</div></td><td className="px-4 py-3.5"><span className="inline-flex items-center gap-1.5 text-[10px] text-slate-600"><span className="h-1.5 w-1.5 rounded-full bg-amber-400" />{resource.telemetry}</span></td><td className="px-4 py-3.5 font-mono text-[10px] font-semibold text-emerald-700">{money(resource.savings)}</td><td className="px-4 py-3.5"><ResourceStatusPill status={resource.status} /></td><td className="px-5 py-3.5 text-right"><button disabled={resource.status !== "IDLE"} onClick={() => remediate(resource.id)} className={`rounded-md px-2.5 py-1.5 text-[9px] font-bold transition ${resource.status !== "IDLE" ? "cursor-default bg-slate-100 text-slate-400" : dryRun ? "bg-amber-50 text-amber-700 hover:bg-amber-100" : "bg-rose-50 text-rose-700 hover:bg-rose-100"}`}>{resource.status === "IDLE" ? resource.action : resource.status === "SIMULATED" ? "Previewed" : "Submitted"}</button></td></tr>)}</tbody>
      </table>{resources.length === 0 && <div className="px-5 py-12 text-center text-xs text-slate-400">No resources match your search.</div>}</div>
      <div className="flex flex-col justify-between gap-2 border-t border-slate-100 px-5 py-3 text-[9px] text-slate-400 sm:flex-row"><span>Sample candidates · verify policy before any real remediation.</span><span className="inline-flex items-center gap-1"><LockKeyhole size={11} />{dryRun ? "Simulation mode · cloud resources unchanged" : "Enforce preview · UI state only, AWS integration not connected"}</span></div>
    </section>
  </>;
}

function Identity() {
  const dwell = [27,35,31,48,42,36,57,41,50,32,46,61,40,34,55,44,63,37,48,31,43,56,39,49,35,60,42,51,33,46,57,40];
  const flight = [45,38,52,34,60,43,31,55,40,48,36,63,42,51,32,46,57,39,49,35,61,43,30,54,40,47,36,59,42,33,50,38];
  return <>
    <PageIntro eyebrow="Zero Trust / Identity" title="Continuous identity" detail="Illustrative session, device, and behavioral signals based on the project brief. Identity and attestation services are not connected." action={<span className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1.5 text-[10px] font-bold text-amber-700"><span className="h-1.5 w-1.5 rounded-full bg-amber-500" />Sample session</span>} />
    <div className="grid gap-4 xl:grid-cols-[1.1fr_.9fr]">
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-start justify-between"><div><div className="flex items-center gap-2 text-xs font-bold"><UserRound size={15} className="text-sky-600" />Sample session</div><p className="mt-1 text-[10px] text-slate-400">Illustrative identity and access context</p></div><span className="rounded-md bg-amber-50 px-2 py-1 text-[9px] font-bold text-amber-700">EXAMPLE</span></div>
        <div className="mt-5 grid gap-4 sm:grid-cols-2"><Info label="Session ID" value="usr_mahesh_aladi_91a" mono /><Info label="Principal" value="Mahesh Aladi" /><Info label="Assigned role" value="DevOps Security Admin" /><Info label="Client location" value="Bengaluru, IN" /><Info label="Client IP" value="103.21.12.4" mono /><Info label="Session started" value="Today, 09:14:22 IST" /></div>
        <div className="mt-5 flex flex-wrap gap-2 border-t border-slate-100 pt-4"><Tag icon={Globe2} text="Public network" tone="sky" /><Tag icon={LockKeyhole} text="MFA satisfied" tone="emerald" /><Tag icon={Radio} text="TLS 1.3" tone="slate" /></div>
      </section>
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-center justify-between"><div><div className="flex items-center gap-2 text-xs font-bold"><ShieldCheck size={15} className="text-emerald-600" />Context integrity</div><p className="mt-1 text-[10px] text-slate-400">Illustrative transport and hardware signals</p></div><Shield size={17} className="text-slate-400" /></div>
        <div className="mt-5 space-y-3"><IntegrityRow icon={Network} label="TLS JA3 fingerprint" value="e7d705a3286e19ea42f587b344ee6865" detail="Example fingerprint · not validated" mono /><IntegrityRow icon={Fingerprint} label="WebAuthn / FIDO2 attestation" value="Apple Secure Enclave" detail="Example platform attestation · not validated" /><IntegrityRow icon={Shield} label="Credential binding" value="Passkey / platform" detail="Illustrative credential context" /></div>
      </section>
    </div>
    <div className="grid gap-4 xl:grid-cols-[.7fr_1.3fr]">
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-center justify-between"><div><p className="text-xs font-bold">Biometric match</p><p className="mt-1 text-[10px] text-slate-400">Illustrative ONNX autoencoder score</p></div><MoreHorizontal size={17} className="text-slate-400" /></div><div className="relative mx-auto mt-4 grid h-40 w-40 place-items-center"><svg className="absolute inset-0 -rotate-90" viewBox="0 0 160 160"><circle cx="80" cy="80" r="67" fill="none" stroke="#e2e8f0" strokeWidth="10"/><circle cx="80" cy="80" r="67" fill="none" stroke="#10b981" strokeWidth="10" strokeLinecap="round" strokeDasharray="413" strokeDashoffset="7.4"/></svg><div className="text-center"><div className="text-4xl font-bold tracking-tighter">98.2<span className="text-lg text-slate-400">%</span></div><div className="mt-1 text-[9px] font-bold uppercase tracking-widest text-emerald-700">Sample confidence</div></div></div><div className="mt-2 flex justify-between text-[9px] text-slate-400"><span>Threshold 85%</span><span>Example model</span></div></section>
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start"><div><div className="flex items-center gap-2 text-xs font-bold"><Waves size={15} className="text-sky-600" />Keystroke dynamics</div><p className="mt-1 text-[10px] text-slate-400">Sample dwell and flight timings · illustrative user baseline</p></div><div className="flex gap-3 text-[9px] font-semibold"><span className="flex items-center gap-1.5 text-sky-700"><i className="h-2 w-2 rounded-sm bg-sky-500" />Dwell T<sub>d</sub></span><span className="flex items-center gap-1.5 text-emerald-700"><i className="h-2 w-2 rounded-sm bg-emerald-500" />Flight T<sub>f</sub></span><span className="flex items-center gap-1.5 text-slate-400"><i className="h-[2px] w-3 border-t border-dashed border-slate-400" />Baseline</span></div></div>
        <div className="relative mt-5 h-40 border-b border-l border-slate-200 px-2"><div className="absolute inset-x-0 top-1/4 border-t border-dashed border-slate-200"/><div className="absolute inset-x-0 top-2/4 border-t border-dashed border-slate-200"/><div className="absolute inset-x-0 top-3/4 border-t border-dashed border-slate-200"/><div className="relative flex h-full items-end justify-between gap-[3px]">{dwell.map((height, index) => <div key={index} className="flex h-full flex-1 items-end justify-center gap-[2px]"><span title={`Dwell ${height}ms`} style={{ height: `${height}%` }} className="w-[42%] rounded-t-[2px] bg-sky-500/80"/><span title={`Flight ${flight[index]}ms`} style={{ height: `${flight[index]}%` }} className="w-[42%] rounded-t-[2px] bg-emerald-500/75"/></div>)}</div></div>
        <div className="mt-3 flex justify-between text-[9px] text-slate-400"><span>−32s</span><span>Keystroke interval · ms (normalized)</span><span>Now</span></div>
      </section>
    </div>
  </>;
}

function Swarm() {
  const [refresh, setRefresh] = useState(0);
  const agents = [
    { label: "Agent A", title: "Biometric specialist", icon: Fingerprint, color: "sky", score: "0.12", note: "Keystroke and mouse dynamics within baseline", signal: "LOW RISK", bars: [20,29,24,32,27,40,31,35,22,28,39,27] },
    { label: "Agent B", title: "Network context specialist", icon: Network, color: "blue", score: "0.05", note: "IP reputation clean · BGP stable · JA3 matches", signal: "LOW RISK", bars: [23,35,27,30,41,33,25,38,28,43,32,34] },
    { label: "Agent C", title: "CloudTrail specialist", icon: Cloud, color: "violet", score: "0.08", note: "No S3 object spike or unusual API calls", signal: "LOW RISK", bars: [30,25,38,28,34,22,40,31,27,35,24,33] },
  ];
  return <>
    <PageIntro eyebrow="AI Security / Consensus" title="Multi-agent swarm" detail="Illustrative agent votes show how independent risk signals feed the arbiter. No IAM action is connected." action={<button onClick={() => setRefresh((value) => value + 1)} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-[10px] font-semibold text-slate-600 shadow-sm hover:bg-slate-50"><RefreshCw size={13} />{refresh ? "Sample refreshed" : "Re-evaluate sample"}</button>} />
    <div className="grid gap-3 lg:grid-cols-3">{agents.map(({ label, title, icon: Icon, color, score, note, signal, bars }, index) => <section key={label} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between"><span className={`grid h-9 w-9 place-items-center rounded-lg ${color === "sky" ? "bg-sky-50 text-sky-600" : color === "blue" ? "bg-blue-50 text-blue-600" : "bg-violet-50 text-violet-600"}`}><Icon size={17} /></span><span className="rounded-md bg-emerald-50 px-2 py-1 text-[9px] font-bold tracking-wide text-emerald-700">{signal}</span></div><div className="mt-4 text-[10px] font-bold uppercase tracking-[.12em] text-slate-400">{label}</div><h3 className="mt-1 text-sm font-bold">{title}</h3><p className="mt-2 min-h-[30px] text-[10px] leading-5 text-slate-500">{note}</p><div className="mt-4 flex items-end justify-between border-t border-slate-100 pt-3"><div><span className="text-[9px] text-slate-400">Risk score</span><div className="font-mono text-lg font-bold text-slate-800">{score}</div></div><div className="flex h-7 items-end gap-1">{bars.map((height, i) => <i key={i} style={{ height: `${height}px` }} className={`w-1 rounded-t-sm ${index === 0 ? "bg-sky-400" : index === 1 ? "bg-blue-400" : "bg-violet-400"}`} />)}</div></div></section>)}</div>
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"><div className="border-b border-slate-100 px-5 py-4"><div className="flex items-center gap-2 text-xs font-bold"><Users size={15} className="text-slate-500" />Agent D · Arbiter decision</div><p className="mt-1 text-[10px] text-slate-400">Sample Weighted Threshold Consensus · no IAM revocation is issued from this demo</p></div><div className="grid gap-6 p-5 md:grid-cols-[1fr_auto_1fr] md:items-center md:gap-8"><div><div className="flex items-center justify-between text-[10px]"><span className="font-semibold text-slate-600">Aggregate risk score</span><span className="font-mono font-bold text-emerald-700">0.08 / 1.00</span></div><div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full w-[8%] rounded-full bg-emerald-500" /></div><div className="mt-2 flex justify-between text-[9px] text-slate-400"><span>Low risk</span><span>Review 0.60</span><span>Revoke 0.85</span></div></div><div className="hidden h-12 border-l border-slate-200 md:block"/><div className="flex items-center gap-3 rounded-lg border border-emerald-100 bg-emerald-50/70 p-3.5"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-700"><Check size={17} strokeWidth={2.5} /></span><div><div className="text-[10px] font-bold uppercase tracking-[.12em] text-emerald-700">Allow active session</div><div className="mt-1 text-[9px] text-emerald-800/70">3 of 3 agents agree · no remediation required</div></div></div></div><div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-slate-100 bg-slate-50/70 px-5 py-3 text-[9px] text-slate-500"><span>Decision ID <b className="ml-1 font-mono text-slate-700">arb-7f32a1</b></span><span>Policy <b className="ml-1 text-slate-700">zt-session-v3</b></span><span>Evaluation <b className="ml-1 text-slate-700">38 ms</b></span><span className="ml-auto text-emerald-700">Illustrative decision · no IAM action connected</span></div></section>
    <div className="grid gap-3 sm:grid-cols-3"><StatCard title="Agents active" value="3 / 3" icon={Radio} tone="sky" footnote="Sample agent status"/><StatCard title="Consensus threshold" value="0.85" icon={Gauge} tone="amber" footnote="Weighted revocation score"/><StatCard title="Voting model" value="2 votes" icon={ShieldCheck} tone="emerald" footnote="Illustrative threshold rule"/></div>
  </>;
}

function Deception() {
  const [events, setEvents] = useState<DeceptionEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;
    const loadEvents = async () => {
      try {
        const response = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080"}/api/v1/sandbox/events?limit=100`, {
          credentials: "include", cache: "no-store", headers: { accept: "application/json" },
        });
        if (response.status === 404) throw new Error("Events route not found. Restart the backend from the updated BACKEND folder.");
        if (response.status === 401) throw new Error("Your session is no longer valid. Sign in again.");
        if (!response.ok) throw new Error(`Backend returned HTTP ${response.status}`);
        const result = await response.json() as DeceptionEvent[];
        if (active) { setEvents(result); setError(""); }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "Could not load honeypot events");
      } finally {
        if (active) setLoading(false);
      }
    };
    void loadEvents();
    const timer = window.setInterval(loadEvents, 10000);
    return () => { active = false; window.clearInterval(timer); };
  }, [refreshKey]);

  return <>
    <PageIntro eyebrow="Threat response / Deception" title="Honeypot sandbox" detail="Backend-captured requests routed to synthetic endpoints. Activity refreshes every 10 seconds." action={<span className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-[10px] font-bold ${error ? "bg-amber-50 text-amber-700" : "bg-emerald-50 text-emerald-700"}`}><span className={`h-1.5 w-1.5 rounded-full ${error ? "bg-amber-500" : "bg-emerald-500"}`} />{error ? "Backend unavailable" : `${events.length} captured events`}</span>} />
    <div className="grid gap-3 sm:grid-cols-3"><StatCard title="Captured requests" value={`${events.length}`} icon={ShieldAlert} tone="rose" footnote="Current in-memory window"/><StatCard title="Known repeat violations" value={`${events.reduce((sum, event) => sum + Math.max(0, event.violationCount - 1), 0)}`} icon={Terminal} tone="amber" footnote="From matching fingerprints"/><StatCard title="Production actions" value="0" icon={ShieldCheck} tone="emerald" footnote="Synthetic handlers only"/></div>
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"><div className="flex items-center justify-between border-b border-slate-100 px-4 py-4 sm:px-5"><div><h3 className="text-xs font-bold">Captured sandbox activity</h3><p className="mt-1 text-[10px] text-slate-400">Payload and headers are bounded and credential-redacted · backend memory store</p></div><button type="button" onClick={() => { setLoading(true); setRefreshKey((value) => value + 1); }} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50"><RefreshCw size={12}/>Refresh</button></div>
      {error && <div role="alert" className="m-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[10px] text-amber-800">Could not load backend events: {error}</div>}
      <div className="overflow-x-auto"><table className="w-full min-w-[850px] text-left"><thead className="bg-slate-50 text-[9px] font-bold uppercase tracking-[.12em] text-slate-400"><tr><th className="px-5 py-3">Source IP</th><th className="px-4 py-3">Request</th><th className="px-4 py-3">Captured at</th><th className="px-4 py-3">Fingerprint</th><th className="px-5 py-3">Captured request details</th></tr></thead><tbody className="divide-y divide-slate-100">{events.map((event) => <tr key={event.logId} className="align-top hover:bg-slate-50/70"><td className="px-5 py-3 font-mono text-[10px] font-semibold text-slate-800">{event.ipAddress}</td><td className="px-4 py-3"><div className="font-mono text-[10px] font-bold text-rose-700">{event.httpMethod}</div><div className="mt-1 max-w-64 break-all font-mono text-[9px] text-slate-500">{event.attemptedEndpoint}</div><div className="mt-1 text-[9px] text-slate-400">{event.violationCount} matching violation{event.violationCount === 1 ? "" : "s"}</div></td><td className="px-4 py-3 whitespace-nowrap text-[10px] text-slate-500">{new Date(event.timestamp).toLocaleString()}</td><td className="px-4 py-3 font-mono text-[9px] text-slate-500">{event.compositeHash.slice(0, 16)}…</td><td className="max-w-md px-5 py-3"><details><summary className="cursor-pointer text-[10px] font-semibold text-sky-700">Show redacted capture</summary><pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-slate-950 p-3 font-mono text-[9px] leading-4 text-slate-200">{event.payloadCaptured}</pre></details></td></tr>)}</tbody></table>
        {!loading && events.length === 0 && !error && <div className="px-5 py-12 text-center"><ShieldCheck size={20} className="mx-auto text-slate-300"/><p className="mt-2 text-xs font-semibold text-slate-700">No backend events captured yet</p><p className="mt-1 text-[10px] text-slate-400">Synthetic sandbox requests and diverted threat requests will appear here.</p></div>}
        {loading && events.length === 0 && <div className="px-5 py-12 text-center text-xs text-slate-400">Loading backend activity…</div>}
      </div><div className="border-t border-slate-100 px-5 py-3 text-[9px] text-slate-400">Events are retained in backend memory only and disappear when the backend restarts.</div></section>
  </>;
}

function EnterpriseGovernance() {
  const backend = (process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const [adminKey, setAdminKey] = useState("");
  const [accountId, setAccountId] = useState("");
  const [userId, setUserId] = useState("");
  const [roleArn, setRoleArn] = useState("");
  const [reason, setReason] = useState("Suspicious continuous identity signals");
  const [riskLevel, setRiskLevel] = useState("HIGH");
  const [compliance, setCompliance] = useState<ComplianceResult | null>(null);
  const [blastRadius, setBlastRadius] = useState<BlastRadiusResult | null>(null);
  const [canary, setCanary] = useState<CanaryResult | null>(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${backend}${path}`, {
      ...init,
      credentials: "include",
      cache: "no-store",
      headers: {
        accept: "application/json",
        "X-GreenOps-Admin-Key": adminKey,
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
    });
    if (!response.ok) {
      let detail = `Backend returned HTTP ${response.status}`;
      try {
        const body = await response.json() as { detail?: string; message?: string; title?: string };
        detail = body.detail ?? body.message ?? body.title ?? detail;
      } catch { /* Keep the HTTP status when the response is not JSON. */ }
      if (response.status === 401) detail = "Your GreenOps sign-in session expired. Sign in again.";
      if (response.status === 403) detail = "Admin key was rejected or enterprise admin access is not configured.";
      throw new Error(detail);
    }
    const body = await response.text();
    return body ? JSON.parse(body) as T : undefined as T;
  }

  async function run<T>(name: string, work: () => Promise<T>, onSuccess: (result: T) => void, successMessage: string) {
    setBusy(name); setError(""); setMessage("");
    try { onSuccess(await work()); setMessage(successMessage); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Enterprise request failed"); }
    finally { setBusy(""); }
  }

  const buttonClass = "inline-flex items-center justify-center gap-2 rounded-lg bg-slate-900 px-3.5 py-2.5 text-[11px] font-semibold text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50";
  const inputClass = "w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[11px] text-slate-800 outline-none placeholder:text-slate-400 focus:border-sky-400 focus:ring-2 focus:ring-sky-100";

  return <>
    <PageIntro eyebrow="Enterprise / Governance" title="Cloud security posture" detail="Live AWS compliance checks, role access simulation, canary credentials, and CAEP session events." action={<span className="inline-flex items-center gap-2 rounded-full bg-sky-50 px-3 py-1.5 text-[10px] font-bold text-sky-700"><Radio size={12}/>Backend connected on demand</span>} />
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex items-start gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-600"><KeyRound size={16}/></span><div className="min-w-0 flex-1"><h3 className="text-xs font-bold">Enterprise administrator access</h3><p className="mt-1 max-w-3xl text-[10px] leading-5 text-slate-500">Enter the key configured as <code className="font-mono">GREENOPS_ENTERPRISE_ADMIN_API_KEY</code>. It stays in this page’s memory and is sent in request headers; it is not saved in browser storage.</p><label className="mt-3 block max-w-xl"><span className="sr-only">Enterprise admin key</span><input type="password" autoComplete="off" value={adminKey} onChange={(event) => setAdminKey(event.target.value)} placeholder="Enterprise admin key" className={inputClass}/></label></div></div>
    </section>
    {(error || message) && <div role={error ? "alert" : "status"} className={`rounded-lg border px-3 py-2.5 text-[11px] ${error ? "border-rose-200 bg-rose-50 text-rose-700" : "border-emerald-200 bg-emerald-50 text-emerald-700"}`}>{error || message}</div>}

    <div className="grid gap-4 xl:grid-cols-2">
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex items-start justify-between"><div><h3 className="text-xs font-bold">Compliance evaluation</h3><p className="mt-1 text-[10px] text-slate-500">S3 public access block, EBS/RDS encryption, root MFA</p></div><ClipboardCheck size={17} className="text-emerald-600"/></div>
        <div className="mt-4 flex flex-col gap-2 sm:flex-row"><input value={accountId} onChange={(event) => setAccountId(event.target.value)} placeholder="12-digit AWS account ID" maxLength={12} inputMode="numeric" className={inputClass}/><button className={buttonClass} disabled={!adminKey || busy !== "" || accountId.length !== 12} onClick={() => void run("compliance", () => api<ComplianceResult>(`/api/v1/enterprise/compliance/score?awsAccountId=${encodeURIComponent(accountId)}`, { method: "GET" }), setCompliance, "Compliance evaluation completed.")}>{busy === "compliance" ? "Evaluating…" : "Evaluate posture"}</button></div>
        {compliance && <div className="mt-4 rounded-lg border border-slate-100 bg-slate-50 p-3"><div className="flex items-end justify-between"><div><div className="text-[9px] font-semibold uppercase tracking-wider text-slate-400">{compliance.framework} score</div><div className="mt-1 text-3xl font-bold">{compliance.complianceScorePercentage.toFixed(1)}<span className="ml-1 text-sm text-slate-400">%</span></div></div><span className="text-[9px] text-slate-400">{new Date(compliance.evaluatedAt).toLocaleString()}</span></div><div className="mt-3 grid gap-3 sm:grid-cols-2"><div><div className="text-[9px] font-bold uppercase text-emerald-700">Passed controls</div><ul className="mt-1 space-y-1">{compliance.passedRules.map((rule) => <li key={rule} className="text-[10px] text-slate-600">✓ {rule}</li>)}</ul></div><div><div className="text-[9px] font-bold uppercase text-rose-700">Findings</div>{compliance.violations.length ? <ul className="mt-1 space-y-1">{compliance.violations.map((item) => <li key={item} className="break-all font-mono text-[9px] text-slate-600">{item}</li>)}</ul> : <p className="mt-1 text-[10px] text-slate-500">No violations returned.</p>}</div></div></div>}
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex items-start justify-between"><div><h3 className="text-xs font-bold">IAM blast radius</h3><p className="mt-1 text-[10px] text-slate-500">Simulates role permissions against discovered resources (up to 100 per type)</p></div><Network size={17} className="text-sky-600"/></div>
        <div className="mt-4 grid gap-2 sm:grid-cols-2"><input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="User ID" className={inputClass}/><input value={roleArn} onChange={(event) => setRoleArn(event.target.value)} placeholder="arn:aws:iam::…:role/…" className={`${inputClass} font-mono`}/></div><button className={`${buttonClass} mt-2 w-full sm:w-auto`} disabled={!adminKey || !userId || !roleArn || busy !== ""} onClick={() => void run("blast", () => api<BlastRadiusResult>(`/api/v1/enterprise/blast-radius/${encodeURIComponent(userId)}?iamRoleArn=${encodeURIComponent(roleArn)}`), setBlastRadius, "IAM policy simulation completed.")}>{busy === "blast" ? "Simulating…" : "Calculate blast radius"}</button>
        {blastRadius && <div className="mt-4"><div className="grid grid-cols-2 gap-2 sm:grid-cols-4"><StatCard title="S3 buckets" value={`${blastRadius.accessibleS3Buckets}`} icon={Cloud} tone="sky" footnote="Accessible by role"/><StatCard title="RDS databases" value={`${blastRadius.accessibleRdsDatabases}`} icon={Database} tone="amber" footnote="Visible by role"/><StatCard title="EC2 instances" value={`${blastRadius.accessibleEc2Instances}`} icon={Server} tone="rose" footnote="Visible by role"/><StatCard title="Estimated exposure" value={money(blastRadius.quantifiedRiskExposureUsd)} icon={CircleDollarSign} tone="emerald" footnote="Configured estimate"/></div><div className="mt-3 max-h-28 overflow-auto rounded-lg bg-slate-50 p-3"><div className="text-[9px] font-bold uppercase tracking-wider text-slate-400">Critical resource ARNs</div>{blastRadius.criticalResourceArns.length ? blastRadius.criticalResourceArns.map((arn) => <div key={arn} className="mt-1 break-all font-mono text-[9px] text-slate-600">{arn}</div>) : <p className="mt-1 text-[10px] text-slate-500">No accessible resources returned.</p>}</div></div>}
      </section>
    </div>

    <div className="grid gap-4 xl:grid-cols-2">
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between"><div><h3 className="text-xs font-bold">Generate canary credential</h3><p className="mt-1 text-[10px] text-slate-500">Creates an inert marker and callback capability for synthetic data.</p></div><ShieldAlert size={17} className="text-rose-600"/></div><div className="mt-4 flex flex-col gap-2 sm:flex-row"><input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="Associated session reference" className={inputClass}/><button className={buttonClass} disabled={!adminKey || !userId || busy !== ""} onClick={() => void run("canary", () => api<CanaryResult>("/api/v1/enterprise/canary/tokens", { method: "POST", body: JSON.stringify({ sessionId: userId }) }), setCanary, "Canary credential generated.")}>{busy === "canary" ? "Generating…" : "Generate marker"}</button></div>{canary && <div className="mt-3 rounded-lg border border-amber-100 bg-amber-50/60 p-3"><div className="text-[9px] font-bold uppercase tracking-wider text-amber-800">Inert canary marker · not an AWS credential</div><div className="mt-2 font-mono text-xs font-bold text-slate-800">{canary.fakeAccessKey}</div><div className="mt-1 break-all font-mono text-[9px] text-slate-500">Token {canary.tokenId}</div><div className="mt-2 break-all text-[9px] text-slate-600">Callback: {canary.callbackUrl || "Not configured"}</div><p className="mt-2 text-[9px] text-slate-500">Use only in synthetic or decoy content. Callback state is kept in backend memory.</p></div>}</section>

      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between"><div><h3 className="text-xs font-bold">CAEP session revocation</h3><p className="mt-1 text-[10px] text-slate-500">Sends a signed session-revoked event to configured receivers.</p></div><Send size={17} className="text-rose-600"/></div><div className="mt-4 grid gap-2 sm:grid-cols-2"><input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="User ID / subject" className={inputClass}/><select value={riskLevel} onChange={(event) => setRiskLevel(event.target.value)} className={inputClass}><option>HIGH</option><option>CRITICAL</option><option>MEDIUM</option><option>LOW</option></select></div><textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={512} rows={2} placeholder="Revocation reason" className={`${inputClass} mt-2 resize-y`}/><button className="mt-2 inline-flex items-center justify-center gap-2 rounded-lg bg-rose-600 px-3.5 py-2.5 text-[11px] font-semibold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={!adminKey || !userId || !reason || busy !== ""} onClick={() => { if (window.confirm(`Send a CAEP session-revoked event for ${userId}? This can sign the user out of connected identity providers.`)) void run("caep", () => api<void>("/api/v1/enterprise/caep/revoke-session", { method: "POST", body: JSON.stringify({ userId, reason, riskLevel }) }), () => undefined, "CAEP event accepted by configured receiver(s)."); }}>{busy === "caep" ? "Sending event…" : "Send session-revoked event"}</button><p className="mt-2 text-[9px] leading-4 text-slate-400">This action can end active sessions. Delivery requires the receiver to trust the configured CAEP issuer and signing key.</p></section>
    </div>
  </>;
}

function EcoSecurity() {
  const tiers = [
    { id: "01", title: "Rule-based checks", level: "LOW RISK", traffic: "88%", count: "124,860", compute: "0.1 ms", detail: "Trusted device · stable network · standard APIs", icon: Zap, color: "emerald", width: "88%" },
    { id: "02", title: "ONNX biometric model", level: "MEDIUM RISK", traffic: "10%", count: "14,192", compute: "12 ms", detail: "New context · behavioral drift · step-up candidate", icon: Cpu, color: "amber", width: "10%" },
    { id: "03", title: "Autonomous swarm inference", level: "HIGH RISK", traffic: "2%", count: "2,842", compute: "150 ms", detail: "Sensitive endpoint · threat signals · consensus required", icon: Network, color: "rose", width: "2%" },
  ];
  return <>
    <PageIntro eyebrow="GreenOps / Adaptive security" title="Eco-security scaler" detail="Illustrative tier distribution and per-request compute from the project brief. Adaptive routing is not connected." action={<span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-[10px] font-bold text-emerald-700"><Leaf size={12}/>Sample distribution</span>} />
    <div className="grid gap-3 sm:grid-cols-3"><StatCard title="Requests evaluated" value="141,894" icon={Activity} tone="sky" footnote="Illustrative 24-hour sample"/><StatCard title="Fast-path traffic" value="88%" icon={Zap} tone="emerald" footnote="Rule-based checks"/><StatCard title="Traffic at fast path" value="88%" icon={Leaf} tone="emerald" footnote="Illustrative rule-based tier"/></div>
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-start"><div><h3 className="text-xs font-bold">Security execution distribution</h3><p className="mt-1 text-[10px] text-slate-400">Illustrative tier split · project brief values</p></div><button className="inline-flex items-center gap-1 text-[10px] font-semibold text-slate-500">Last 24 hours <ChevronDown size={13}/></button></div><div className="mt-5 flex h-3 overflow-hidden rounded-full bg-slate-100"><div className="bg-emerald-500" style={{width:"88%"}}/><div className="bg-amber-400" style={{width:"10%"}}/><div className="bg-rose-500" style={{width:"2%"}}/></div><div className="mt-2 flex flex-wrap justify-between gap-2 text-[9px] text-slate-400"><span>Tier 1 <b className="text-emerald-700">88%</b></span><span>Tier 2 <b className="text-amber-700">10%</b></span><span>Tier 3 <b className="text-rose-700">2%</b></span></div></section>
    <div className="grid gap-3 xl:grid-cols-3">{tiers.map(({id,title,level,traffic,count,compute,detail,icon:Icon,color,width})=><section key={id} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-start justify-between"><span className={`grid h-10 w-10 place-items-center rounded-xl ${color === "emerald" ? "bg-emerald-50 text-emerald-600" : color === "amber" ? "bg-amber-50 text-amber-600" : "bg-rose-50 text-rose-600"}`}><Icon size={18}/></span><span className={`rounded-md px-2 py-1 text-[9px] font-bold tracking-wide ${color === "emerald" ? "bg-emerald-50 text-emerald-700" : color === "amber" ? "bg-amber-50 text-amber-700" : "bg-rose-50 text-rose-700"}`}>{level}</span></div><div className="mt-4 text-[9px] font-bold uppercase tracking-[.14em] text-slate-400">Tier {id} · {traffic} traffic</div><h3 className="mt-1 text-sm font-bold">{title}</h3><p className="mt-1 min-h-[32px] text-[10px] leading-5 text-slate-500">{detail}</p><div className="mt-4 grid grid-cols-2 gap-3 border-t border-slate-100 pt-3"><div><div className="text-[9px] text-slate-400">Requests</div><div className="mt-1 font-mono text-sm font-bold">{count}</div></div><div><div className="text-[9px] text-slate-400">Compute / request</div><div className="mt-1 font-mono text-sm font-bold">{compute}</div></div></div><div className="mt-4 h-1.5 rounded-full bg-slate-100"><div style={{width}} className={`h-full rounded-full ${color === "emerald" ? "bg-emerald-500" : color === "amber" ? "bg-amber-400" : "bg-rose-500"}`}/></div></section>)}</div>
    <section className="rounded-xl border border-sky-100 bg-sky-50/60 p-4 sm:p-5"><div className="flex items-start gap-3"><span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-sky-100 text-sky-700"><Gauge size={16}/></span><div><h3 className="text-xs font-bold text-sky-900">Security policy remains the control plane</h3><p className="mt-1 text-[10px] leading-5 text-sky-800/75">The tier breakdown demonstrates the proposed cost-aware security design. This sample UI does not route requests or change authorization decisions.</p></div></div></section>
  </>;
}

function PageIntro({ eyebrow, title, detail, action }: { eyebrow: string; title: string; detail: string; action?: React.ReactNode }) { return <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end"><div><div className="text-[9px] font-bold uppercase tracking-[.16em] text-slate-400">{eyebrow}</div><h2 className="mt-1.5 text-xl font-bold tracking-tight sm:text-2xl">{title}</h2><p className="mt-1 max-w-2xl text-[11px] leading-5 text-slate-500">{detail}</p></div>{action}</div>; }

function Kpi({ icon: Icon, label, value, change, tone, note }: { icon: typeof Activity; label: string; value: string; change: string; tone: "emerald" | "sky" | "rose" | "amber"; note: string }) {
  const classes = tone === "emerald" ? "bg-emerald-50 text-emerald-600" : tone === "sky" ? "bg-sky-50 text-sky-600" : tone === "rose" ? "bg-rose-50 text-rose-600" : "bg-amber-50 text-amber-600";
  return <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between"><span className="text-[10px] font-semibold text-slate-500">{label}</span><span className={`grid h-8 w-8 place-items-center rounded-lg ${classes}`}><Icon size={16}/></span></div><div className="mt-3 text-[22px] font-bold tracking-tight text-slate-900">{value}</div><div className="mt-2 flex items-center gap-1.5 text-[9px]"><span className={`inline-flex items-center gap-0.5 rounded px-1 py-0.5 font-bold ${tone === "rose" ? "bg-rose-50 text-rose-700" : tone === "amber" ? "bg-amber-50 text-amber-700" : "bg-emerald-50 text-emerald-700"}`}><ArrowUpRight size={10}/>{change}</span><span className="text-slate-400">{note}</span></div></section>;
}

function StatCard({ title, value, icon: Icon, tone, footnote }: { title: string; value: string; icon: typeof Activity; tone: "emerald" | "sky" | "rose" | "amber"; footnote: string }) {
  const color = tone === "emerald" ? "bg-emerald-50 text-emerald-600" : tone === "sky" ? "bg-sky-50 text-sky-600" : tone === "rose" ? "bg-rose-50 text-rose-600" : "bg-amber-50 text-amber-600";
  return <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"><div className="flex items-center justify-between"><span className="text-[10px] font-semibold text-slate-500">{title}</span><span className={`grid h-8 w-8 place-items-center rounded-lg ${color}`}><Icon size={15}/></span></div><div className="mt-3 text-xl font-bold tracking-tight">{value}</div><div className="mt-1 text-[9px] text-slate-400">{footnote}</div></section>;
}

function QuickCard({ icon: Icon, title, status, detail, tone, action }: { icon: typeof Activity; title: string; status: string; detail: string; tone: "emerald" | "sky" | "rose"; action: () => void }) {
  const iconColor = tone === "emerald" ? "bg-emerald-50 text-emerald-600" : tone === "sky" ? "bg-sky-50 text-sky-600" : "bg-rose-50 text-rose-600";
  const statusColor = tone === "emerald" ? "text-emerald-700" : tone === "sky" ? "text-sky-700" : "text-rose-700";
  return <button onClick={action} className="group flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow"><span className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg ${iconColor}`}><Icon size={17}/></span><span className="min-w-0 flex-1"><span className="block text-[9px] font-semibold text-slate-400">{title}</span><span className={`mt-1 block truncate text-[11px] font-bold ${statusColor}`}>{status}</span><span className="mt-1 block truncate text-[9px] text-slate-400">{detail}</span></span><ArrowRight size={14} className="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-slate-500"/></button>;
}

function MiniSparkline() { return <div className="flex h-10 items-end gap-1">{[16,23,18,28,22,31,24,34,26,38,31,40,33,42,36,49,42,54,46,59,51,62,56,70].map((height, index) => <i key={index} style={{height:`${height}%`}} className={`w-1 rounded-t-sm ${index > 17 ? "bg-emerald-500" : "bg-emerald-200"}`}/>)}</div>; }
function levelColor(level: LogEntry["level"]) { return level === "SUCCESS" ? "text-emerald-400" : level === "WARN" ? "text-amber-400" : level === "AUTH" ? "text-sky-400" : level === "SIMULATION" ? "text-violet-400" : "text-slate-400"; }
function ResourceIcon({ service }: { service: string }) { const Icon = service === "EC2" ? Server : service === "EBS" ? HardDrive : service === "RDS" ? Database : Network; return <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-500"><Icon size={15}/></span>; }
function ResourceStatusPill({ status }: { status: ResourceStatus }) { const cls = status === "IDLE" ? "bg-amber-50 text-amber-700" : status === "SIMULATED" ? "bg-sky-50 text-sky-700" : "bg-emerald-50 text-emerald-700"; return <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-[8px] font-bold tracking-wide ${cls}`}><span className="h-1 w-1 rounded-full bg-current"/>{status}</span>; }
function Info({ label, value, mono }: { label: string; value: string; mono?: boolean }) { return <div><div className="text-[9px] font-semibold uppercase tracking-[.1em] text-slate-400">{label}</div><div className={`mt-1 break-all text-[11px] font-semibold text-slate-800 ${mono ? "font-mono" : ""}`}>{value}</div></div>; }
function Tag({ icon: Icon, text, tone }: { icon: typeof Activity; text: string; tone: "emerald" | "sky" | "slate" }) { const cls = tone === "emerald" ? "bg-emerald-50 text-emerald-700" : tone === "sky" ? "bg-sky-50 text-sky-700" : "bg-slate-100 text-slate-600"; return <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[9px] font-semibold ${cls}`}><Icon size={11}/>{text}</span>; }
function IntegrityRow({ icon: Icon, label, value, detail, mono }: { icon: typeof Activity; label: string; value: string; detail: string; mono?: boolean }) { return <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3"><span className="mt-0.5 text-slate-400"><Icon size={14}/></span><div className="min-w-0 flex-1"><div className="flex flex-col justify-between gap-1 sm:flex-row"><span className="text-[9px] font-semibold text-slate-500">{label}</span><span className="inline-flex items-center gap-1 text-[8px] font-bold text-emerald-700"><CheckCircle2 size={10}/>SAMPLE</span></div><div className={`mt-1 break-all text-[10px] font-semibold text-slate-800 ${mono ? "font-mono" : ""}`}>{value}</div><div className="mt-1 text-[9px] text-slate-400">{detail}</div></div></div>; }
function AttackerRow({ ip, session, target, duration, exploit }: { ip: string; session: string; target: string; duration: string; exploit: string }) { return <tr className="hover:bg-slate-50/70"><td className="px-5 py-4"><div className="flex items-center gap-2"><span className="h-1.5 w-1.5 rounded-full bg-rose-500"/><span className="font-mono text-[10px] font-semibold text-slate-800">{ip}</span></div><div className="mt-1 pl-3.5 text-[9px] text-slate-400">Sample IP reputation · unverified</div></td><td className="px-4 py-4 font-mono text-[10px] text-slate-600">{session}</td><td className="px-4 py-4"><span className="inline-flex items-center gap-1.5 text-[10px] font-semibold text-slate-700"><Database size={12} className="text-amber-500"/>{target}</span><div className="mt-1 text-[9px] text-slate-400">Synthetic environment</div></td><td className="px-4 py-4 font-mono text-[10px] text-slate-600">{duration}</td><td className="px-4 py-4"><span className="inline-flex items-center gap-1 rounded-md bg-rose-50 px-2 py-1 text-[9px] font-semibold text-rose-700"><AlertTriangle size={10}/>{exploit}</span></td><td className="px-5 py-4"><span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-1 text-[8px] font-bold text-emerald-700"><LockKeyhole size={9}/>DEMO</span></td></tr>; }
