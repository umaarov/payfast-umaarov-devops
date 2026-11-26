import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { App } from '../App';
import type { Courier, Delivery, Order, Product } from '../api/types';
import { setApiKey } from '../auth/keyStore';

export const TEST_KEY = 'test-key-0123456789';

export interface RecordedCall {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: unknown;
}

export interface MockRoute {
  method?: string;
  path: string | RegExp;
  status?: number;
  body?: unknown;
  respond?: (call: RecordedCall) => { status: number; body?: unknown };
}

function toResponse(status: number, body: unknown): Response {
  const hasBody = body !== undefined && status !== 204;
  return new Response(hasBody ? JSON.stringify(body) : null, {
    status,
    headers: hasBody ? { 'Content-Type': 'application/json' } : {},
  });
}

export function mockFetch(routes: MockRoute[]) {
  const calls: RecordedCall[] = [];
  const handler = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
    const call: RecordedCall = { method, url, headers, body };
    calls.push(call);
    const route = routes.find(
      (entry) =>
        (entry.method ?? 'GET').toUpperCase() === method &&
        (typeof entry.path === 'string' ? entry.path === url : entry.path.test(url)),
    );
    if (!route) {
      return toResponse(404, { title: 'Not Found', status: 404, detail: `No mock for ${method} ${url}` });
    }
    if (route.respond) {
      const result = route.respond(call);
      return toResponse(result.status, result.body);
    }
    return toResponse(route.status ?? 200, route.body);
  });
  vi.stubGlobal('fetch', handler);
  return { calls, handler };
}

export function renderApp(route: string, options: { signedIn?: boolean } = {}) {
  if (options.signedIn ?? true) {
    setApiKey(TEST_KEY);
  }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: '0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55',
    trackingNumber: 'LGK4M9X2PQ7A',
    status: 'CONFIRMED',
    deliveryStatus: null,
    rejectionReason: null,
    customerName: 'Aziz Karimov',
    customerPhone: '+998901234567',
    city: 'Tashkent',
    addressLine: 'Amir Temur 1',
    items: [{ sku: 'BOX-M', quantity: 2 }],
    createdAt: '2026-11-25T08:00:00Z',
    updatedAt: '2026-11-25T08:00:00Z',
    ...overrides,
  };
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: '5f0c7b0e-5a59-4a1c-9d0c-3e2d8f0f7a11',
    sku: 'BOX-M',
    name: 'Medium box',
    quantityOnHand: 100,
    quantityReserved: 10,
    available: 90,
    createdAt: '2026-11-25T08:00:00Z',
    updatedAt: '2026-11-25T08:00:00Z',
    ...overrides,
  };
}

export function courier(overrides: Partial<Courier> = {}): Courier {
  return {
    id: 'e1c5a8d2-0000-4000-8000-000000000001',
    name: 'Bekzod',
    phone: '+998901112233',
    status: 'AVAILABLE',
    maxActiveDeliveries: 5,
    activeDeliveries: 2,
    createdAt: '2026-11-25T08:00:00Z',
    ...overrides,
  };
}

export function delivery(overrides: Partial<Delivery> = {}): Delivery {
  return {
    id: '9a7e31f4-4e0b-4c3a-8a4f-1c7a9d0e55aa',
    orderId: '0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55',
    trackingNumber: 'LGK4M9X2PQ7A',
    status: 'ASSIGNED',
    courier: { id: 'e1c5a8d2-0000-4000-8000-000000000001', name: 'Bekzod', phone: '+998901112233' },
    recipientName: 'Aziz Karimov',
    recipientPhone: '+998901234567',
    city: 'Tashkent',
    addressLine: 'Amir Temur 1',
    failureReason: null,
    createdAt: '2026-11-25T08:00:00Z',
    updatedAt: '2026-11-25T08:00:00Z',
    ...overrides,
  };
}
