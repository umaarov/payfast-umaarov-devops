import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { listProducts } from '../api/endpoints';
import { setApiKey } from '../auth/keyStore';
import { useApiKey } from '../auth/useApiKey';
import { Field } from '../components/Common';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const existing = useApiKey();
  const [key, setKey] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const destination = (location.state as LocationState | null)?.from ?? '/console';

  if (existing) {
    return <Navigate to={destination} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const candidate = key.trim();
    if (!candidate) {
      setError('Enter the API key.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await listProducts({ size: 1 }, candidate);
      setApiKey(candidate);
      navigate(destination, { replace: true });
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) {
        setError('This API key was not accepted.');
      } else if (failure instanceof ApiError && failure.status === 0) {
        setError(failure.detail);
      } else {
        setError('The key could not be checked. Try again in a moment.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card narrow">
      <h1>Staff sign in</h1>
      <p className="muted">
        The console talks to the gateway with an API key. The key is kept in this browser tab only and is removed when
        the tab closes or you sign out.
      </p>
      <form onSubmit={submit} className="form" noValidate>
        <Field label="API key" error={error ?? undefined}>
          <input
            type="password"
            value={key}
            onChange={(event) => setKey(event.target.value)}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={error ? true : undefined}
          />
        </Field>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Checking' : 'Sign in'}
        </button>
      </form>
    </section>
  );
}
