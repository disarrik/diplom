const AVATAR_COLORS = [
  '#f97316', '#3b82f6', '#10b981', '#a855f7',
  '#ec4899', '#06b6d4', '#eab308', '#ef4444',
];

export function colorFor(id: string): string {
  const sum = [...id].reduce((a, c) => a + c.charCodeAt(0), 0);
  return AVATAR_COLORS[Math.abs(sum) % AVATAR_COLORS.length];
}

export function initials(name: string): string {
  return name
    .split(' ')
    .map((p) => p[0] || '')
    .slice(0, 2)
    .join('')
    .toUpperCase();
}
