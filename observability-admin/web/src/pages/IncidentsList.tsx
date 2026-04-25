import { useMemo, useState } from 'react';
import { Avatar } from '../components/Avatar';
import { DSBadge } from '../components/StatusPill';
import { Icon } from '../components/Icon';
import { fmtDuration, fmtRelative } from '../lib/time';
import type { Datasource, Incident, Member, Team } from '../types';

type Filter = 'all' | 'open' | 'resolved';

export function IncidentsList({
  incidents,
  datasources,
  teams,
  members,
  onOpen,
}: {
  incidents: Incident[];
  datasources: Datasource[];
  teams: Team[];
  members: Member[];
  onOpen: (id: string) => void;
}) {
  const [filter, setFilter] = useState<Filter>('all');
  const [q, setQ] = useState('');

  const teamById = useMemo(() => Object.fromEntries(teams.map((t) => [t.id, t])), [teams]);
  const memberById = useMemo(() => Object.fromEntries(members.map((m) => [m.id, m])), [members]);
  const dsById = useMemo(() => Object.fromEntries(datasources.map((d) => [d.id, d])), [datasources]);

  const filtered = incidents.filter((i) => {
    if (filter !== 'all' && i.status !== filter) return false;
    if (q && !(i.title.toLowerCase().includes(q.toLowerCase()) || i.id.toLowerCase().includes(q.toLowerCase())))
      return false;
    return true;
  });

  const open = filtered.filter((i) => i.status === 'open');
  const resolved = filtered.filter((i) => i.status === 'resolved');

  return (
    <>
      <div className="page-title">
        <h1>Incidents</h1>
      </div>
      <p className="page-subtitle">
        Live incidents emitted from external observability events. Routing and 3rd-party conversations are managed
        here.
      </p>

      <div className="filters">
        <button
          className={`chip ${filter === 'all' ? 'chip--active' : ''}`}
          onClick={() => setFilter('all')}
        >
          All <span className="chip__count">{incidents.length}</span>
        </button>
        <button
          className={`chip ${filter === 'open' ? 'chip--active' : ''}`}
          onClick={() => setFilter('open')}
        >
          <span className="status-dot status-dot--open" style={{ width: 6, height: 6 }}></span>
          Open <span className="chip__count">{incidents.filter((i) => i.status === 'open').length}</span>
        </button>
        <button
          className={`chip ${filter === 'resolved' ? 'chip--active' : ''}`}
          onClick={() => setFilter('resolved')}
        >
          <span className="status-dot status-dot--resolved" style={{ width: 6, height: 6 }}></span>
          Resolved <span className="chip__count">{incidents.filter((i) => i.status === 'resolved').length}</span>
        </button>
        <div className="search">
          <Icon name="search" size={12} />
          <input placeholder="Search by ID or title…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>

      <div className="incidents">
        {open.length > 0 && (filter === 'all' || filter === 'open') && (
          <div className="section-header">
            <span className="status-dot status-dot--open" style={{ width: 6, height: 6 }}></span>
            Open <span className="section-header__count">{open.length}</span>
          </div>
        )}
        {(filter === 'all' || filter === 'open') &&
          open.map((inc) => (
            <IncidentRow
              key={inc.id}
              inc={inc}
              teamById={teamById}
              memberById={memberById}
              dsById={dsById}
              onOpen={onOpen}
            />
          ))}

        {resolved.length > 0 && (filter === 'all' || filter === 'resolved') && (
          <div className="section-header">
            <span className="status-dot status-dot--resolved" style={{ width: 6, height: 6 }}></span>
            Resolved <span className="section-header__count">{resolved.length}</span>
          </div>
        )}
        {(filter === 'all' || filter === 'resolved') &&
          resolved.map((inc) => (
            <IncidentRow
              key={inc.id}
              inc={inc}
              teamById={teamById}
              memberById={memberById}
              dsById={dsById}
              onOpen={onOpen}
            />
          ))}

        {filtered.length === 0 && <div className="empty">No incidents match your filters.</div>}
      </div>
    </>
  );
}

function IncidentRow({
  inc,
  teamById,
  memberById,
  dsById,
  onOpen,
}: {
  inc: Incident;
  teamById: Record<string, Team>;
  memberById: Record<string, Member>;
  dsById: Record<string, Datasource>;
  onOpen: (id: string) => void;
}) {
  const team = inc.teamId ? teamById[inc.teamId] : undefined;
  const assignee = inc.assigneeId ? memberById[inc.assigneeId] : undefined;
  const rootDs = dsById[inc.rootDsId];
  return (
    <button className="incident-row" onClick={() => onOpen(inc.id)}>
      <span className={`status-dot status-dot--${inc.status}`}></span>
      <span className="incident-row__id">{inc.id.slice(0, 8)}</span>
      <div className="incident-row__title">
        <div className="incident-row__name">{inc.title}</div>
        <div className="incident-row__meta">
          {inc.affectedDsIds.length} affected
          {inc.integrations.length > 0 && (
            <>
              <span>·</span>
              <span>{inc.integrations.map((i) => i.type).join(' + ')}</span>
            </>
          )}
        </div>
      </div>
      <div className="incident-row__source">
        <DSBadge type={rootDs?.type || ''} />
        <span className="incident-row__source-name">{rootDs ? `${rootDs.namespace}.${rootDs.name}` : ''}</span>
      </div>
      <div className="incident-row__team">{team?.name ?? <span style={{ color: 'var(--ink-4)' }}>unassigned</span>}</div>
      <div>{assignee && <Avatar name={assignee.name} id={assignee.id} />}</div>
      <div className="incident-row__time">
        {inc.status === 'open' || !inc.resolvedAt
          ? fmtRelative(inc.openedAt)
          : fmtDuration(inc.openedAt, inc.resolvedAt)}
      </div>
    </button>
  );
}
