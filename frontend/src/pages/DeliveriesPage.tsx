import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listCouriers, listDeliveries, updateDeliveryStatus } from '../api/endpoints';
import { DELIVERY_STATUSES, type Delivery, type DeliveryStatus } from '../api/types';
import { EmptyState, Loading, PageHeader, Pager } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, humanize } from '../lib/format';
import { needsNote, nextDeliveryStatuses } from '../lib/deliveryFlow';

const PAGE_SIZE = 20;

const ACTION_LABELS: Record<DeliveryStatus, string> = {
  ASSIGNED: 'Assign',
  PICKED_UP: 'Mark picked up',
  IN_TRANSIT: 'Mark in transit',
  DELIVERED: 'Mark delivered',
  FAILED: 'Report failed attempt',
  CANCELLED: 'Cancel',
};

function DeliveryRow({ delivery }: { delivery: Delivery }) {
  const client = useQueryClient();
  const [failing, setFailing] = useState(false);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);

  const update = useMutation({
    mutationFn: ({ status, text }: { status: DeliveryStatus; text?: string }) =>
      updateDeliveryStatus(delivery.id, status, text),
    onSuccess: async () => {
      setFailing(false);
      setNote('');
      await client.invalidateQueries({ queryKey: ['deliveries'] });
      await client.invalidateQueries({ queryKey: ['couriers'] });
      await client.invalidateQueries({ queryKey: ['orders'] });
      await client.invalidateQueries({ queryKey: ['order'] });
    },
  });

  function choose(status: DeliveryStatus) {
    if (needsNote(status)) {
      setFailing(true);
      return;
    }
    update.mutate({ status });
  }

  function submitFailure(event: FormEvent) {
    event.preventDefault();
    const text = note.trim();
    if (!text) {
      setNoteError('Describe what went wrong.');
      return;
    }
    setNoteError(null);
    update.mutate({ status: 'FAILED', text });
  }

  const next = nextDeliveryStatuses(delivery.status);

  return (
    <tr>
      <td>
        <Link to={`/console/orders/${delivery.orderId}`} className="mono">
          {delivery.trackingNumber}
        </Link>
        <div className="muted">{formatDateTime(delivery.createdAt)}</div>
      </td>
      <td>
        {delivery.recipientName}
        <div className="muted">{delivery.recipientPhone}</div>
      </td>
      <td>
        {delivery.city}
        <div className="muted">{delivery.addressLine}</div>
      </td>
      <td>{delivery.courier?.name ?? '-'}</td>
      <td>
        <StatusBadge status={delivery.status} />
        {delivery.failureReason && <div className="muted">{delivery.failureReason}</div>}
      </td>
      <td>
        {next.length === 0 && <span className="muted">Finished</span>}
        <div className="actions">
          {next.map((status) => (
            <button
              key={status}
              type="button"
              className={status === 'FAILED' ? 'btn btn-ghost' : 'btn btn-primary'}
              onClick={() => choose(status)}
              disabled={update.isPending}
            >
              {ACTION_LABELS[status]}
            </button>
          ))}
        </div>
        {failing && (
          <form className="inline-form" onSubmit={submitFailure} noValidate>
            <input
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Recipient not at home"
              maxLength={255}
              aria-label={`Failure note for ${delivery.trackingNumber}`}
              aria-invalid={noteError ? true : undefined}
            />
            <button type="submit" className="btn btn-danger" disabled={update.isPending}>
              Save failure
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setFailing(false)}>
              Back
            </button>
          </form>
        )}
        {noteError && (
          <span className="field-error" role="alert">
            {noteError}
          </span>
        )}
        <ErrorNotice error={update.error} />
      </td>
    </tr>
  );
}

function parseStatus(value: string | null): DeliveryStatus | '' {
  return DELIVERY_STATUSES.find((status) => status === value) ?? '';
}

export function DeliveriesPage() {
  const [params, setParams] = useSearchParams();
  const status = parseStatus(params.get('status'));
  const courierId = params.get('courierId') ?? '';
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);

  const couriers = useQuery({ queryKey: ['couriers', 'all'], queryFn: () => listCouriers({ size: 200 }) });
  const deliveries = useQuery({
    queryKey: ['deliveries', { status, courierId, page }],
    queryFn: () => listDeliveries({ status, courierId: courierId || undefined, page, size: PAGE_SIZE }),
    refetchInterval: 10000,
    placeholderData: (previous) => previous,
  });

  function update(changes: Record<string, string | undefined>, keepPage = false) {
    const merged = new URLSearchParams(params);
    for (const [name, value] of Object.entries(changes)) {
      if (value) {
        merged.set(name, value);
      } else {
        merged.delete(name);
      }
    }
    if (!keepPage) {
      merged.delete('page');
    }
    setParams(merged);
  }

  return (
    <>
      <PageHeader title="Deliveries" subtitle="Move each parcel through its steps. Orders and tracking follow automatically" />
      <div className="toolbar">
        <select value={status} onChange={(event) => update({ status: event.target.value })} aria-label="Filter by status">
          <option value="">All statuses</option>
          {DELIVERY_STATUSES.map((entry) => (
            <option key={entry} value={entry}>
              {humanize(entry)}
            </option>
          ))}
        </select>
        <select value={courierId} onChange={(event) => update({ courierId: event.target.value })} aria-label="Filter by courier">
          <option value="">All couriers</option>
          {couriers.data?.map((courier) => (
            <option key={courier.id} value={courier.id}>
              {courier.name}
            </option>
          ))}
        </select>
      </div>
      <ErrorNotice error={deliveries.error} />
      {deliveries.isPending && <Loading />}
      {deliveries.data && deliveries.data.length === 0 && <EmptyState>No deliveries match these filters.</EmptyState>}
      {deliveries.data && deliveries.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Parcel</th>
                <th>Recipient</th>
                <th>Address</th>
                <th>Courier</th>
                <th>Status</th>
                <th>Next step</th>
              </tr>
            </thead>
            <tbody>
              {deliveries.data.map((delivery) => (
                <DeliveryRow key={delivery.id} delivery={delivery} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager
        page={page}
        hasNext={(deliveries.data?.length ?? 0) === PAGE_SIZE}
        onChange={(next) => update({ page: next > 0 ? String(next) : undefined }, true)}
      />
    </>
  );
}
