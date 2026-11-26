import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { delivery, mockFetch, order, product, renderApp } from '../test/utils';

async function fillCustomer() {
  await userEvent.type(screen.getByLabelText('Customer name'), 'Aziz Karimov');
  await userEvent.type(screen.getByLabelText('Customer phone'), '+998901234567');
  await userEvent.type(screen.getByLabelText('City'), 'Tashkent');
  await userEvent.type(screen.getByLabelText('Address'), 'Amir Temur 1');
}

const PRODUCTS = [product({ sku: 'BOX-M', name: 'Medium box', available: 90 }), product({ id: 'p2', sku: 'TAPE-1', name: 'Tape', available: 4 })];

describe('orders list', () => {
  it('lists orders and sends the chosen filter to the server', async () => {
    const { calls } = mockFetch([{ path: /^\/api\/orders/, body: [order()] }]);
    renderApp('/console/orders');
    expect(await screen.findByRole('link', { name: 'LGK4M9X2PQ7A' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Filter by status'), 'REJECTED');
    await waitFor(() => expect(calls.some((call) => call.url.includes('status=REJECTED'))).toBe(true));
  });

  it('reads the filter from the address', async () => {
    const { calls } = mockFetch([{ path: /^\/api\/orders/, body: [] }]);
    renderApp('/console/orders?status=DELIVERED');
    expect(await screen.findByText('No orders match this filter.')).toBeInTheDocument();
    expect(calls[0]?.url).toContain('status=DELIVERED');
    expect(screen.getByLabelText('Filter by status')).toHaveValue('DELIVERED');
  });

  it('ignores an unknown status in the address', async () => {
    const { calls } = mockFetch([{ path: /^\/api\/orders/, body: [] }]);
    renderApp('/console/orders?status=BOGUS');
    await screen.findByText('No orders match this filter.');
    expect(calls[0]?.url).not.toContain('status=');
  });

  it('enables the next page only when a full page came back', async () => {
    const full = Array.from({ length: 20 }, (_, index) => order({ id: `id-${index}`, trackingNumber: `LGAAAAAAAA${String(index).padStart(2, '0')}` }));
    mockFetch([{ path: /^\/api\/orders/, body: full }]);
    renderApp('/console/orders');
    await screen.findAllByRole('link', { name: /^LGAAAAAAAA/ });
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
  });
});

describe('new order', () => {
  it('shows every validation error and does not call the server', async () => {
    const { calls } = mockFetch([{ path: /^\/api\/warehouse\/products/, body: PRODUCTS }]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(screen.getByText('Enter the customer name.')).toBeInTheDocument();
    expect(screen.getByText('Use 9 to 15 digits with an optional leading +.')).toBeInTheDocument();
    expect(screen.getByText('Enter the city.')).toBeInTheDocument();
    expect(screen.getByText('Enter the street address.')).toBeInTheDocument();
    expect(screen.getByText('Choose a product for every line.')).toBeInTheDocument();
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });

  it('refuses a quantity that is not a whole number in range', async () => {
    mockFetch([{ path: /^\/api\/warehouse\/products/, body: PRODUCTS }]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    await fillCustomer();
    await userEvent.selectOptions(screen.getByLabelText('Product for line 1'), 'BOX-M');
    const quantity = screen.getByLabelText('Quantity for line 1');
    await userEvent.clear(quantity);
    await userEvent.type(quantity, '1001');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(screen.getByText('Quantity must be a whole number from 1 to 1000.')).toBeInTheDocument();
  });

  it('places the order with an idempotency key and opens it', async () => {
    const placed = order({ id: 'new-order-id', trackingNumber: 'LGNEWORDER01' });
    const { calls } = mockFetch([
      { path: /^\/api\/warehouse\/products/, body: PRODUCTS },
      { method: 'POST', path: '/api/orders', status: 201, body: placed },
      { path: '/api/orders/new-order-id', body: placed },
      { path: /^\/api\/delivery\/deliveries/, body: [] },
    ]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    await fillCustomer();
    await userEvent.selectOptions(screen.getByLabelText('Product for line 1'), 'BOX-M');
    const quantity = screen.getByLabelText('Quantity for line 1');
    await userEvent.clear(quantity);
    await userEvent.type(quantity, '3');
    await userEvent.click(screen.getByRole('button', { name: 'Add line' }));
    await userEvent.selectOptions(screen.getByLabelText('Product for line 2'), 'TAPE-1');

    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(await screen.findByRole('heading', { name: 'Order LGNEWORDER01' })).toBeInTheDocument();

    const post = calls.find((call) => call.method === 'POST');
    expect(post?.headers['Idempotency-Key']).toMatch(/\S{8,}/);
    expect(post?.body).toEqual({
      customerName: 'Aziz Karimov',
      customerPhone: '+998901234567',
      city: 'Tashkent',
      addressLine: 'Amir Temur 1',
      items: [
        { sku: 'BOX-M', quantity: 3 },
        { sku: 'TAPE-1', quantity: 1 },
      ],
    });
  });

  it('shows why an order was rejected and links to it', async () => {
    mockFetch([
      { path: /^\/api\/warehouse\/products/, body: PRODUCTS },
      {
        method: 'POST',
        path: '/api/orders',
        status: 409,
        body: {
          title: 'Order rejected',
          status: 409,
          detail: 'Insufficient stock for sku BOX-M: requested 5, available 1',
          orderId: 'rejected-id',
          reason: 'INSUFFICIENT_STOCK',
        },
      },
    ]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    await fillCustomer();
    await userEvent.selectOptions(screen.getByLabelText('Product for line 1'), 'BOX-M');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    const alert = await screen.findByText(/Insufficient stock for sku BOX-M/);
    expect(alert).toBeInTheDocument();
    expect(screen.getByText('Reason: INSUFFICIENT_STOCK')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the rejected order' })).toHaveAttribute('href', '/console/orders/rejected-id');
  });

  it('reuses the idempotency key when the same order is submitted again after an error', async () => {
    let attempts = 0;
    const { calls } = mockFetch([
      { path: /^\/api\/warehouse\/products/, body: PRODUCTS },
      {
        method: 'POST',
        path: '/api/orders',
        respond: () => {
          attempts += 1;
          return { status: 503, body: { title: 'Service Unavailable', status: 503, detail: 'delivery-service is down' } };
        },
      },
    ]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    await fillCustomer();
    await userEvent.selectOptions(screen.getByLabelText('Product for line 1'), 'BOX-M');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await screen.findByText('delivery-service is down');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await waitFor(() => expect(attempts).toBe(2));

    await userEvent.type(screen.getByLabelText('City'), ' 2');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await waitFor(() => expect(attempts).toBe(3));

    const keys = calls.filter((call) => call.method === 'POST').map((call) => call.headers['Idempotency-Key']);
    expect(keys[0]).toBe(keys[1]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it('keeps one line and lets lines be removed', async () => {
    mockFetch([{ path: /^\/api\/warehouse\/products/, body: PRODUCTS }]);
    renderApp('/console/orders/new');
    await screen.findByRole('option', { name: /BOX-M/ });
    expect(screen.getByRole('button', { name: 'Remove line 1' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Add line' }));
    expect(screen.getByLabelText('Product for line 2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove line 2' }));
    expect(screen.queryByLabelText('Product for line 2')).not.toBeInTheDocument();
  });
});

describe('order detail', () => {
  const id = '0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55';

  it('shows the order, its items and its delivery', async () => {
    mockFetch([
      { path: `/api/orders/${id}`, body: order({ deliveryStatus: 'PICKED_UP', status: 'IN_DELIVERY' }) },
      { path: /^\/api\/delivery\/deliveries/, body: [delivery({ status: 'PICKED_UP' })] },
    ]);
    renderApp(`/console/orders/${id}`);
    expect(await screen.findByRole('heading', { name: 'Order LGK4M9X2PQ7A' })).toBeInTheDocument();
    expect(screen.getByText('BOX-M')).toBeInTheDocument();
    expect(await screen.findByText('Bekzod, +998901112233')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Public tracking page' })).toHaveAttribute('href', '/track/LGK4M9X2PQ7A');
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('shows the rejection reason of a rejected order and no delivery', async () => {
    const { calls } = mockFetch([
      { path: `/api/orders/${id}`, body: order({ status: 'REJECTED', rejectionReason: 'NO_COURIER_AVAILABLE' }) },
    ]);
    renderApp(`/console/orders/${id}`);
    expect(await screen.findByText('NO_COURIER_AVAILABLE')).toBeInTheDocument();
    expect(calls.some((call) => call.url.includes('/api/delivery/'))).toBe(false);
  });

  it('cancels a confirmed order only after confirmation', async () => {
    const { calls } = mockFetch([
      { path: `/api/orders/${id}`, body: order({ status: 'CONFIRMED' }) },
      { path: /^\/api\/delivery\/deliveries/, body: [delivery()] },
      { method: 'POST', path: `/api/orders/${id}/cancel`, body: order({ status: 'CANCELLED' }) },
    ]);
    renderApp(`/console/orders/${id}`);
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    expect(calls.some((call) => call.method === 'POST')).toBe(false);

    const dialog = screen.getByRole('alertdialog', { name: 'Confirm cancellation' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Yes, cancel the order' }));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url.endsWith('/cancel'))).toBe(true));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(await screen.findByText('Cancelled')).toBeInTheDocument();
  });

  it('can back out of a cancellation', async () => {
    const { calls } = mockFetch([
      { path: `/api/orders/${id}`, body: order({ status: 'CONFIRMED' }) },
      { path: /^\/api\/delivery\/deliveries/, body: [] },
    ]);
    renderApp(`/console/orders/${id}`);
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await userEvent.click(screen.getByRole('button', { name: 'Keep the order' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('explains a failed cancellation', async () => {
    mockFetch([
      { path: `/api/orders/${id}`, body: order({ status: 'CONFIRMED' }) },
      { path: /^\/api\/delivery\/deliveries/, body: [] },
      { method: 'POST', path: `/api/orders/${id}/cancel`, status: 409, body: { title: 'Conflict', status: 409, detail: 'The parcel was already picked up' } },
    ]);
    renderApp(`/console/orders/${id}`);
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel the order' }));
    expect(await screen.findByText('The parcel was already picked up')).toBeInTheDocument();
  });

  it('shows an error for an unknown order', async () => {
    mockFetch([{ path: `/api/orders/${id}`, status: 404, body: { title: 'Not Found', status: 404, detail: 'Order not found' } }]);
    renderApp(`/console/orders/${id}`);
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
  });
});
