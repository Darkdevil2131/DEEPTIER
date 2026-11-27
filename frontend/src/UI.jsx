import React from 'react';
import { AlertTriangle, Ban, CircleHelp, CircleOff, CheckCircle2, Info } from 'lucide-react';

const statusStyle = {
  passed: ['passed', CheckCircle2, 'Passed'], approved: ['passed', CheckCircle2, 'Approved'], complete: ['passed', CheckCircle2, 'Complete'], completed: ['passed', CheckCircle2, 'Complete'],
  review: ['review', AlertTriangle, 'Review required'], review_required: ['review', AlertTriangle, 'Review required'], pending_approval: ['review', AlertTriangle, 'Pending review'], required: ['review', AlertTriangle, 'Required'],
  blocked: ['blocked', Ban, 'Blocked'], rejected: ['blocked', Ban, 'Rejected'], failed: ['blocked', Ban, 'Failed'],
  informational: ['information', Info, 'Informational'], estimated: ['information', Info, 'Estimated'], running: ['information', Info, 'In progress'],
  unavailable: ['unavailable', CircleOff, 'Unavailable'], unknown: ['unavailable', CircleHelp, 'Not confirmed'],
};

export function Panel({ title, children, className = '', ...props }) {
  return <section className={`panel ${className}`.trim()} {...props}>{title && <h2>{title}</h2>}{children}</section>;
}

export function Line({ label, value, note }) {
  return <div className="line"><span>{label}</span><strong>{value ?? 'Unavailable'}</strong>{note && <small>{note}</small>}</div>;
}

export function Json({ data, label = 'Inspect technical response' }) {
  if (data == null) return null;
  return <details className="technical-disclosure"><summary>{label}</summary><pre>{JSON.stringify(data, null, 2)}</pre></details>;
}

export function Metric({ label, value, note, provenance }) {
  return <div className="metric"><span className="metric-label">{label}</span><strong>{value ?? 'Unavailable'}</strong>{note && <span className="metric-note">{note}</span>}{provenance && <ProvenanceBadge provenance={provenance} />}</div>;
}

export function DataStatusBadge({ status = 'unavailable', label }) {
  const key = String(status).toLowerCase().replaceAll(' ', '_');
  const [tone, Icon, fallback] = statusStyle[key] || statusStyle.unavailable;
  return <span className={`status-badge status-${tone}`}><Icon aria-hidden="true" size={14} strokeWidth={2} /><span>{label || fallback}</span></span>;
}

export function ProvenanceBadge({ provenance = 'estimated' }) {
  const label = ({ live:'Live Java response', historical:'Historical data', 'user-provided':'User-provided', estimated:'Planning estimate', synthetic:'Synthetic demonstration', recorded:'Recorded response' })[provenance] || 'Unverified source';
  return <span className="provenance-badge"><Info aria-hidden="true" size={13} /><span>{label}</span></span>;
}

export function DecisionGate({ title, status, children }) {
  return <section className="decision-gate"><div className="decision-gate-title"><h3>{title}</h3><DataStatusBadge status={status} /></div>{children}</section>;
}

export function EvidencePanel({ title, provenance = 'estimated', children }) {
  return <section className="evidence-panel"><header><h3>{title}</h3><ProvenanceBadge provenance={provenance} /></header>{children}</section>;
}

export function download(name, data) {
  const url = URL.createObjectURL(new Blob([data]));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
