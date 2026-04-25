import type {
  Datasource,
  Incident,
  IntegrationProviderInfo,
  Member,
  Team,
} from './types';

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
  });
  if (!res.ok) throw new Error(`${init?.method || 'GET'} ${path} → ${res.status}`);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  members: {
    list: () => req<Member[]>('/api/members'),
    create: (m: Omit<Member, 'id'>) => req<Member>('/api/members', { method: 'POST', body: JSON.stringify(m) }),
    update: (m: Member) => req<Member>(`/api/members/${m.id}`, { method: 'PUT', body: JSON.stringify(m) }),
    remove: (id: string) => req<void>(`/api/members/${id}`, { method: 'DELETE' }),
  },
  teams: {
    list: () => req<Team[]>('/api/teams'),
    create: (t: Omit<Team, 'id'>) => req<Team>('/api/teams', { method: 'POST', body: JSON.stringify(t) }),
    update: (t: Team) => req<Team>(`/api/teams/${t.id}`, { method: 'PUT', body: JSON.stringify(t) }),
    remove: (id: string) => req<void>(`/api/teams/${id}`, { method: 'DELETE' }),
  },
  datasources: {
    list: () => req<Datasource[]>('/api/datasources'),
    create: (d: Omit<Datasource, 'id'>) =>
      req<Datasource>('/api/datasources', { method: 'POST', body: JSON.stringify(d) }),
    update: (d: Datasource) =>
      req<Datasource>(`/api/datasources/${d.id}`, { method: 'PUT', body: JSON.stringify(d) }),
    remove: (id: string) => req<void>(`/api/datasources/${id}`, { method: 'DELETE' }),
  },
  incidents: {
    list: () => req<Incident[]>('/api/incidents'),
    get: (id: string) => req<Incident>(`/api/incidents/${id}`),
    resolve: (id: string) => req<Incident>(`/api/incidents/${id}/resolve`, { method: 'POST' }),
    attachIntegration: (id: string, providerId: string, params: Record<string, string>) =>
      req<Incident>(`/api/incidents/${id}/integrations`, {
        method: 'POST',
        body: JSON.stringify({ providerId, params }),
      }),
  },
  integrations: {
    providers: () => req<IntegrationProviderInfo[]>('/api/integrations/providers'),
  },
};
