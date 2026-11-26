export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'IN_DELIVERY' | 'DELIVERED' | 'CANCELLED' | 'REJECTED';

export const ORDER_STATUSES: OrderStatus[] = [
  'PENDING',
  'CONFIRMED',
  'IN_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'REJECTED',
];

export type DeliveryStatus = 'ASSIGNED' | 'PICKED_UP' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

export const DELIVERY_STATUSES: DeliveryStatus[] = [
  'ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'DELIVERED',
  'FAILED',
  'CANCELLED',
];

export type CourierStatus = 'AVAILABLE' | 'OFF_DUTY';

export type TrackingStatus =
  | 'CREATED'
  | 'COURIER_ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'DELIVERY_FAILED'
  | 'CANCELLED';

export interface OrderItem {
  sku: string;
  quantity: number;
}

export interface Order {
  id: string;
  trackingNumber: string;
  status: OrderStatus;
  deliveryStatus: string | null;
  rejectionReason: string | null;
  customerName: string;
  customerPhone: string;
  city: string;
  addressLine: string;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PlaceOrderPayload {
  customerName: string;
  customerPhone: string;
  city: string;
  addressLine: string;
  items: OrderItem[];
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  quantityOnHand: number;
  quantityReserved: number;
  available: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductPayload {
  sku: string;
  name: string;
  initialQuantity: number;
}

export interface Courier {
  id: string;
  name: string;
  phone: string;
  status: CourierStatus;
  maxActiveDeliveries: number;
  activeDeliveries: number;
  createdAt: string;
}

export interface CreateCourierPayload {
  name: string;
  phone: string;
  maxActiveDeliveries: number;
}

export interface CourierSummary {
  id: string;
  name: string;
  phone: string;
}

export interface Delivery {
  id: string;
  orderId: string;
  trackingNumber: string;
  status: DeliveryStatus;
  courier: CourierSummary | null;
  recipientName: string;
  recipientPhone: string;
  city: string;
  addressLine: string;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TrackingEvent {
  status: TrackingStatus;
  description: string | null;
  location: string | null;
  occurredAt: string;
}

export interface Timeline {
  trackingNumber: string;
  status: TrackingStatus;
  updatedAt: string;
  events: TrackingEvent[];
}

export interface PageParams {
  page?: number;
  size?: number;
}
