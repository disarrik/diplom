import type { PluginFieldSpec, PluginFieldType } from '../types';

const inputTypeFor = (t: PluginFieldType): string => {
  switch (t) {
    case 'url': return 'url';
    case 'email': return 'email';
    case 'number': return 'number';
    case 'channel':
    case 'text':
    default: return 'text';
  }
};

export function PluginFields({
  schema,
  value,
  onChange,
}: {
  schema: PluginFieldSpec[];
  value: Record<string, string>;
  onChange: (next: Record<string, string>) => void;
}) {
  if (schema.length === 0) return null;
  return (
    <>
      {schema.map((f) => (
        <div key={f.key} className="field">
          <div className="field__label">
            {f.label}
            {f.required ? <span style={{ color: 'var(--danger)' }}> *</span> : null}
          </div>
          <input
            className="field__input"
            type={inputTypeFor(f.type)}
            placeholder={f.placeholder ?? undefined}
            value={value[f.key] ?? ''}
            onChange={(e) => onChange({ ...value, [f.key]: e.target.value })}
          />
        </div>
      ))}
    </>
  );
}
