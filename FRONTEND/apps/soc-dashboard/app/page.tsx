"use client";

import { useState } from "react";
import type { ReactNode } from "react";
import { Activity, Cloud, LayoutDashboard, ShieldAlert, ShieldCheck, Settings, Terminal } from "lucide-react";
import { RiskGauge } from "../components/RiskGauge";
import { DualBranchVisualizer } from "../components/DualBranchVisualizer";
import { AuditTimeline } from "../components/AuditTimeline";
import { useRiskStream } from "../lib/useRiskStream";

export default function DashboardPage(): ReactNode {
  const [section, setSection] = useState("Overview");
  const risk = useRiskStream();
  const nav = [
    { label: "Overview", icon: LayoutDashboard },
    { label: "Identity risk", icon: ShieldAlert },
    { label: "Cloud remediation", icon: Cloud },
    { label: "Audit timeline", icon: Terminal },
    { label: "Settings", icon: Settings }
  ];
  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="logo"><span>G</span> GREENOPS</div>
        <p className="eyebrow">SECURITY OPERATIONS</p>
        <nav>{nav.map(({ label, icon: Icon }) => <button className={section === label ? "nav active" : "nav"} key={label} onClick={() => setSection(label)}><Icon size={17} />{label}</button>)}</nav>
        <div className="connection"><span className="dot" /> Event stream connected <small>WebSocket · 42ms</small></div>
        <div className="operator"><span className="avatar">AM</span><div><strong>Alex Morgan</strong><small>Platform engineer</small></div></div>
      </aside>
      <section className="workspace">
        <header className="topbar"><div><p className="eyebrow">PRODUCTION / {section.toUpperCase()}</p><h1>Zero Trust control center</h1></div><div className="top-actions"><span className="healthy"><ShieldCheck size={15} /> Protected</span><button className="icon-button" aria-label="Notifications"><Activity size={18} /></button></div></header>
        <div className="content">
          <section className="hero"><div><span className="badge orange">LIVE RISK MONITORING</span><h2>Continuous authentication is active.</h2><p>Identity, transport context, and autonomous remediation signals are evaluated in real time.</p></div><div className="hero-metric"><span>Last event</span><strong>{new Date(risk.timestamp).toLocaleTimeString()}</strong><small>{risk.sequence} events received</small></div></section>
          <section className="grid two"><RiskGauge event={risk} /><DualBranchVisualizer event={risk} /></section>
          <AuditTimeline />
        </div>
      </section>
    </main>
  );
}
