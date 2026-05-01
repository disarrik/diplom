import { Avatar, AvatarStack } from '../components/Avatar';
import { Icon } from '../components/Icon';
import { DSBadge, StatusPill } from '../components/StatusPill';
import { fmtDuration, fmtTime } from '../lib/time';
import type {
  Datasource,
  Incident,
  Integration,
  Member,
  PluginDescriptor,
  Team,
} from '../types';

export function IncidentDetail({
  incident,
  datasources,
  teams,
  members,
  plugins,
  onBack,
  onResolve,
}: {
  incident: Incident;
  datasources: Datasource[];
  teams: Team[];
  members: Member[];
  plugins: PluginDescriptor[];
  onBack: () => void;
  onResolve: () => void;
}) {
  const pluginById = new Map(plugins.map((p) => [p.id, p]));

  const team = teams.find((t) => t.id === incident.teamId);
  const assignee = members.find((m) => m.id === incident.assigneeId);
  const rootDs = datasources.find((d) => d.id === incident.rootDsId);
  const teamMembers = team ? members.filter((m) => m.teamIds.includes(team.id)) : [];
  const affected = incident.affectedDsIds
    .map((id) => datasources.find((d) => d.id === id))
    .filter(Boolean) as Datasource[];

  return (
    <>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 20 }}>
        <button className="btn btn--ghost btn--sm" onClick={onBack}>
          <Icon name="arrowLeft" size={12} /> Incidents
        </button>
        <span style={{ color: 'var(--ink-4)' }}>/</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--ink-3)' }}>
          {incident.id}
        </span>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          {incident.status === 'open' && (
            <button className="btn btn--primary btn--sm" onClick={onResolve}>
              <Icon name="check" size={12} /> Mark resolved
            </button>
          )}
        </div>
      </div>

      <div className="detail">
        <div>
          <h2 className="detail__title">{incident.title}</h2>
          <div className="detail__id">
            <span>Opened {fmtTime(incident.openedAt)}</span>
            {incident.resolvedAt && (
              <>
                <span>·</span>
                <span>Resolved in {fmtDuration(incident.openedAt, incident.resolvedAt)}</span>
              </>
            )}
          </div>
          <div className="detail__meta">
            <StatusPill status={incident.status} />
            {rootDs && (
              <span className="status-pill">
                <Icon name="db" size={11} /> {rootDs.namespace}.{rootDs.name}
              </span>
            )}
            {team && (
              <span className="status-pill">
                <Icon name="team" size={11} /> {team.name}
              </span>
            )}
          </div>

          <div className="timeline">
            <h3>Event timeline</h3>
            {incident.events.map((e, i) => (
              <div key={i} className="tl-event">
                <div
                  className={`tl-event__dot ${e.type === 'opened' ? 'tl-event__dot--accent' : ''} ${
                    e.type === 'resolved' ? 'tl-event__dot--resolve' : ''
                  }`}
                >
                  <Icon
                    name={
                      e.type === 'opened'
                        ? 'bolt'
                        : e.type === 'resolved'
                        ? 'check'
                        : e.type === 'integration'
                        ? 'link'
                        : e.type === 'assigned'
                        ? 'team'
                        : e.type === 'affected'
                        ? 'activity'
                        : e.type === 'ack'
                        ? 'flag'
                        : 'clock'
                    }
                    size={11}
                  />
                </div>
                <div className="tl-event__body">
                  <div className="tl-event__head">
                    <span className="tl-event__actor">{e.actor}</span>
                    <span className="tl-event__action">{e.text}</span>
                    <span className="tl-event__time">{fmtTime(e.at)}</span>
                  </div>
                  {e.detail && (
                    <div
                      className={`tl-event__detail ${
                        e.type === 'opened' ? 'tl-event__detail--accent' : ''
                      }`}
                    >
                      {e.detail}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        <aside>
          <div className="side-section">
            <div className="side-label">Assigned team</div>
            {team ? (
              <>
                <div className="assignee-card" style={{ marginBottom: 8 }}>
                  <Avatar name={team.name} id={team.id} size="md" />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="assignee-card__name">{team.name}</div>
                    <div className="assignee-card__role" style={{ fontFamily: 'var(--font-mono)' }}>
                      {team.handle}
                    </div>
                  </div>
                </div>
                <div className="side-row" style={{ paddingTop: 8 }}>
                  <span className="side-row__label">On-call lead</span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {assignee && <Avatar name={assignee.name} id={assignee.id} />}
                    <span style={{ fontSize: 12.5 }}>{assignee?.name ?? '—'}</span>
                  </span>
                </div>
                <div className="side-row">
                  <span className="side-row__label">Team members</span>
                  <AvatarStack items={teamMembers} max={4} />
                </div>
              </>
            ) : (
              <div style={{ fontSize: 12, color: 'var(--ink-4)' }}>
                No team owns this datasource. Assign one in Admin → Datasources.
              </div>
            )}
          </div>

          <div className="side-section">
            <div className="side-label">
              Affected sources <span style={{ color: 'var(--ink-4)' }}>({affected.length})</span>
            </div>
            <div className="affected-list">
              {affected.map((d) => (
                <div key={d.id} className="affected-row">
                  <span className="affected-row__icon">
                    <Icon name="db" size={11} />
                  </span>
                  <span className="affected-row__name">
                    {d.namespace}.{d.name}
                  </span>
                  <DSBadge type={d.type} />
                </div>
              ))}
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 8, lineHeight: 1.5 }}>
              From event graph — this system does not store relations between sources.
            </div>
          </div>

          <div className="side-section">
            <div className="side-label">Integrations</div>
            {incident.integrations.map((it, i) => (
              <IntegrationCard key={i} integration={it} plugin={pluginById.get(it.pluginId ?? it.type)} />
            ))}
            {incident.integrations.length === 0 && (
              <div style={{ fontSize: 12, color: 'var(--ink-4)', marginBottom: 8 }}>
                No integrations attached yet.
              </div>
            )}
            <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 8, lineHeight: 1.5 }}>
              The conversation lives in your tools. We just keep the link.
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}

function IntegrationCard({ integration, plugin }: { integration: Integration; plugin?: PluginDescriptor }) {
  const m = plugin?.displayMeta ?? { color: '#404040', iconText: '?', cardTitle: integration.type };
  return (
    <a href={integration.url} target="_blank" rel="noreferrer" className="integration-card">
      <div
        className="integration-card__icon"
        style={{ background: m.color, color: 'white', fontFamily: 'var(--font-mono)', fontWeight: 700 }}
      >
        {m.iconText}
      </div>
      <div className="integration-card__body">
        <div className="integration-card__title">{m.cardTitle}</div>
        <div className="integration-card__sub">{integration.label}</div>
      </div>
      <span className="integration-card__ext">
        <Icon name="ext" size={12} />
      </span>
    </a>
  );
}

