import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listCouriers, listOrders, listProducts } from '../api/endpoints';
import { ORDER_STATUSES } from '../api/types';
import { EmptyState, Loading, PageHeader } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, humanize } from '../lib/format';
import { countOrdersByStatus, lowStock, summarizeCouriers } from '../lib/stats';

const LOW_STOCK_THRESHOLD = 5;

export function DashboardPage() {
  const orders = useQuery({ queryKey: ['orders', 'dashboard'], queryFn: () => listOrders({ size: 200 }), refetchInterval: 15000 });
  const products = useQuery({ queryKey: ['products', 'all'], queryFn: () => listProducts({ size: 200 }), refetchInterval: 30000 });
  const couriers = useQuery({ queryKey: ['couriers', 'all'], queryFn: () => listCouriers({ size: 200 }), refetchInterval: 30000 });

  const error = orders.error ?? products.error ?? couriers.error;
  const counts = orders.data ? countOrdersByStatus(orders.data) : null;
  const low = products.data ? lowStock(products.data, LOW_STOCK_THRESHOLD) : null;
  const load = couriers.data ? summarizeCouriers(couriers.data) : null;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Based on the latest 200 records of each kind"
        actions={
          <Link to="/console/orders/new" className="btn btn-primary">
            New order
          </Link>
        }
      />
      <ErrorNotice error={error} />

      <section aria-labelledby="orders-heading">
        <h2 id="orders-heading">Orders</h2>
        {orders.isPending ? (
          <Loading />
        ) : counts ? (
          <div className="stat-grid">
            {ORDER_STATUSES.map((status) => (
              <Link key={status} to={`/console/orders?status=${status}`} className="stat">
                <span className="stat-value">{counts[status]}</span>
                <span className="stat-label">{humanize(status)}</span>
              </Link>
            ))}
          </div>
        ) : null}
      </section>

      <div className="two-column">
        <section className="card" aria-labelledby="stock-heading">
          <h2 id="stock-heading">Low stock</h2>
          {products.isPending ? (
            <Loading />
          ) : low && low.length === 0 ? (
            <EmptyState>Every product has more than {LOW_STOCK_THRESHOLD} units available.</EmptyState>
          ) : low ? (
            <ul className="plain-list">
              {low.map((product) => (
                <li key={product.id}>
                  <span className="mono">{product.sku}</span>
                  <span>{product.available} available</span>
                </li>
              ))}
            </ul>
          ) : null}
          <Link to="/console/warehouse">Open the warehouse</Link>
        </section>

        <section className="card" aria-labelledby="couriers-heading">
          <h2 id="couriers-heading">Couriers</h2>
          {couriers.isPending ? (
            <Loading />
          ) : load ? (
            <dl className="facts">
              <div>
                <dt>Available</dt>
                <dd>{load.available}</dd>
              </div>
              <div>
                <dt>Off duty</dt>
                <dd>{load.offDuty}</dd>
              </div>
              <div>
                <dt>Active deliveries</dt>
                <dd>
                  {load.active} of {load.capacity} slots
                </dd>
              </div>
            </dl>
          ) : null}
          <Link to="/console/couriers">Open the couriers</Link>
        </section>
      </div>

      <section className="card" aria-labelledby="recent-heading">
        <h2 id="recent-heading">Recent orders</h2>
        {orders.data && orders.data.length === 0 && <EmptyState>No orders yet.</EmptyState>}
        {orders.data && orders.data.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Created</th>
                  <th>Tracking</th>
                  <th>Customer</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {orders.data.slice(0, 5).map((order) => (
                  <tr key={order.id}>
                    <td>{formatDateTime(order.createdAt)}</td>
                    <td>
                      <Link to={`/console/orders/${order.id}`} className="mono">
                        {order.trackingNumber}
                      </Link>
                    </td>
                    <td>{order.customerName}</td>
                    <td>
                      <StatusBadge status={order.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
