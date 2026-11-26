import { describe, expect, it, vi } from 'vitest';
import { clearApiKey, getApiKey, setApiKey } from '../auth/keyStore';
import { mockFetch } from '../test/utils';
import { ApiError, query, request } from './client';
import { getTimeline, listOrders, placeOrder, updateDeliveryStatus } from './endpoints';

describe('request', () => {
  it('sends the stored API key', async () => {
    setApiKey('stored-key-0123456789');
    const { calls } = mockFetch([{ path: '/api/orders', body: [] }]);
    await listOrders();
    expect(calls[0]?.headers['X-API-Key']).toBe('stored-key-0123456789');
    expect(calls[0]?.headers.Accept).toBe('application/json');
  });

  it('does not send the key to public endpoints', async () => {
    setApiKey('stored-key-0123456789');
    const { calls } = mockFetch([
      { path: '/api/tracking/LGK4M9X2PQ7A', body: { trackingNumber: 'LGK4M9X2PQ7A', status: 'CREATED', updatedAt: '', events: [] } },
    ]);
    await getTimeline('LGK4M9X2PQ7A');
    expect(calls[0]?.headers['X-API-Key']).toBeUndefined();
  });

  it('sends JSON bodies and the idempotency key', async () => {
    setApiKey('stored-key-0123456789');
    const { calls } = mockFetch([{ method: 'POST', path: '/api/orders', status: 201, body: {} }]);
    await placeOrder(
      { customerName: 'A', customerPhone: '+998901234567', city: 'T', addressLine: 'S', items: [{ sku: 'X', quantity: 1 }] },
      'key-1',
    );
    expect(calls[0]?.headers['Idempotency-Key']).toBe('key-1');
    expect(calls[0]?.headers['Content-Type']).toBe('application/json');
    expect(calls[0]?.body).toMatchObject({ items: [{ sku: 'X', quantity: 1 }] });
  });

  it('turns a problem response into an ApiError', async () => {
    mockFetch([
      {
        method: 'POST',
        path: '/api/orders',
        status: 409,
        body: {
          title: 'Order rejected',
          status: 409,
          detail: 'Insufficient stock for sku BOX-M',
          reason: 'INSUFFICIENT_STOCK',
          orderId: 'abc',
          errors: ['items: must not be empty'],
        },
      },
    ]);
    const failure = await request('/api/orders', { method: 'POST', body: {} }).catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ApiError);
    const error = failure as ApiError;
    expect(error.status).toBe(409);
    expect(error.detail).toBe('Insufficient stock for sku BOX-M');
    expect(error.reason).toBe('INSUFFICIENT_STOCK');
    expect(error.orderId).toBe('abc');
    expect(error.errors).toEqual(['items: must not be empty']);
  });

  it('handles an error response without a JSON body', async () => {
    mockFetch([{ path: '/api/orders', status: 502 }]);
    const error = (await request('/api/orders').catch((failure: unknown) => failure)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
  });

  it('reports a network failure as status 0', async () => {
    vi.stubGlobal('fetch', () => Promise.reject(new TypeError('Failed to fetch')));
    const error = (await request('/api/orders').catch((failure: unknown) => failure)) as ApiError;
    expect(error.status).toBe(0);
    expect(error.detail).toMatch(/cannot be reached/);
  });

  it('forgets the key after a 401 on an authenticated call', async () => {
    setApiKey('revoked-key-0123456789');
    mockFetch([{ path: '/api/orders', status: 401, body: { title: 'Unauthorized', status: 401, detail: 'A valid X-API-Key header is required' } }]);
    await expect(request('/api/orders')).rejects.toBeInstanceOf(ApiError);
    expect(getApiKey()).toBeNull();
  });

  it('keeps the stored key when a candidate key is rejected', async () => {
    setApiKey('good-key-0123456789');
    mockFetch([{ path: '/api/warehouse/products', status: 401, body: { title: 'Unauthorized', status: 401, detail: '' } }]);
    await expect(request('/api/warehouse/products', { apiKey: 'candidate' })).rejects.toBeInstanceOf(ApiError);
    expect(getApiKey()).toBe('good-key-0123456789');
    clearApiKey();
  });

  it('returns undefined for 204', async () => {
    mockFetch([{ method: 'POST', path: '/api/x', status: 204 }]);
    await expect(request('/api/x', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('sends a note only when one is given', async () => {
    setApiKey('stored-key-0123456789');
    const { calls } = mockFetch([{ method: 'POST', path: /status$/, body: {} }]);
    await updateDeliveryStatus('d1', 'PICKED_UP');
    await updateDeliveryStatus('d1', 'FAILED', 'Not at home');
    expect(calls[0]?.body).toEqual({ status: 'PICKED_UP' });
    expect(calls[1]?.body).toEqual({ status: 'FAILED', note: 'Not at home' });
  });
});

describe('query', () => {
  it('skips empty values', () => {
    expect(query({ status: '', page: 0, size: 20, courierId: undefined })).toBe('?page=0&size=20');
    expect(query({})).toBe('');
  });

  it('encodes values', () => {
    expect(query({ status: 'A B' })).toBe('?status=A+B');
  });
});
