import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return error.detail;
    }
    if (error.detail) {
      return error.detail;
    }
    return `${error.title} (${error.status})`;
  }
  return 'Something went wrong. Try again.';
}

export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const api = error instanceof ApiError ? error : null;
  return (
    <div className="notice notice-error" role="alert">
      <strong>{api?.title ?? 'Error'}</strong>
      <p>{messageFor(error)}</p>
      {api && api.errors.length > 0 && (
        <ul>
          {api.errors.map((entry) => (
            <li key={entry}>{entry}</li>
          ))}
        </ul>
      )}
      {api?.reason && <p className="muted">Reason: {api.reason}</p>}
      {api?.orderId && (
        <p>
          <Link to={`/console/orders/${api.orderId}`}>Open the rejected order</Link>
        </p>
      )}
    </div>
  );
}
