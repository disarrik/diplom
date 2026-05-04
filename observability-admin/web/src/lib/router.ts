import { useEffect, useMemo, useState } from 'react';

export type Route =
  | { page: 'incidents' }
  | { page: 'incident'; id: string }
  | { page: 'admin-members' }
  | { page: 'admin-teams' }
  | { page: 'admin-datasources' };

export const isAdminRoute = (r: Route) => r.page.startsWith('admin');

export function routeToPath(route: Route): string {
  switch (route.page) {
    case 'incidents':
      return '/';
    case 'incident':
      return `/incidents/${encodeURIComponent(route.id)}`;
    case 'admin-members':
      return '/admin/members';
    case 'admin-teams':
      return '/admin/teams';
    case 'admin-datasources':
      return '/admin/datasources';
  }
}

export function pathToRoute(pathname: string): Route {
  const clean = pathname.replace(/\/+$/, '') || '/';
  if (clean === '/' || clean === '/incidents') return { page: 'incidents' };
  const incidentMatch = clean.match(/^\/incidents\/([^/]+)$/);
  if (incidentMatch) return { page: 'incident', id: decodeURIComponent(incidentMatch[1]) };
  if (clean === '/admin/members') return { page: 'admin-members' };
  if (clean === '/admin/teams') return { page: 'admin-teams' };
  if (clean === '/admin/datasources') return { page: 'admin-datasources' };
  return { page: 'incidents' };
}

export function useRoute(): { route: Route; navigate: (next: Route) => void } {
  const [route, setRoute] = useState<Route>(() => pathToRoute(window.location.pathname));

  useEffect(() => {
    const canonical = routeToPath(route);
    if (window.location.pathname !== canonical) {
      window.history.replaceState(null, '', canonical);
    }
    // run once on mount to canonicalize the URL
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const onPop = () => setRoute(pathToRoute(window.location.pathname));
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const navigate = useMemo(
    () => (next: Route) => {
      const path = routeToPath(next);
      if (path !== window.location.pathname) {
        window.history.pushState(null, '', path);
      }
      setRoute(next);
    },
    [],
  );

  return { route, navigate };
}
