import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Avatar } from './components/Avatar';
import { Icon } from './components/Icon';
import { LoginModal } from './components/LoginModal';
import { Toast } from './components/Toast';
import { AdminPanel, type AdminTab } from './pages/AdminPanel';
import { IncidentDetail } from './pages/IncidentDetail';
import { IncidentsList } from './pages/IncidentsList';
import type { Datasource, Incident, Member, PluginDescriptor, Team } from './types';
import { api } from './api';
import { clearCreds, getCreds, UnauthorizedError } from './auth';
import { isAdminRoute, type Route, routeToPath, useRoute } from './lib/router';

const POLL_MS = 5000;

export function App() {
  const { route, navigate } = useRoute();
  const [members, setMembers] = useState<Member[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [datasources, setDatasources] = useState<Datasource[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [plugins, setPlugins] = useState<PluginDescriptor[]>([]);
  const [currentIncident, setCurrentIncident] = useState<Incident | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [authed, setAuthed] = useState<boolean>(!!getCreds());
  const [pendingRoute, setPendingRoute] = useState<Route | null>(null);

  const showToast = (msg: string) => setToast(msg);

  const requireAuth = (target: Route) => {
    if (authed) {
      navigate(target);
    } else {
      setPendingRoute(target);
    }
  };

  const onUnauthorized = () => {
    clearCreds();
    setAuthed(false);
    const target: Route = isAdminRoute(route) ? route : { page: 'admin-members' };
    navigate({ page: 'incidents' });
    setPendingRoute(target);
    showToast('Session expired, please sign in');
  };

  const guarded = <A extends unknown[], R>(fn: (...args: A) => Promise<R>) =>
    async (...args: A): Promise<R | void> => {
      try {
        return await fn(...args);
      } catch (e) {
        if (e instanceof UnauthorizedError) {
          onUnauthorized();
          return;
        }
        throw e;
      }
    };

  const refreshAll = useCallback(async () => {
    const [m, t, d, i, p] = await Promise.all([
      api.members.list(),
      api.teams.list(),
      api.datasources.list(),
      api.incidents.list(),
      api.plugins.list(),
    ]);
    setMembers(m);
    setTeams(t);
    setDatasources(d);
    setIncidents(i);
    setPlugins(p);
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
  const addMember = guarded(async (m: Omit<Member, 'id'>) => {
    const created = await api.members.create(m);
    setMembers((prev) => [created, ...prev]);
    showToast(`Added ${created.name}`);
  });
  const updateMember = guarded(async (m: Member) => {
    const updated = await api.members.update(m);
    setMembers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
  });
  const deleteMember = guarded(async (id: string) => {
    const m = members.find((x) => x.id === id);
    await api.members.remove(id);
    setMembers((prev) => prev.filter((x) => x.id !== id));
    showToast(`Removed ${m?.name ?? id}`);
  });

  // Team CRUD
  const addTeam = guarded(async (t: Omit<Team, 'id'>) => {
    const created = await api.teams.create(t);
    setTeams((prev) => [created, ...prev]);
    showToast(`Created team ${created.name}`);
  });
  const deleteTeam = guarded(async (id: string) => {
    await api.teams.remove(id);
    setTeams((prev) => prev.filter((x) => x.id !== id));
  });

  // Datasource CRUD
  const addDatasource = guarded(async (d: Omit<Datasource, 'id'>) => {
    const created = await api.datasources.create(d);
    setDatasources((prev) => [created, ...prev]);
    showToast(`Added datasource ${created.namespace}.${created.name}`);
  });
  const updateDatasource = guarded(async (d: Datasource) => {
    const updated = await api.datasources.update(d);
    setDatasources((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
  });
  const deleteDatasource = guarded(async (id: string) => {
    await api.datasources.remove(id);
    setDatasources((prev) => prev.filter((x) => x.id !== id));
  });

  // Incident actions
  const resolveCurrent = async () => {
    if (!currentIncident) return;
    const updated = await api.incidents.resolve(currentIncident.id);
    setCurrentIncident(updated);
    setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
    showToast('Incident resolved');
  };

  const signOut = () => {
    clearCreds();
    setAuthed(false);
    if (isAdminRoute(route)) navigate({ page: 'incidents' });
    showToast('Signed out');
  };

  useEffect(() => {
    if (isAdminRoute(route) && !authed) {
      setPendingRoute(route);
    }
    // run once on mount: if a deep-linked admin URL was loaded while signed out,
    // pop the login modal — closing it will navigate back to /incidents
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <div className="sidebar__brand-mark">O</div>
          <span>observability</span>
        </div>

        <div className="sidebar__group-label">Operations</div>
        <NavLink
          target={{ page: 'incidents' }}
          active={route.page === 'incidents' || route.page === 'incident'}
          onActivate={navigate}
        >
          <span className="nav-item__icon">
            <Icon name="bolt" size={13} />
          </span>
          Incidents
          <span className="nav-item__count">{openIncidents.length}</span>
        </NavLink>

        <div className="sidebar__group-label">Admin</div>
        <NavLink
          target={{ page: 'admin-members' }}
          active={route.page === 'admin-members'}
          onActivate={requireAuth}
        >
          <span className="nav-item__icon">
            <Icon name="users" size={13} />
          </span>
          Members
          <span className="nav-item__count">{members.length}</span>
        </NavLink>
        <NavLink
          target={{ page: 'admin-teams' }}
          active={route.page === 'admin-teams'}
          onActivate={requireAuth}
        >
          <span className="nav-item__icon">
            <Icon name="team" size={13} />
          </span>
          Teams
          <span className="nav-item__count">{teams.length}</span>
        </NavLink>
        <NavLink
          target={{ page: 'admin-datasources' }}
          active={route.page === 'admin-datasources'}
          onActivate={requireAuth}
        >
          <span className="nav-item__icon">
            <Icon name="db" size={13} />
          </span>
          Datasources
          <span className="nav-item__count">{datasources.length}</span>
        </NavLink>

        <div className="sidebar__user">
          {authed ? (
            <>
              <Avatar name="admin" id="admin" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12.5, fontWeight: 500 }}>admin</div>
                <button
                  onClick={signOut}
                  style={{
                    fontSize: 11,
                    color: 'var(--ink-3)',
                    background: 'none',
                    border: 'none',
                    padding: 0,
                    cursor: 'pointer',
                  }}
                >
                  Sign out
                </button>
              </div>
            </>
          ) : (
            <button
              className="btn btn--sm"
              style={{ width: '100%' }}
              onClick={() => setPendingRoute({ page: 'admin-members' })}
            >
              <Icon name="users" size={12} /> Sign in
            </button>
          )}
        </div>
      </aside>

      <main className="main">
        <div className="topbar">
          <div className="topbar__crumbs">
            {route.page === 'incidents' && <strong>Incidents</strong>}
            {route.page === 'incident' && (
              <>
                <a
                  href={routeToPath({ page: 'incidents' })}
                  onClick={(e) => {
                    if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
                    e.preventDefault();
                    navigate({ page: 'incidents' });
                  }}
                  style={{ cursor: 'pointer' }}
                >
                  Incidents
                </a>
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
              onOpen={(id) => navigate({ page: 'incident', id })}
            />
          )}
          {route.page === 'incident' && currentIncident && (
            <IncidentDetail
              incident={currentIncident}
              datasources={datasources}
              teams={teams}
              members={members}
              plugins={plugins}
              onBack={() => navigate({ page: 'incidents' })}
              onResolve={resolveCurrent}
            />
          )}
          {route.page === 'incident' && !currentIncident && <div className="empty">Loading incident…</div>}
          {route.page.startsWith('admin') && (
            <AdminPanel
              key={route.page}
              members={members}
              teams={teams}
              datasources={datasources}
              plugins={plugins}
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

      {pendingRoute && (
        <LoginModal
          onClose={() => {
            if (isAdminRoute(route)) navigate({ page: 'incidents' });
            setPendingRoute(null);
          }}
          onSuccess={() => {
            setAuthed(true);
            navigate(pendingRoute);
            setPendingRoute(null);
            showToast('Signed in');
          }}
        />
      )}
      {toast && <Toast msg={toast} onDone={() => setToast(null)} />}
    </div>
  );
}

function NavLink({
  target,
  active,
  onActivate,
  children,
}: {
  target: Route;
  active: boolean;
  onActivate: (target: Route) => void;
  children: ReactNode;
}) {
  return (
    <a
      className={`nav-item ${active ? 'nav-item--active' : ''}`}
      href={routeToPath(target)}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        onActivate(target);
      }}
    >
      {children}
    </a>
  );
}
