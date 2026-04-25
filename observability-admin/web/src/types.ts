export interface Member {
  id: string;
  name: string;
  email: string;
  role: string;
  teamIds: string[];
}

export interface Team {
  id: string;
  name: string;
  handle: string;
  slack: string;
}

export interface Datasource {
  id: string;
  namespace: string;
  name: string;
  type: string;
  host: string;
  teamIds: string[];
}

export interface Integration {
  type: string;
  label: string;
  url: string;
}

export interface IncidentEvent {
  type: string;
  at: string;
  actor: string;
  text: string;
  detail?: string | null;
}

export type IncidentStatus = 'open' | 'resolved';

export interface Incident {
  id: string;
  title: string;
  incidentType: string;
  rootDsId: string;
  affectedDsIds: string[];
  teamId: string | null;
  assigneeId: string | null;
  status: IncidentStatus;
  openedAt: string;
  resolvedAt?: string | null;
  integrations: Integration[];
  events: IncidentEvent[];
}

export interface IntegrationProviderInfo {
  id: string;
  label: string;
}
