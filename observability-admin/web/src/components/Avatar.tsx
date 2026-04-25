import { colorFor, initials } from '../lib/color';

export function Avatar({
  name,
  id,
  size,
}: {
  name: string;
  id?: string;
  size?: 'md' | 'lg' | '';
}) {
  return (
    <div
      className={`avatar ${size ? 'avatar--' + size : ''}`}
      style={{ background: colorFor(id || name) }}
    >
      {initials(name)}
    </div>
  );
}

export function AvatarStack({
  items,
  max = 3,
}: {
  items: Array<{ id: string; name: string }>;
  max?: number;
}) {
  const shown = items.slice(0, max);
  const extra = items.length - shown.length;
  return (
    <div className="avatar-stack">
      {shown.map((m) => (
        <Avatar key={m.id} name={m.name} id={m.id} />
      ))}
      {extra > 0 && (
        <div className="avatar" style={{ background: '#737373', color: 'white' }}>
          +{extra}
        </div>
      )}
    </div>
  );
}
