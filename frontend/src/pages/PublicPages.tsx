import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getTimeline } from '../api/endpoints';
import type { TrackingEvent } from '../api/types';
import { Loading } from '../components/Common';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime } from '../lib/format';
import { isValidTrackingNumber, normalizeTrackingNumber } from '../lib/validation';

export function HomePage() {
  const navigate = useNavigate();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const trackingNumber = normalizeTrackingNumber(value);
    if (!isValidTrackingNumber(trackingNumber)) {
      setError('A tracking number has 8 to 32 letters and digits, for example LGK4M9X2PQ7A.');
      return;
    }
    setError(null);
    navigate(`/track/${trackingNumber}`);
  }

  return (
    <section className="hero">
      <h1>Where is my parcel?</h1>
      <p className="muted">Enter the tracking number from your order to see every step of the delivery.</p>
      <form onSubmit={submit} className="track-form" noValidate>
        <label htmlFor="tracking-number" className="sr-only">
          Tracking number
        </label>
        <input
          id="tracking-number"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder="LGK4M9X2PQ7A"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          aria-invalid={error ? true : undefined}
        />
        <button type="submit" className="btn btn-primary">
          Track
        </button>
      </form>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}

const CLOSED = new Set(['DELIVERED', 'CANCELLED']);

export function TrackPage() {
  const { trackingNumber = '' } = useParams();
  const normalized = normalizeTrackingNumber(trackingNumber);
  const valid = isValidTrackingNumber(normalized);

  const timeline = useQuery({
    queryKey: ['tracking', normalized],
    queryFn: () => getTimeline(normalized),
    enabled: valid,
    retry: false,
    refetchInterval: (state) => (state.state.data && CLOSED.has(state.state.data.status) ? false : 15000),
  });

  if (!valid) {
    return (
      <section className="card narrow">
        <h1>Invalid tracking number</h1>
        <p className="muted">A tracking number has 8 to 32 letters and digits.</p>
        <Link to="/" className="btn btn-primary">
          Try again
        </Link>
      </section>
    );
  }

  if (timeline.isPending) {
    return <Loading label="Looking up your parcel" />;
  }

  if (timeline.isError) {
    const notFound = timeline.error instanceof ApiError && timeline.error.status === 404;
    return (
      <section className="card narrow" role="alert">
        <h1>{notFound ? 'Parcel not found' : 'Cannot load tracking'}</h1>
        <p className="muted">
          {notFound
            ? `No parcel has the tracking number ${normalized}. Check the number and try again.`
            : 'The tracking service is not reachable right now. Try again in a moment.'}
        </p>
        <Link to="/" className="btn btn-primary">
          Search again
        </Link>
      </section>
    );
  }

  const data = timeline.data;
  const events: TrackingEvent[] = [...data.events].reverse();

  return (
    <section className="card narrow">
      <div className="track-summary">
        <div>
          <p className="muted">Tracking number</p>
          <h1 className="mono">{data.trackingNumber}</h1>
        </div>
        <StatusBadge status={data.status} />
      </div>
      <p className="muted">Last update {formatDateTime(data.updatedAt)}</p>
      <ol className="timeline" aria-label="Parcel history">
        {events.map((event, index) => (
          <li key={`${event.status}-${event.occurredAt}-${index}`} className={index === 0 ? 'current' : undefined}>
            <div className="timeline-head">
              <StatusBadge status={event.status} />
              <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
            </div>
            {event.description && <p>{event.description}</p>}
            {event.location && <p className="muted">{event.location}</p>}
          </li>
        ))}
      </ol>
    </section>
  );
}

export function NotFoundPage() {
  return (
    <section className="card narrow">
      <h1>Page not found</h1>
      <p className="muted">The page you opened does not exist.</p>
      <Link to="/" className="btn btn-primary">
        Go to the start page
      </Link>
    </section>
  );
}
