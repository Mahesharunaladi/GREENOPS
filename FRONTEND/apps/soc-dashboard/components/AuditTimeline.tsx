import { CheckCircle2, Clock3, LockKeyhole } from "lucide-react";
import type { ReactNode } from "react";

const actions = [
  ["PutUserPolicy", "Explicit deny policy prepared", "2 min ago", "success"],
  ["STS credential invalidation", "aws:TokenIssueTime condition applied", "4 min ago", "success"],
  ["EventBridge CAEP", "session-revoked SET broadcast", "7 min ago", "pending"]
] as const;

export function AuditTimeline(): ReactNode {
  return <article className="card timeline"><div className="card-heading"><div><p className="eyebrow">AWS SDK V2 CONTROL PLANE</p><h3>Remediation audit timeline</h3></div><button className="link-button">View all audits →</button></div><div className="timeline-list">{actions.map(([title, detail, time, status]) => <div className="timeline-item" key={title}><span className={`timeline-icon ${status}`}>{status === "success" ? <CheckCircle2 size={16} /> : <Clock3 size={16} />}</span><div><strong>{title}</strong><p>{detail}</p></div><time>{time}</time></div>)}</div><div className="audit-footer"><LockKeyhole size={15} /> All actions require policy guardrails and explicit operator approval.</div></article>;
}
