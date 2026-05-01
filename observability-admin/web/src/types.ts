export interface Member {
  id: string;
  name: string;
  email: string;
  role: string;
  teamIds: string[];
  extensions?: Record<string, Record<string, string>>;
}

export interface Team {
  id: string;
  name: string;
  handle: string;
  extensions?: Record<string, Record<string, string>>;
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
  pluginId?: string | null;
  extra?: Record<string, string>;
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

export type PluginFieldType = 'text' | 'url' | 'channel' | 'email' | 'number';

export interface PluginFieldSpec {
  key: string;
  label: string;
  type: PluginFieldType;
  placeholder?: string | null;
  required?: boolean;
}

export interface PluginDisplayMeta {
  color: string;
  iconText: string;
  cardTitle: string;
}

export type PluginCardKind = 'link' | 'info';

export interface PluginDescriptor {
  id: string;
  label: string;
  displayMeta: PluginDisplayMeta;
  teamFields: PluginFieldSpec[];
  memberFields: PluginFieldSpec[];
  cardKind: PluginCardKind;
}
