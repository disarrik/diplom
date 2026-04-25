import { useState } from 'react';
import { Avatar, AvatarStack } from '../components/Avatar';
import { DSBadge } from '../components/StatusPill';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { MultiSelect } from '../components/MultiSelect';
import type { Datasource, Member, Team } from '../types';

export type AdminTab = 'members' | 'teams' | 'datasources';

export function AdminPanel({
  members,
  teams,
  datasources,
  initialTab,
  onAddMember,
  onUpdateMember,
  onDeleteMember,
  onAddTeam,
  onDeleteTeam,
  onAddDatasource,
  onUpdateDatasource,
  onDeleteDatasource,
}: {
  members: Member[];
  teams: Team[];
  datasources: Datasource[];
  initialTab: AdminTab;
  onAddMember: (m: Omit<Member, 'id'>) => void;
  onUpdateMember: (m: Member) => void;
  onDeleteMember: (id: string) => void;
  onAddTeam: (t: Omit<Team, 'id'>) => void;
  onDeleteTeam: (id: string) => void;
  onAddDatasource: (d: Omit<Datasource, 'id'>) => void;
  onUpdateDatasource: (d: Datasource) => void;
  onDeleteDatasource: (id: string) => void;
}) {
  const [tab, setTab] = useState<AdminTab>(initialTab);
  return (
    <>
      <div className="page-title">
        <h1>Admin</h1>
      </div>
      <p className="page-subtitle">
        Manage members, teams, and the datasources they own. Routing for new incidents follows these relations.
      </p>

      <div className="tabs">
        <div className={`tab ${tab === 'members' ? 'tab--active' : ''}`} onClick={() => setTab('members')}>
          <Icon name="users" size={13} /> Members <span className="tab__count">{members.length}</span>
        </div>
        <div className={`tab ${tab === 'teams' ? 'tab--active' : ''}`} onClick={() => setTab('teams')}>
          <Icon name="team" size={13} /> Teams <span className="tab__count">{teams.length}</span>
        </div>
        <div
          className={`tab ${tab === 'datasources' ? 'tab--active' : ''}`}
          onClick={() => setTab('datasources')}
        >
          <Icon name="db" size={13} /> Datasources <span className="tab__count">{datasources.length}</span>
        </div>
      </div>

      {tab === 'members' && (
        <MembersTab
          members={members}
          teams={teams}
          onAdd={onAddMember}
          onUpdate={onUpdateMember}
          onDelete={onDeleteMember}
        />
      )}
      {tab === 'teams' && (
        <TeamsTab
          teams={teams}
          members={members}
          datasources={datasources}
          onAdd={onAddTeam}
          onDelete={onDeleteTeam}
        />
      )}
      {tab === 'datasources' && (
        <DatasourcesTab
          datasources={datasources}
          teams={teams}
          onAdd={onAddDatasource}
          onUpdate={onUpdateDatasource}
          onDelete={onDeleteDatasource}
        />
      )}
    </>
  );
}

