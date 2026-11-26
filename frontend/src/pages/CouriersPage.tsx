import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createCourier, listCouriers, setCourierAvailability } from '../api/endpoints';
import type { Courier } from '../api/types';
import { EmptyState, Field, Loading, PageHeader, Pager } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { StatusBadge } from '../components/StatusBadge';
import { isValidPhone, parseWholeNumber } from '../lib/validation';

const PAGE_SIZE = 20;

function CreateCourierForm() {
  const client = useQueryClient();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [capacity, setCapacity] = useState('5');
  const [errors, setErrors] = useState<Partial<Record<'name' | 'phone' | 'capacity', string>>>({});

  const create = useMutation({
    mutationFn: createCourier,
    onSuccess: async () => {
      setName('');
      setPhone('');
      setCapacity('5');
      await client.invalidateQueries({ queryKey: ['couriers'] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const found: typeof errors = {};
    const maxActiveDeliveries = parseWholeNumber(capacity);
    if (!name.trim()) {
      found.name = 'Enter the courier name.';
    }
    if (!isValidPhone(phone.trim())) {
      found.phone = 'Use 9 to 15 digits with an optional leading +.';
    }
    if (maxActiveDeliveries === null || maxActiveDeliveries < 1 || maxActiveDeliveries > 50) {
      found.capacity = 'Enter a whole number from 1 to 50.';
    }
    setErrors(found);
    if (Object.keys(found).length > 0 || maxActiveDeliveries === null) {
      return;
    }
    create.mutate({ name: name.trim(), phone: phone.trim(), maxActiveDeliveries });
  }

  return (
    <form className="card form" onSubmit={submit} noValidate aria-label="Add a courier">
      <h2>Add a courier</h2>
      <div className="form-grid">
        <Field label="Name" error={errors.name}>
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength={120} />
        </Field>
        <Field label="Phone" error={errors.phone}>
          <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="+998901112233" inputMode="tel" />
        </Field>
        <Field label="Parcels at the same time" error={errors.capacity}>
          <input value={capacity} onChange={(event) => setCapacity(event.target.value)} inputMode="numeric" />
        </Field>
      </div>
      <ErrorNotice error={create.error} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={create.isPending}>
          {create.isPending ? 'Saving' : 'Add courier'}
        </button>
      </div>
    </form>
  );
}

function CourierRow({ courier }: { courier: Courier }) {
  const client = useQueryClient();
  const toggle = useMutation({
    mutationFn: () => setCourierAvailability(courier.id, courier.status !== 'AVAILABLE'),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['couriers'] });
    },
  });
  const percent = Math.min(100, Math.round((courier.activeDeliveries / courier.maxActiveDeliveries) * 100));

  return (
    <tr>
      <td>{courier.name}</td>
      <td>{courier.phone}</td>
      <td>
        <StatusBadge status={courier.status} />
      </td>
      <td>
        <div className="load" title={`${courier.activeDeliveries} of ${courier.maxActiveDeliveries}`}>
          <div className="load-bar" style={{ width: `${percent}%` }} />
        </div>
        <span className="muted">
          {courier.activeDeliveries} of {courier.maxActiveDeliveries}
        </span>
      </td>
      <td>
        <button type="button" className="btn btn-ghost" onClick={() => toggle.mutate()} disabled={toggle.isPending}>
          {courier.status === 'AVAILABLE' ? 'Take off duty' : 'Put on duty'}
        </button>
        {toggle.error && <ErrorNotice error={toggle.error} />}
      </td>
    </tr>
  );
}

export function CouriersPage() {
  const [page, setPage] = useState(0);
  const couriers = useQuery({
    queryKey: ['couriers', { page }],
    queryFn: () => listCouriers({ page, size: PAGE_SIZE }),
    refetchInterval: 15000,
    placeholderData: (previous) => previous,
  });

  return (
    <>
      <PageHeader title="Couriers" subtitle="A courier off duty keeps the parcels already assigned and gets no new ones" />
      <CreateCourierForm />
      <ErrorNotice error={couriers.error} />
      {couriers.isPending && <Loading />}
      {couriers.data && couriers.data.length === 0 && <EmptyState>No couriers yet. Add one so orders can be assigned.</EmptyState>}
      {couriers.data && couriers.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone</th>
                <th>Status</th>
                <th>Load</th>
                <th>Availability</th>
              </tr>
            </thead>
            <tbody>
              {couriers.data.map((courier) => (
                <CourierRow key={courier.id} courier={courier} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager page={page} hasNext={(couriers.data?.length ?? 0) === PAGE_SIZE} onChange={setPage} />
    </>
  );
}
