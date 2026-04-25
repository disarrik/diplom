import { useEffect } from 'react';
import { Icon } from './Icon';

export function Toast({ msg, onDone }: { msg: string; onDone: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDone, 2400);
    return () => clearTimeout(t);
  }, [msg, onDone]);
  return (
    <div className="toast">
      <Icon name="check" size={14} /> {msg}
    </div>
  );
}
