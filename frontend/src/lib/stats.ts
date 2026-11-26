import type { Courier, Order, OrderStatus, Product } from '../api/types';

export function countOrdersByStatus(orders: Order[]): Record<OrderStatus, number> {
  const counts: Record<OrderStatus, number> = {
    PENDING: 0,
    CONFIRMED: 0,
    IN_DELIVERY: 0,
    DELIVERED: 0,
    CANCELLED: 0,
    REJECTED: 0,
  };
  for (const order of orders) {
    counts[order.status] += 1;
  }
  return counts;
}

export function lowStock(products: Product[], threshold: number): Product[] {
  return products.filter((product) => product.available <= threshold).sort((a, b) => a.available - b.available);
}

export interface CourierLoad {
  available: number;
  offDuty: number;
  active: number;
  capacity: number;
}

export function summarizeCouriers(couriers: Courier[]): CourierLoad {
  const load: CourierLoad = { available: 0, offDuty: 0, active: 0, capacity: 0 };
  for (const courier of couriers) {
    if (courier.status === 'AVAILABLE') {
      load.available += 1;
      load.capacity += courier.maxActiveDeliveries;
    } else {
      load.offDuty += 1;
    }
    load.active += courier.activeDeliveries;
  }
  return load;
}
