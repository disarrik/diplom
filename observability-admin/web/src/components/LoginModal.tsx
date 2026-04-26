import { useState } from 'react';
import { api } from '../api';
import { setCreds, UnauthorizedError } from '../auth';
import { Modal } from './Modal';

export function LoginModal({
  onClose,
  onSuccess,
}: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      await api.auth.me(username, password);
      setCreds(username, password);
      onSuccess();
    } catch (e) {
      if (e instanceof UnauthorizedError) setError('Invalid credentials');
      else setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Sign in"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn--primary" onClick={submit} disabled={busy || !username || !password}>
            Sign in
          </button>
        </>
      }
    >
      <div className="field">
        <div className="field__label">Username</div>
        <input
          className="field__input"
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
      </div>
      <div className="field">
        <div className="field__label">Password</div>
        <input
          className="field__input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
      </div>
      {error && <div style={{ fontSize: 12, color: 'var(--danger)' }}>{error}</div>}
    </Modal>
  );
}