function MembersTab({
  members,
  teams,
  onAdd,
  onUpdate,
  onDelete,
}: {
  members: Member[];
  teams: Team[];
  onAdd: (m: Omit<Member, 'id'>) => void;
  onUpdate: (m: Member) => void;
  onDelete: (id: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div className="search">
          <Icon name="search" size={12} />
          <input placeholder="Search members…" />
        </div>
        <button className="btn btn--primary" onClick={() => setAdding(true)}>
          <Icon name="plus" size={12} /> New member
        </button>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th style={{ width: 240 }}>Name</th>
              <th style={{ width: 220 }}>Email</th>
              <th style={{ width: 160 }}>Role</th>
              <th>Teams</th>
              <th style={{ width: 50 }}></th>
            </tr>
          </thead>
          <tbody>
            {members.map((m) => (
              <tr key={m.id}>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <Avatar name={m.name} id={m.id} />
                    <span className="col-name">{m.name}</span>
                  </div>
                </td>
                <td className="col-mono">{m.email}</td>
                <td style={{ color: 'var(--ink-2)' }}>{m.role}</td>
                <td>
                  <MultiSelect
                    options={teams}
                    selected={m.teamIds}
                    onChange={(ids) => onUpdate({ ...m, teamIds: ids })}
                    placeholder="Add to team…"
                    getLabel={(t) => t.name}
                  />
                </td>
                <td>
                  <button className="icon-btn" onClick={() => onDelete(m.id)}>
                    <Icon name="trash" size={13} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {adding && (
        <NewMemberModal
          teams={teams}
          onClose={() => setAdding(false)}
          onSubmit={(m) => {
            onAdd(m);
            setAdding(false);
          }}
        />
      )}
    </>
  );
}

function NewMemberModal({
  teams,
  onClose,
  onSubmit,
}: {
  teams: Team[];
  onClose: () => void;
  onSubmit: (m: Omit<Member, 'id'>) => void;
}) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('Data Engineer');
  const [teamIds, setTeamIds] = useState<string[]>([]);
  const submit = () => {
    if (!name.trim() || !email.trim()) return;
    onSubmit({ name, email, role, teamIds });
  };
  return (
    <Modal
      title="New member"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn--primary" onClick={submit}>
            Create
          </button>
        </>
      }
    >
      <div className="field">
        <div className="field__label">Full name</div>
        <input
          className="field__input"
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Ada Lovelace"
        />
      </div>
      <div className="field">
        <div className="field__label">Email</div>
        <input
          className="field__input"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="ada@acme.io"
        />
      </div>
      <div className="field">
        <div className="field__label">Role</div>
        <select className="field__select" value={role} onChange={(e) => setRole(e.target.value)}>
          <option>Data Engineer</option>
          <option>Analytics Eng.</option>
          <option>SRE</option>
          <option>On-call Eng.</option>
          <option>Eng Manager</option>
          <option>Director</option>
        </select>
      </div>
      <div className="field">
        <div className="field__label">Teams</div>
        <MultiSelect
          options={teams}
          selected={teamIds}
          onChange={setTeamIds}
          placeholder="Add to teams…"
          getLabel={(t) => t.name}
        />
      </div>
    </Modal>
  );
}

function TeamsTab({
  teams,
  members,
  datasources,
  onAdd,
  onDelete,
}: {
  teams: Team[];
  members: Member[];
  datasources: Datasource[];
  onAdd: (t: Omit<Team, 'id'>) => void;
  onDelete: (id: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div className="search">
          <Icon name="search" size={12} />
          <input placeholder="Search teams…" />
        </div>
        <button className="btn btn--primary" onClick={() => setAdding(true)}>
          <Icon name="plus" size={12} /> New team
        </button>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th style={{ width: 220 }}>Team</th>
              <th style={{ width: 180 }}>Handle</th>
              <th style={{ width: 100 }}>Members</th>
              <th style={{ width: 120 }}>Datasources</th>
              <th>Slack default</th>
              <th style={{ width: 50 }}></th>
            </tr>
          </thead>
          <tbody>
            {teams.map((t) => {
              const teamMembers = members.filter((m) => m.teamIds.includes(t.id));
              const teamDs = datasources.filter((d) => d.teamIds.includes(t.id));
              return (
                <tr key={t.id}>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <Avatar name={t.name} id={t.id} />
                      <span className="col-name">{t.name}</span>
                    </div>
                  </td>
                  <td className="col-mono">{t.handle}</td>
                  <td>
                    {teamMembers.length === 0 ? (
                      <span style={{ color: 'var(--ink-4)', fontSize: 12 }}>None</span>
                    ) : (
                      <AvatarStack items={teamMembers} max={4} />
                    )}
                  </td>
                  <td style={{ fontVariantNumeric: 'tabular-nums', color: 'var(--ink-2)' }}>{teamDs.length}</td>
                  <td className="col-mono">{t.slack}</td>
                  <td>
                    <button className="icon-btn" onClick={() => onDelete(t.id)}>
                      <Icon name="trash" size={13} />
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {adding && (
        <NewTeamModal
          onClose={() => setAdding(false)}
          onSubmit={(t) => {
            onAdd(t);
            setAdding(false);
          }}
        />
      )}
    </>
  );
}

function NewTeamModal({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (t: Omit<Team, 'id'>) => void;
}) {
  const [name, setName] = useState('');
  const [handle, setHandle] = useState('');
  const [slack, setSlack] = useState('');
  const submit = () => {
    if (!name.trim()) return;
    onSubmit({
      name,
      handle: handle || '@' + name.toLowerCase().replace(/\s+/g, '-'),
      slack: slack || '#data-' + name.toLowerCase().replace(/\s+/g, '-'),
    });
  };
  return (
    <Modal
      title="New team"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn--primary" onClick={submit}>
            Create
          </button>
        </>
      }
    >
      <div className="field">
        <div className="field__label">Team name</div>
        <input
          className="field__input"
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Marketing Analytics"
        />
      </div>
      <div className="field">
        <div className="field__label">Handle</div>
        <input
          className="field__input"
          value={handle}
          onChange={(e) => setHandle(e.target.value)}
          placeholder="@marketing-analytics"
        />
      </div>
      <div className="field">
        <div className="field__label">Default Slack channel</div>
        <input
          className="field__input"
          value={slack}
          onChange={(e) => setSlack(e.target.value)}
          placeholder="#data-marketing"
        />
      </div>
    </Modal>
  );
}

function DatasourcesTab({
  datasources,
  teams,
  onAdd,
  onUpdate,
  onDelete,
}: {
  datasources: Datasource[];
  teams: Team[];
  onAdd: (d: Omit<Datasource, 'id'>) => void;
  onUpdate: (d: Datasource) => void;
  onDelete: (id: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div className="search">
          <Icon name="search" size={12} />
          <input placeholder="Search datasources…" />
        </div>
        <button className="btn btn--primary" onClick={() => setAdding(true)}>
          <Icon name="plus" size={12} /> New datasource
        </button>
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th style={{ width: 120 }}>Type</th>
              <th style={{ width: 220 }}>Host</th>
              <th style={{ width: 280 }}>Owning teams</th>
              <th style={{ width: 50 }}></th>
            </tr>
          </thead>
          <tbody>
            {datasources.map((d) => (
              <tr key={d.id}>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ color: 'var(--ink-3)' }}>
                      <Icon name="db" size={13} />
                    </span>
                    <span className="col-mono col-name" style={{ color: 'var(--ink)' }}>
                      {d.namespace}.{d.name}
                    </span>
                  </div>
                </td>
                <td>
                  <DSBadge type={d.type} />
                </td>
                <td className="col-mono">{d.host}</td>
                <td>
                  <MultiSelect
                    options={teams}
                    selected={d.teamIds}
                    onChange={(ids) => onUpdate({ ...d, teamIds: ids })}
                    placeholder="Assign team…"
                    getLabel={(t) => t.name}
                  />
                </td>
                <td>
                  <button className="icon-btn" onClick={() => onDelete(d.id)}>
                    <Icon name="trash" size={13} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {adding && (
        <NewDatasourceModal
          teams={teams}
          onClose={() => setAdding(false)}
          onSubmit={(d) => {
            onAdd(d);
            setAdding(false);
          }}
        />
      )}
    </>
  );
}

function NewDatasourceModal({
  teams,
  onClose,
  onSubmit,
}: {
  teams: Team[];
  onClose: () => void;
  onSubmit: (d: Omit<Datasource, 'id'>) => void;
}) {
  const [fq, setFq] = useState('');
  const [type, setType] = useState('postgres');
  const [host, setHost] = useState('');
  const [teamIds, setTeamIds] = useState<string[]>([]);
  const submit = () => {
    if (!fq.trim()) return;
    const dot = fq.lastIndexOf('.');
    const namespace = dot >= 0 ? fq.slice(0, dot) : fq;
    const name = dot >= 0 ? fq.slice(dot + 1) : '';
    onSubmit({ namespace, name, type, host, teamIds });
  };
  return (
    <Modal
      title="New datasource"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn--primary" onClick={submit}>
            Create
          </button>
        </>
      }
    >
      <div className="field">
        <div className="field__label">Fully-qualified name</div>
        <input
          className="field__input"
          autoFocus
          value={fq}
          onChange={(e) => setFq(e.target.value)}
          placeholder="warehouse.fct.daily_orders"
          style={{ fontFamily: 'var(--font-mono)' }}
        />
      </div>
      <div className="field">
        <div className="field__label">Type</div>
        <select className="field__select" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="postgres">Postgres</option>
          <option value="mysql">MySQL</option>
          <option value="snowflake">Snowflake</option>
          <option value="bigquery">BigQuery</option>
          <option value="redshift">Redshift</option>
        </select>
      </div>
      <div className="field">
        <div className="field__label">Host</div>
        <input
          className="field__input"
          value={host}
          onChange={(e) => setHost(e.target.value)}
          placeholder="acme.snowflakecomputing.com"
        />
      </div>
      <div className="field">
        <div className="field__label">Owning teams</div>
        <MultiSelect
          options={teams}
          selected={teamIds}
          onChange={setTeamIds}
          placeholder="Assign owning teams…"
          getLabel={(t) => t.name}
        />
      </div>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
        Datasources have no relations to each other — downstream impact is provided by external events.
      </div>
    </Modal>
  );
}
