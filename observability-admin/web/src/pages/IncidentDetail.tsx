import { useEffect, useState } from 'react';
import { Avatar, AvatarStack } from '../components/Avatar';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { DSBadge, StatusPill } from '../components/StatusPill';
import { fmtDuration, fmtTime } from '../lib/time';
import type {
  Datasource,
  Incident,
  Integration,
  IntegrationProviderInfo,
  Member,
  Team,
} from '../types';
import { api } from '../api';

export function IncidentDetail({
  incident,
  datasources,
  teams,
  members,
  onBack,
  onResolve,
  onAttached,
}: {
  incident: Incident;
  datasources: Datasource[];
  teams: Team[];
  members: Member[];
  onBack: () => void;
  onResolve: () => void;
  onAttached: () => void;
}) {
  const [adding, setAdding] = useState(false);

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
              <IntegrationCard key={i} integration={it} />
            ))}
            {incident.integrations.length === 0 && (
              <div style={{ fontSize: 12, color: 'var(--ink-4)', marginBottom: 8 }}>
                No integrations attached.
              </div>
            )}
            <button className="integration-add" onClick={() => setAdding(true)}>
              <Icon name="plus" size={12} /> Attach integration
            </button>
            <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 8, lineHeight: 1.5 }}>
              The conversation lives in your tools. We just keep the link.
            </div>
          </div>
        </aside>
      </div>

      {adding && (
        <AttachIntegrationModal
          incidentId={incident.id}
          onClose={() => setAdding(false)}
          onAttached={() => {
            setAdding(false);
            onAttached();
          }}
        />
      )}
    </>
  );
}

function IntegrationCard({ integration }: { integration: Integration }) {
  const meta: Record<string, { bg: string; label: string; icon: string }> = {
    slack: { bg: '#4A154B', label: 'Slack', icon: '#' },
    jira: { bg: '#0052CC', label: 'Jira', icon: 'J' },
  };
  const m = meta[integration.type] ?? { bg: '#404040', label: integration.type, icon: '?' };
  return (
    <a href={integration.url} target="_blank" rel="noreferrer" className="integration-card">
      <div
        className="integration-card__icon"
        style={{ background: m.bg, color: 'white', fontFamily: 'var(--font-mono)', fontWeight: 700 }}
      >
        {m.icon}
      </div>
      <div className="integration-card__body">
        <div className="integration-card__title">{m.label}</div>
        <div className="integration-card__sub">{integration.label}</div>
      </div>
      <span className="integration-card__ext">
        <Icon name="ext" size={12} />
      </span>
    </a>
  );
}

function AttachIntegrationModal({
  incidentId,
  onClose,
  onAttached,
}: {
  incidentId: string;
  onClose: () => void;
  onAttached: () => void;
}) {
  const [providers, setProviders] = useState<IntegrationProviderInfo[] | null>(null);
  const [providerId, setProviderId] = useState<string>('');
  const [paramsText, setParamsText] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.integrations
      .providers()
      .then((p) => {
        setProviders(p);
        if (p.length > 0) setProviderId(p[0].id);
      })
      .catch(() => setProviders([]));
  }, []);

  const submit = async () => {
    setError(null);
    if (!providerId) {
      setError('Pick a provider.');
      return;
    }
    let params: Record<string, string> = {};
    if (paramsText.trim()) {
      try {
        params = JSON.parse(paramsText);
      } catch {
        setError('Params must be JSON.');
        return;
      }
    }
    try {
      await api.incidents.attachIntegration(incidentId, providerId, params);
      onAttached();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <Modal
      title="Attach integration"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn--primary"
            onClick={submit}
            disabled={!providers || providers.length === 0}
          >
            Create &amp; attach
          </button>
        </>
      }
    >
      {providers === null && <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>Loading providers…</div>}
      {providers && providers.length === 0 && (
        <div style={{ fontSize: 12.5, color: 'var(--ink-3)', lineHeight: 1.5 }}>
          No integration providers configured. Implementations of <code>IntegrationProvider</code> registered
          on the server appear here.
        </div>
      )}
      {providers && providers.length > 0 && (
        <>
          <div className="field">
            <div className="field__label">Provider</div>
            <select
              className="field__select"
              value={providerId}
              onChange={(e) => setProviderId(e.target.value)}
            >
              {providers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <div className="field__label">Params (JSON)</div>
            <input
              className="field__input"
              placeholder='{"project":"DATA"}'
              value={paramsText}
              onChange={(e) => setParamsText(e.target.value)}
            />
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            The provider creates the resource via its API and returns a reference URL — the conversation is
            not stored here.
          </div>
        </>
      )}
      {error && <div style={{ fontSize: 12, color: 'var(--danger)' }}>{error}</div>}
    </Modal>
  );
}
