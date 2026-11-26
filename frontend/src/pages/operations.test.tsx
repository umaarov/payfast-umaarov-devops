import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { courier, delivery, mockFetch, product, renderApp } from '../test/utils';

describe('deliveries', () => {
  it('offers only the allowed next step for each delivery', async () => {
    mockFetch([
      { path: /^\/api\/delivery\/couriers/, body: [courier()] },
      {
        path: /^\/api\/delivery\/deliveries/,
        body: [
          delivery({ id: 'd1', trackingNumber: 'LGAAAAAAAA01', status: 'ASSIGNED' }),
          delivery({ id: 'd2', trackingNumber: 'LGAAAAAAAA02', status: 'IN_TRANSIT' }),
          delivery({ id: 'd3', trackingNumber: 'LGAAAAAAAA03', status: 'DELIVERED' }),
        ],
      },
    ]);
    renderApp('/console/deliveries');
    await screen.findByRole('link', { name: 'LGAAAAAAAA01' });
    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]!).getByRole('button', { name: 'Mark picked up' })).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole('button', { name: 'Mark delivered' })).not.toBeInTheDocument();
    expect(within(rows[1]!).getByRole('button', { name: 'Mark delivered' })).toBeInTheDocument();
    expect(within(rows[1]!).getByRole('button', { name: 'Report failed attempt' })).toBeInTheDocument();
    expect(within(rows[2]!).getByText('Finished')).toBeInTheDocument();
    expect(within(rows[2]!).queryByRole('button')).not.toBeInTheDocument();
  });

  it('sends the chosen status', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers/, body: [] },
      { path: /^\/api\/delivery\/deliveries\?/, body: [delivery({ id: 'd1', status: 'ASSIGNED' })] },
      { method: 'POST', path: '/api/delivery/deliveries/d1/status', body: delivery({ id: 'd1', status: 'PICKED_UP' }) },
    ]);
    renderApp('/console/deliveries');
    await userEvent.click(await screen.findByRole('button', { name: 'Mark picked up' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ status: 'PICKED_UP' }));
  });

  it('needs a note for a failed attempt', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers/, body: [] },
      { path: /^\/api\/delivery\/deliveries\?/, body: [delivery({ id: 'd1', status: 'IN_TRANSIT' })] },
      { method: 'POST', path: '/api/delivery/deliveries/d1/status', body: delivery({ id: 'd1', status: 'FAILED' }) },
    ]);
    renderApp('/console/deliveries');
    await userEvent.click(await screen.findByRole('button', { name: 'Report failed attempt' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save failure' }));
    expect(screen.getByText('Describe what went wrong.')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST')).toBe(false);

    await userEvent.type(screen.getByLabelText(/Failure note/), 'Recipient not at home');
    await userEvent.click(screen.getByRole('button', { name: 'Save failure' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ status: 'FAILED', note: 'Recipient not at home' }));
  });

  it('shows the server message when a step is refused', async () => {
    mockFetch([
      { path: /^\/api\/delivery\/couriers/, body: [] },
      { path: /^\/api\/delivery\/deliveries\?/, body: [delivery({ id: 'd1', status: 'ASSIGNED' })] },
      { method: 'POST', path: '/api/delivery/deliveries/d1/status', status: 409, body: { title: 'Conflict', status: 409, detail: 'Delivery was changed by someone else' } },
    ]);
    renderApp('/console/deliveries');
    await userEvent.click(await screen.findByRole('button', { name: 'Mark picked up' }));
    expect(await screen.findByText('Delivery was changed by someone else')).toBeInTheDocument();
  });

  it('filters by courier and status on the server', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers/, body: [courier({ id: 'c-1', name: 'Bekzod' })] },
      { path: /^\/api\/delivery\/deliveries\?/, body: [] },
    ]);
    renderApp('/console/deliveries');
    await screen.findByText('No deliveries match these filters.');
    await screen.findByRole('option', { name: 'Bekzod' });
    await userEvent.selectOptions(screen.getByLabelText('Filter by courier'), 'c-1');
    await userEvent.selectOptions(screen.getByLabelText('Filter by status'), 'FAILED');
    await waitFor(() => expect(calls.some((call) => call.url.includes('courierId=c-1') && call.url.includes('status=FAILED'))).toBe(true));
  });
});

