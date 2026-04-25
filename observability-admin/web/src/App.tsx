import { useCallback, useEffect, useState } from 'react';
import { Avatar } from './components/Avatar';
import { Icon } from './components/Icon';
import { Toast } from './components/Toast';
import { AdminPanel, type AdminTab } from './pages/AdminPanel';
import { IncidentDetail } from './pages/IncidentDetail';
import { IncidentsList } from './pages/IncidentsList';
import type { Datasource, Incident, Member, Team } from './types';
import { api } from './api';

type Route =
  | { page: 'incidents' }
  | { page: 'incident'; id: string }
  | { page: 'admin-members' }
  | { page: 'admin-teams' }
  | { page: 'admin-datasources' };

const POLL_MS = 5000;

export function App() {
  const [route, setRoute] = useState<Route>({ page: 'incidents' });
  const [members, setMembers] = useState<Member[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [currentIncident, setCurrentIncident] = useState<Incident | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const showToast = (msg: string) => setToast(msg);

  const refreshAll = useCallback(async () => {
    const [m, t, d, i] = await Promise.all([
      api.members.list(),
      api.teams.list(),
      api.datasources.list(),
      api.incidents.list(),
    ]);
    setMembers(m);
    setTeams(t);
    setDatasources(d);
    setIncidents(i);
  }, []);

  useEffect(() => {
    refreshAll().catch((e) => console.error('initial load failed', e));
  }, [refreshAll]);

  useEffect(() => {
    if (route.page !== 'incidents') return;
    const t = setInterval(() => {
      api.incidents.list().then(setIncidents).catch(() => {});
    }, POLL_MS);
    return () => clearInterval(t);
  }, [route.page]);

  useEffect(() => {
    if (route.page !== 'incident') {
      setCurrentIncident(null);
      return;
    }
    api.incidents.get(route.id).then(setCurrentIncident).catch(() => setCurrentIncident(null));
  }, [route]);

  const openIncidents = incidents.filter((i) => i.status === 'open');

  // Member CRUD
  const addMember = async (m: Omit<Member, 'id'>) => {
    const created = await api.members.create(m);
    setMembers((prev) => [created, ...prev]);
    showToast(`Added ${created.name}`);
  };
  const updateMember = async (m: Member) => {
    const updated = await api.members.update(m);
    setMembers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
  };
  const deleteMember = async (id: string) => {
    const m = members.find((x) => x.id === id);
    await api.members.remove(id);
    setMembers((prev) => prev.filter((x) => x.id !== id));
    showToast(`Removed ${m?.name ?? id}`);
  };

  // Team CRUD
  const addTeam = async (t: Omit<Team, 'id'>) => {
    const created = await api.teams.create(t);
    setTeams((prev) => [created, ...prev]);
    showToast(`Created team ${created.name}`);
  };
  const deleteTeam = async (id: string) => {
    await api.teams.remove(id);
    setTeams((prev) => prev.filter((x) => x.id !== id));
  };

  // Datasource CRUD
  const addDatasource = async (d: Omit<Datasource, 'id'>) => {
    const created = await api.datasources.create(d);
    setDatasources((prev) => [created, ...prev]);
    showToast(`Added datasource ${created.namespace}.${created.name}`);
  };
  const updateDatasource = async (d: Datasource) => {
    const updated = await api.datasources.update(d);
    setDatasources((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
  };
  const deleteDatasource = async (id: string) => {
    await api.datasources.remove(id);
    setDatasources((prev) => prev.filter((x) => x.id !== id));
  };

  // Incident actions
  const resolveCurrent = async () => {
    if (!currentIncident) return;
    const updated = await api.incidents.resolve(currentIncident.id);
    setCurrentIncident(updated);
    setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
    showToast('Incident resolved');
  };
  const reloadCurrent = async () => {
    if (!currentIncident) return;
    const fresh = await api.incidents.get(currentIncident.id);
    setCurrentIncident(fresh);
    setIncidents((prev) => prev.map((i) => (i.id === fresh.id ? fresh : i)));
    showToast('Integration attached');
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <div className="sidebar__brand-mark">O</div>
          <span>observability</span>
        </div>

        <div className="sidebar__group-label">Operations</div>
        <button
          className={`nav-item ${
            route.page === 'incidents' || route.page === 'incident' ? 'nav-item--active' : ''
          }`}
          onClick={() => setRoute({ page: 'incidents' })}
        >
          <span className="nav-item__icon">
            <Icon name="bolt" size={13} />
          </span>
          Incidents
          <span className="nav-item__count">{openIncidents.length}</span>
        </button>

        <div className="sidebar__group-label">Admin</div>
        <button
          className={`nav-item ${route.page === 'admin-members' ? 'nav-item--active' : ''}`}
          onClick={() => setRoute({ page: 'admin-members' })}
        >
          <span className="nav-item__icon">
            <Icon name="users" size={13} />
          </span>
          Members
          <span className="nav-item__count">{members.length}</span>
        </button>
        <button
          className={`nav-item ${route.page === 'admin-teams' ? 'nav-item--active' : ''}`}
          onClick={() => setRoute({ page: 'admin-teams' })}
        >
          <span className="nav-item__icon">
            <Icon name="team" size={13} />
          </span>
          Teams
          <span className="nav-item__count">{teams.length}</span>
        </button>
        <button
          className={`nav-item ${route.page === 'admin-datasources' ? 'nav-item--active' : ''}`}
          onClick={() => setRoute({ page: 'admin-datasources' })}
        >
          <span className="nav-item__icon">
            <Icon name="db" size={13} />
          </span>
          Datasources
          <span className="nav-item__count">{datasources.length}</span>
        </button>

        <div className="sidebar__user">
          <Avatar name="Maya Chen" id="u_02" />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 12.5, fontWeight: 500 }}>Maya Chen</div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>Admin · Acme</div>
          </div>
        </div>
      </aside>

      <main className="main">
        <div className="topbar">
          <div className="topbar__crumbs">
            {route.page === 'incidents' && <strong>Incidents</strong>}
            {route.page === 'incident' && (
              <>
                <button onClick={() => setRoute({ page: 'incidents' })} style={{ cursor: 'pointer' }}>
                  Incidents
                </button>
                <span className="topbar__sep">/</span>
                <strong style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                  {currentIncident?.id ?? route.id}
                </strong>
              </>
            )}
            {route.page.startsWith('admin') && (
              <>
                <span>Admin</span>
                <span className="topbar__sep">/</span>
                <strong>
                  {route.page === 'admin-members' && 'Members'}
                  {route.page === 'admin-teams' && 'Teams'}
                  {route.page === 'admin-datasources' && 'Datasources'}
                </strong>
              </>
            )}
          </div>
          <div className="topbar__actions">
            <button className="btn btn--ghost btn--icon" title="Notifications">
              <Icon name="bell" size={14} />
            </button>
          </div>
        </div>

        <div className="content">
          {route.page === 'incidents' && (
            <IncidentsList
              incidents={incidents}
              datasources={datasources}
              teams={teams}
              members={members}
              onOpen={(id) => setRoute({ page: 'incident', id })}
            />
          )}
          {route.page === 'incident' && currentIncident && (
            <IncidentDetail
              incident={currentIncident}
              datasources={datasources}
              teams={teams}
              members={members}
              onBack={() => setRoute({ page: 'incidents' })}
              onResolve={resolveCurrent}
              onAttached={reloadCurrent}
            />
          )}
          {route.page === 'incident' && !currentIncident && <div className="empty">Loading incident…</div>}
          {route.page.startsWith('admin') && (
            <AdminPanel
              key={route.page}
              members={members}
              teams={teams}
              datasources={datasources}
              initialTab={
                route.page === 'admin-teams'
                  ? 'teams'
                  : route.page === 'admin-datasources'
                  ? 'datasources'
                  : ('members' as AdminTab)
              }
              onAddMember={addMember}
              onUpdateMember={updateMember}
              onDeleteMember={deleteMember}
              onAddTeam={addTeam}
              onDeleteTeam={deleteTeam}
              onAddDatasource={addDatasource}
              onUpdateDatasource={updateDatasource}
              onDeleteDatasource={deleteDatasource}
            />
          )}
        </div>
      </main>

      {toast && <Toast msg={toast} onDone={() => setToast(null)} />}
    </div>
  );
}
