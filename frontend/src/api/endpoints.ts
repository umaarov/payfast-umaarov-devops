import { query, request } from './client';
import type {
  Courier,
  CreateCourierPayload,
  CreateProductPayload,
  Delivery,
  DeliveryStatus,
  Order,
  OrderStatus,
  PageParams,
  PlaceOrderPayload,
  Product,
  Timeline,
} from './types';

export interface OrderListParams extends PageParams {
  status?: OrderStatus | '';
}

export interface DeliveryListParams extends PageParams {
  status?: DeliveryStatus | '';
  courierId?: string;
}

export function listOrders(params: OrderListParams = {}): Promise<Order[]> {
  return request<Order[]>(`/api/orders${query({ status: params.status, page: params.page, size: params.size })}`);
}

export function getOrder(id: string): Promise<Order> {
  return request<Order>(`/api/orders/${encodeURIComponent(id)}`);
}

export function placeOrder(payload: PlaceOrderPayload, idempotencyKey: string): Promise<Order> {
  return request<Order>('/api/orders', {
    method: 'POST',
    body: payload,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function cancelOrder(id: string): Promise<Order> {
  return request<Order>(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}

export function listProducts(params: PageParams = {}, apiKey?: string): Promise<Product[]> {
  return request<Product[]>(`/api/warehouse/products${query({ page: params.page, size: params.size })}`, { apiKey });
}

export function createProduct(payload: CreateProductPayload): Promise<Product> {
  return request<Product>('/api/warehouse/products', { method: 'POST', body: payload });
}

export function receiveStock(sku: string, quantity: number): Promise<Product> {
  return request<Product>(`/api/warehouse/products/${encodeURIComponent(sku)}/receipts`, {
    method: 'POST',
    body: { quantity },
  });
}

export function listCouriers(params: PageParams = {}): Promise<Courier[]> {
  return request<Courier[]>(`/api/delivery/couriers${query({ page: params.page, size: params.size })}`);
}

export function createCourier(payload: CreateCourierPayload): Promise<Courier> {
  return request<Courier>('/api/delivery/couriers', { method: 'POST', body: payload });
}

export function setCourierAvailability(id: string, available: boolean): Promise<Courier> {
  return request<Courier>(`/api/delivery/couriers/${encodeURIComponent(id)}/availability`, {
    method: 'POST',
    body: { available },
  });
}

export function listDeliveries(params: DeliveryListParams = {}): Promise<Delivery[]> {
  return request<Delivery[]>(
    `/api/delivery/deliveries${query({
      status: params.status,
      courierId: params.courierId,
      page: params.page,
      size: params.size,
    })}`,
  );
}

export function updateDeliveryStatus(id: string, status: DeliveryStatus, note?: string): Promise<Delivery> {
  return request<Delivery>(`/api/delivery/deliveries/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: note ? { status, note } : { status },
  });
}

export function getTimeline(trackingNumber: string): Promise<Timeline> {
  return request<Timeline>(`/api/tracking/${encodeURIComponent(trackingNumber)}`, { auth: false });
}
