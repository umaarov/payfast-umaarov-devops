import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { cancelOrder, getOrder, listDeliveries } from '../api/endpoints';
import type { Order } from '../api/types';
import { Loading, PageHeader } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime } from '../lib/format';

const ACTIVE = new Set(['PENDING', 'CONFIRMED', 'IN_DELIVERY']);

export function OrderDetailPage() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const [confirming, setConfirming] = useState(false);

  const order = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(id),
    refetchInterval: (state) => (state.state.data && ACTIVE.has(state.state.data.status) ? 5000 : false),
  });

  const deliveries = useQuery({
    queryKey: ['deliveries', 'by-order', id],
    queryFn: () => listDeliveries({ size: 200 }),
    select: (rows) => rows.find((row) => row.orderId === id) ?? null,
    enabled: order.data !== undefined && order.data.status !== 'REJECTED',
  });

  const cancel = useMutation({
    mutationFn: () => cancelOrder(id),
    onSuccess: async (updated: Order) => {
      client.setQueryData(['order', id], updated);
      setConfirming(false);
      await client.invalidateQueries({ queryKey: ['orders'] });
      await client.invalidateQueries({ queryKey: ['deliveries'] });
      await client.invalidateQueries({ queryKey: ['products'] });
    },
  });

  if (order.isPending) {
    return <Loading />;
  }
  if (order.isError) {
    return (
      <>
        <PageHeader title="Order" />
        <ErrorNotice error={order.error} />
        <Link to="/console/orders">Back to orders</Link>
      </>
    );
  }

  const data = order.data;
  const delivery = deliveries.data;

  return (
    <>
      <PageHeader
        title={`Order ${data.trackingNumber}`}
        subtitle={`Placed ${formatDateTime(data.createdAt)}`}
        actions={
          <>
            <Link to={`/track/${data.trackingNumber}`} className="btn btn-ghost">
              Public tracking page
            </Link>
            {data.status === 'CONFIRMED' && !confirming && (
              <button type="button" className="btn btn-danger" onClick={() => setConfirming(true)}>
                Cancel order
              </button>
            )}
          </>
        }
      />

      {confirming && (
        <div className="notice notice-warn" role="alertdialog" aria-label="Confirm cancellation">
          <p>
            Cancel this order? The delivery is cancelled and the reserved stock goes back to the warehouse. This cannot be
            undone.
          </p>
          <div className="form-actions">
            <button type="button" className="btn btn-danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
              {cancel.isPending ? 'Cancelling' : 'Yes, cancel the order'}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setConfirming(false)}>
              Keep the order
            </button>
          </div>
        </div>
      )}
      <ErrorNotice error={cancel.error} />

      <div className="two-column">
        <section className="card" aria-labelledby="summary-heading">
          <h2 id="summary-heading">Summary</h2>
          <dl className="facts">
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={data.status} />
              </dd>
            </div>
            <div>
              <dt>Delivery</dt>
              <dd>
                <StatusBadge status={data.deliveryStatus} />
              </dd>
            </div>
            {data.rejectionReason && (
              <div>
                <dt>Rejection reason</dt>
                <dd>{data.rejectionReason}</dd>
              </div>
            )}
            <div>
              <dt>Last update</dt>
              <dd>{formatDateTime(data.updatedAt)}</dd>
            </div>
            <div>
              <dt>Order id</dt>
              <dd className="mono">{data.id}</dd>
            </div>
          </dl>
        </section>

        <section className="card" aria-labelledby="customer-heading">
          <h2 id="customer-heading">Customer</h2>
          <dl className="facts">
            <div>
              <dt>Name</dt>
              <dd>{data.customerName}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{data.customerPhone}</dd>
            </div>
            <div>
              <dt>City</dt>
              <dd>{data.city}</dd>
            </div>
            <div>
              <dt>Address</dt>
              <dd>{data.addressLine}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section className="card" aria-labelledby="items-heading">
        <h2 id="items-heading">Items</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Quantity</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.sku}>
                  <td className="mono">{item.sku}</td>
                  <td>{item.quantity}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {delivery && (
        <section className="card" aria-labelledby="delivery-heading">
          <h2 id="delivery-heading">Delivery</h2>
          <dl className="facts">
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={delivery.status} />
              </dd>
            </div>
            <div>
              <dt>Courier</dt>
              <dd>{delivery.courier ? `${delivery.courier.name}, ${delivery.courier.phone}` : '-'}</dd>
            </div>
            {delivery.failureReason && (
              <div>
                <dt>Last failure</dt>
                <dd>{delivery.failureReason}</dd>
              </div>
            )}
          </dl>
          <Link to="/console/deliveries">Manage deliveries</Link>
        </section>
      )}
    </>
  );
}
