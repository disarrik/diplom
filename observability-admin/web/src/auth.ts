const KEY = 'auth';

export function getCreds(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setCreds(username: string, password: string): void {
  sessionStorage.setItem(KEY, btoa(`${username}:${password}`));
}

export function clearCreds(): void {
  sessionStorage.removeItem(KEY);
}

export function authHeader(): Record<string, string> {
  const c = getCreds();
  return c ? { Authorization: `Basic ${c}` } : {};
}

export class UnauthorizedError extends Error {
  constructor() {
    super('Unauthorized');
    this.name = 'UnauthorizedError';
  }
}
