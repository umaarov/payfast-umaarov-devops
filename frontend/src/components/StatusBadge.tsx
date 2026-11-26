import { humanize } from '../lib/format';

const TONES: Record<string, string> = {
  PENDING: 'warn',
  CONFIRMED: 'info',
  IN_DELIVERY: 'info',
  DELIVERED: 'good',
  CANCELLED: 'muted',
  REJECTED: 'bad',
  ASSIGNED: 'info',
  PICKED_UP: 'info',
  IN_TRANSIT: 'info',
  FAILED: 'bad',
  AVAILABLE: 'good',
  OFF_DUTY: 'muted',
  CREATED: 'info',
  COURIER_ASSIGNED: 'info',
  DELIVERY_FAILED: 'bad',
};

export function StatusBadge({ status }: { status: string | null | undefined }) {
  if (!status) {
    return <span className="badge badge-muted">-</span>;
  }
  return <span className={`badge badge-${TONES[status] ?? 'muted'}`}>{humanize(status)}</span>;
}