describe('warehouse', () => {
  it('lists stock counters', async () => {
    mockFetch([{ path: /^\/api\/warehouse\/products/, body: [product({ quantityOnHand: 100, quantityReserved: 10, available: 90 })] }]);
    renderApp('/console/warehouse');
    const row = (await screen.findByText('Medium box')).closest('tr')!;
    expect(within(row).getByText('100')).toBeInTheDocument();
    expect(within(row).getByText('10')).toBeInTheDocument();
    expect(within(row).getByText('90')).toBeInTheDocument();
  });

  it('validates a new product before calling the server', async () => {
    const { calls } = mockFetch([{ path: /^\/api\/warehouse\/products/, body: [] }]);
    renderApp('/console/warehouse');
    await screen.findByText('No products to show.');
    await userEvent.type(screen.getByLabelText('SKU'), 'bad sku');
    await userEvent.type(screen.getByLabelText('Initial quantity'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Add product' }));
    expect(screen.getByText('Use letters, digits, dot, underscore or dash, up to 64 characters.')).toBeInTheDocument();
    expect(screen.getByText('Enter a product name.')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('creates a product', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/warehouse\/products\?/, body: [] },
      { method: 'POST', path: '/api/warehouse/products', status: 201, body: product({ sku: 'NEW-1' }) },
    ]);
    renderApp('/console/warehouse');
    await screen.findByText('No products to show.');
    await userEvent.type(screen.getByLabelText('SKU'), 'NEW-1');
    await userEvent.type(screen.getByLabelText('Name'), 'New thing');
    await userEvent.clear(screen.getByLabelText('Initial quantity'));
    await userEvent.type(screen.getByLabelText('Initial quantity'), '25');
    await userEvent.click(screen.getByRole('button', { name: 'Add product' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ sku: 'NEW-1', name: 'New thing', initialQuantity: 25 }));
  });

  it('shows a duplicate sku error from the server', async () => {
    mockFetch([
      { path: /^\/api\/warehouse\/products\?/, body: [] },
      { method: 'POST', path: '/api/warehouse/products', status: 409, body: { title: 'Conflict', status: 409, detail: 'Product BOX-M already exists' } },
    ]);
    renderApp('/console/warehouse');
    await screen.findByText('No products to show.');
    await userEvent.type(screen.getByLabelText('SKU'), 'BOX-M');
    await userEvent.type(screen.getByLabelText('Name'), 'Medium box');
    await userEvent.click(screen.getByRole('button', { name: 'Add product' }));
    expect(await screen.findByText('Product BOX-M already exists')).toBeInTheDocument();
  });

  it('receives stock for one product', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/warehouse\/products\?/, body: [product({ sku: 'BOX-M' })] },
      { method: 'POST', path: '/api/warehouse/products/BOX-M/receipts', body: product({ sku: 'BOX-M', quantityOnHand: 125, available: 115 }) },
    ]);
    renderApp('/console/warehouse');
    await userEvent.click(await screen.findByRole('button', { name: 'Receive' }));
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST')).toBe(false);

    await userEvent.type(screen.getByLabelText('Quantity to receive for BOX-M'), '25');
    await userEvent.click(screen.getByRole('button', { name: 'Receive' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ quantity: 25 }));
  });

  it('filters the page by name', async () => {
    mockFetch([{ path: /^\/api\/warehouse\/products/, body: [product({ sku: 'BOX-M', name: 'Medium box' }), product({ id: 'p2', sku: 'TAPE-1', name: 'Tape' })] }]);
    renderApp('/console/warehouse');
    await screen.findByText('Medium box');
    await userEvent.type(screen.getByLabelText('Filter products'), 'tape');
    expect(screen.queryByText('Medium box')).not.toBeInTheDocument();
    expect(screen.getByText('Tape')).toBeInTheDocument();
  });
});

describe('couriers', () => {
  it('shows load and toggles availability', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers\?/, body: [courier({ id: 'c1', status: 'AVAILABLE', activeDeliveries: 2, maxActiveDeliveries: 5 })] },
      { method: 'POST', path: '/api/delivery/couriers/c1/availability', body: courier({ id: 'c1', status: 'OFF_DUTY' }) },
    ]);
    renderApp('/console/couriers');
    expect(await screen.findByText('2 of 5')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Take off duty' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ available: false }));
  });

  it('offers to put an off-duty courier back on duty', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers\?/, body: [courier({ id: 'c1', status: 'OFF_DUTY' })] },
      { method: 'POST', path: '/api/delivery/couriers/c1/availability', body: courier({ id: 'c1' }) },
    ]);
    renderApp('/console/couriers');
    await userEvent.click(await screen.findByRole('button', { name: 'Put on duty' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ available: true }));
  });

  it('validates and creates a courier', async () => {
    const { calls } = mockFetch([
      { path: /^\/api\/delivery\/couriers\?/, body: [] },
      { method: 'POST', path: '/api/delivery/couriers', status: 201, body: courier() },
    ]);
    renderApp('/console/couriers');
    await screen.findByText(/No couriers yet/);
    await userEvent.click(screen.getByRole('button', { name: 'Add courier' }));
    expect(screen.getByText('Enter the courier name.')).toBeInTheDocument();
    expect(screen.getByText('Use 9 to 15 digits with an optional leading +.')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST')).toBe(false);

    await userEvent.type(screen.getByLabelText('Name'), 'Bekzod');
    await userEvent.type(screen.getByLabelText('Phone'), '+998901112233');
    await userEvent.clear(screen.getByLabelText('Parcels at the same time'));
    await userEvent.type(screen.getByLabelText('Parcels at the same time'), '51');
    await userEvent.click(screen.getByRole('button', { name: 'Add courier' }));
    expect(screen.getByText('Enter a whole number from 1 to 50.')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Parcels at the same time'));
    await userEvent.type(screen.getByLabelText('Parcels at the same time'), '4');
    await userEvent.click(screen.getByRole('button', { name: 'Add courier' }));
    await waitFor(() => expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ name: 'Bekzod', phone: '+998901112233', maxActiveDeliveries: 4 }));
  });
});
