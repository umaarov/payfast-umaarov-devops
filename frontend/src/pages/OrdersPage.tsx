import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { listOrders } from '../api/endpoints';
import { ORDER_STATUSES, type OrderStatus } from '../api/types';
import { EmptyState, Loading, PageHeader, Pager } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, humanize } from '../lib/format';

const PAGE_SIZE = 20;

function parseStatus(value: string | null): OrderStatus | '' {
  return ORDER_STATUSES.find((status) => status === value) ?? '';
}

export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = parseStatus(params.get('status'));
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);

  const orders = useQuery({
    queryKey: ['orders', { status, page }],
    queryFn: () => listOrders({ status, page, size: PAGE_SIZE }),
    refetchInterval: 15000,
    placeholderData: (previous) => previous,
  });

  function update(next: { status?: string; page?: number }) {
    const merged = new URLSearchParams(params);
    if (next.status !== undefined) {
      if (next.status) {
        merged.set('status', next.status);
      } else {
        merged.delete('status');
      }
      merged.delete('page');
    }
    if (next.page !== undefined) {
      if (next.page > 0) {
        merged.set('page', String(next.page));
      } else {
        merged.delete('page');
      }
    }
    setParams(merged);
  }

  return (
    <>
      <PageHeader
        title="Orders"
        actions={
          <Link to="/console/orders/new" className="btn btn-primary">
            New order
          </Link>
        }
      />
      <div className="toolbar">
        <label>
          <span className="sr-only">Filter by status</span>
          <select value={status} onChange={(event) => update({ status: event.target.value })} aria-label="Filter by status">
            <option value="">All statuses</option>
            {ORDER_STATUSES.map((entry) => (
              <option key={entry} value={entry}>
                {humanize(entry)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ErrorNotice error={orders.error} />
      {orders.isPending && <Loading />}
      {orders.data && orders.data.length === 0 && <EmptyState>No orders match this filter.</EmptyState>}
      {orders.data && orders.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Created</th>
                <th>Tracking</th>
                <th>Customer</th>
                <th>City</th>
                <th>Items</th>
                <th>Status</th>
                <th>Delivery</th>
              </tr>
            </thead>
            <tbody>
              {orders.data.map((order) => (
                <tr key={order.id}>
                  <td>{formatDateTime(order.createdAt)}</td>
                  <td>
                    <Link to={`/console/orders/${order.id}`} className="mono">
                      {order.trackingNumber}
                    </Link>
                  </td>
                  <td>{order.customerName}</td>
                  <td>{order.city}</td>
                  <td>{order.items.reduce((sum, item) => sum + item.quantity, 0)}</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                  <td>
                    <StatusBadge status={order.deliveryStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager page={page} hasNext={(orders.data?.length ?? 0) === PAGE_SIZE} onChange={(next) => update({ page: next })} />
    </>
  );
}
