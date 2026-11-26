import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { getApiKey } from '../auth/keyStore';
import { delivery, mockFetch, order, product, renderApp, TEST_KEY } from '../test/utils';

const TRACKING = 'LGK4M9X2PQ7A';

describe('home and tracking', () => {
  it('rejects a malformed tracking number without calling the server', async () => {
    const { calls } = mockFetch([]);
    renderApp('/', { signedIn: false });
    await userEvent.type(screen.getByLabelText('Tracking number'), 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Track' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/8 to 32 letters and digits/);
    expect(calls).toHaveLength(0);
  });

  it('opens the timeline for a valid number, newest event first, without sending a key', async () => {
    const { calls } = mockFetch([
      {
        path: `/api/tracking/${TRACKING}`,
        body: {
          trackingNumber: TRACKING,
          status: 'COURIER_ASSIGNED',
          updatedAt: '2026-11-25T09:00:00Z',
          events: [
            { status: 'CREATED', description: 'Order accepted', location: 'Tashkent', occurredAt: '2026-11-25T08:00:00Z' },
            { status: 'COURIER_ASSIGNED', description: 'Courier assigned', location: 'Tashkent', occurredAt: '2026-11-25T09:00:00Z' },
          ],
        },
      },
    ]);
    renderApp('/', { signedIn: false });
    await userEvent.type(screen.getByLabelText('Tracking number'), ' lgk4m9x2pq7a ');
    await userEvent.click(screen.getByRole('button', { name: 'Track' }));

    const history = await screen.findByRole('list', { name: 'Parcel history' });
    const items = within(history).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Courier assigned');
    expect(items[1]).toHaveTextContent('Order accepted');
    expect(screen.getByRole('heading', { name: TRACKING })).toBeInTheDocument();
    expect(calls[0]?.headers['X-API-Key']).toBeUndefined();
  });

  it('says when the parcel does not exist', async () => {
    mockFetch([{ path: `/api/tracking/${TRACKING}`, status: 404, body: { title: 'Not Found', status: 404, detail: 'Unknown' } }]);
    renderApp(`/track/${TRACKING}`, { signedIn: false });
    expect(await screen.findByRole('heading', { name: 'Parcel not found' })).toBeInTheDocument();
  });

  it('says when the tracking service is unavailable', async () => {
    mockFetch([{ path: `/api/tracking/${TRACKING}`, status: 503, body: { title: 'Service Unavailable', status: 503, detail: '' } }]);
    renderApp(`/track/${TRACKING}`, { signedIn: false });
    expect(await screen.findByRole('heading', { name: 'Cannot load tracking' })).toBeInTheDocument();
  });

  it('shows an invalid-number page for a bad address', () => {
    mockFetch([]);
    renderApp('/track/xx', { signedIn: false });
    expect(screen.getByRole('heading', { name: 'Invalid tracking number' })).toBeInTheDocument();
  });

  it('shows a not-found page for an unknown route', () => {
    mockFetch([]);
    renderApp('/nowhere', { signedIn: false });
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});

describe('sign in', () => {
  it('sends an anonymous visitor from the console to the sign-in page', () => {
    mockFetch([]);
    renderApp('/console/orders', { signedIn: false });
    expect(screen.getByRole('heading', { name: 'Staff sign in' })).toBeInTheDocument();
  });

  it('does not store a key the gateway refuses', async () => {
    mockFetch([
      { path: '/api/warehouse/products?size=1', status: 401, body: { title: 'Unauthorized', status: 401, detail: 'A valid X-API-Key header is required' } },
    ]);
    renderApp('/login', { signedIn: false });
    await userEvent.type(screen.getByLabelText('API key'), 'wrong-key-0123456789');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('This API key was not accepted.')).toBeInTheDocument();
    expect(getApiKey()).toBeNull();
  });

  it('asks for a key before calling the server', async () => {
    const { calls } = mockFetch([]);
    renderApp('/login', { signedIn: false });
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter the API key.')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('stores an accepted key and opens the page that was asked for', async () => {
    const { calls } = mockFetch([
      { path: '/api/warehouse/products?size=1', body: [product()] },
      { path: /^\/api\/orders/, body: [order()] },
    ]);
    renderApp('/console/orders', { signedIn: false });
    await userEvent.type(screen.getByLabelText('API key'), TEST_KEY);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { name: 'Orders' })).toBeInTheDocument();
    expect(getApiKey()).toBe(TEST_KEY);
    expect(calls[0]?.headers['X-API-Key']).toBe(TEST_KEY);
  });

  it('signs out and returns to the sign-in page', async () => {
    mockFetch([{ path: /^\/api\//, body: [] }]);
    renderApp('/console');
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Staff sign in' })).toBeInTheDocument());
    expect(getApiKey()).toBeNull();
  });

  it('drops the key and asks again when the gateway answers 401', async () => {
    mockFetch([{ path: /^\/api\/orders/, status: 401, body: { title: 'Unauthorized', status: 401, detail: '' } }]);
    renderApp('/console/orders');
    expect(await screen.findByRole('heading', { name: 'Staff sign in' })).toBeInTheDocument();
    expect(getApiKey()).toBeNull();
  });
});

describe('dashboard', () => {
  it('shows order counts, low stock and courier load', async () => {
    mockFetch([
      { path: /^\/api\/orders/, body: [order({ status: 'CONFIRMED' }), order({ id: 'b', status: 'CONFIRMED' }), order({ id: 'c', status: 'DELIVERED' })] },
      { path: /^\/api\/warehouse\/products/, body: [product({ sku: 'TAPE-1', available: 2 }), product({ id: 'x', sku: 'BOX-M', available: 90 })] },
      { path: /^\/api\/delivery\/couriers/, body: [{ id: 'k', name: 'Bekzod', phone: '+998901112233', status: 'AVAILABLE', maxActiveDeliveries: 5, activeDeliveries: 2, createdAt: '' }] },
      { path: /^\/api\/delivery\/deliveries/, body: [delivery()] },
    ]);
    renderApp('/console');
    const confirmed = await screen.findByRole('link', { name: /Confirmed/ });
    expect(confirmed).toHaveTextContent('2');
    expect(await screen.findByText('TAPE-1')).toBeInTheDocument();
    expect(screen.queryByText('2 available')).toBeInTheDocument();
    expect(await screen.findByText('2 of 5 slots')).toBeInTheDocument();
  });
});
