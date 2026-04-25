import { useEffect, useRef, useState } from 'react';
import { Icon } from './Icon';

interface Option {
  id: string;
}

export function MultiSelect<T extends Option>({
  options,
  selected,
  onChange,
  placeholder = 'Select…',
  getLabel,
  renderOption,
}: {
  options: T[];
  selected: string[];
  onChange: (ids: string[]) => void;
  placeholder?: string;
  getLabel: (opt: T) => string;
  renderOption?: (opt: T) => React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const toggle = (id: string) => {
    onChange(selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id]);
  };

  return (
    <div className="multi-select" ref={ref}>
      <div
        className={`multi-select__control ${open ? 'multi-select__control--open' : ''}`}
        onClick={() => setOpen((o) => !o)}
      >
        {selected.length === 0 && <span className="multi-select__placeholder">{placeholder}</span>}
        {selected.map((id) => {
          const opt = options.find((o) => o.id === id);
          if (!opt) return null;
          return (
            <span key={id} className="multi-select__tag">
              {getLabel(opt)}
              <button
                className="multi-select__tag-x"
                onClick={(e) => {
                  e.stopPropagation();
                  toggle(id);
                }}
              >
                <Icon name="x" size={10} />
              </button>
            </span>
          );
        })}
      </div>
      {open && (
        <div className="multi-select__menu" onClick={(e) => e.stopPropagation()}>
          {options.length === 0 && (
            <div className="empty" style={{ padding: 16 }}>
              No options
            </div>
          )}
          {options.map((opt) => {
            const isSel = selected.includes(opt.id);
            return (
              <div
                key={opt.id}
                className={`multi-select__option ${isSel ? 'multi-select__option--selected' : ''}`}
                onClick={() => toggle(opt.id)}
              >
                <span className="multi-select__check">
                  {isSel && <Icon name="check" size={10} />}
                </span>
                {renderOption ? renderOption(opt) : <span>{getLabel(opt)}</span>}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
