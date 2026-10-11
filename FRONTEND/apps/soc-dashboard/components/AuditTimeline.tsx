import { CheckCircle2, Clock3, LockKeyhole, XCircle } from "lucide-react";
import type { ReactNode } from "react";
import type { AuditEntry } from "@greenops/sdk";

const actions: readonly AuditEntry[] = [
  { id: "audit-1", action: "PutUserPolicy", detail: "Explicit deny policy prepared", timestamp: "2 min ago", status: "SUCCESS" },
  { id: "audit-2", action: "STS credential invalidation", detail: "aws:TokenIssueTime condition applied", timestamp: "4 min ago", status: "SUCCESS" },
  { id: "audit-3", action: "EventBridge CAEP", detail: "session-revoked SET broadcast", timestamp: "7 min ago", status: "PENDING" }
];

const iconFor = (status: AuditEntry["status"]): ReactNode => status === "SUCCESS" ? <CheckCircle2 size={16} /> : status === "FAILED" ? <XCircle size={16} /> : <Clock3 size={16} />;

export function AuditTimeline(): ReactNode {
  return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-start justify-between gap-4"><div><p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">AWS SDK v2 control plane</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Remediation audit timeline</h2></div><button className="text-xs font-semibold text-indigo-600 hover:text-indigo-800" type="button">View all audits <span aria-hidden="true">→</span></button></div><div className="mt-5 divide-y divide-slate-100">{actions.map((entry) => <div className="flex items-start gap-3 py-4 first:pt-0 last:pb-0" key={entry.id}><span className={`mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full ${entry.status === "SUCCESS" ? "bg-emerald-50 text-emerald-600" : entry.status === "FAILED" ? "bg-rose-50 text-rose-600" : "bg-amber-50 text-amber-600"}`}>{iconFor(entry.status)}</span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><strong className="text-sm font-semibold text-slate-800">{entry.action}</strong><span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-500">{entry.status}</span></div><p className="mt-1 text-xs text-slate-500">{entry.detail}</p></div><time className="shrink-0 text-xs text-slate-400">{entry.timestamp}</time></div>)}</div><div className="mt-5 flex items-center gap-2 border-t border-slate-100 pt-4 text-xs text-slate-500"><LockKeyhole size={14} className="text-slate-400" /> Guardrails protect every autonomous action.</div></article>;
}
