import type { IncidentStatus } from '../types';

export function StatusPill({ status }: { status: IncidentStatus }) {
  return (
    <span className={`status-pill ${status === 'open' ? 'status-pill--open' : ''}`}>
      <span className={`status-dot status-dot--${status}`}></span>
      {status === 'open' ? 'Open' : 'Resolved'}
    </span>
  );
}

export function DSBadge({ type }: { type: string }) {
  return <span className="ds-badge">{type}</span>;
}
