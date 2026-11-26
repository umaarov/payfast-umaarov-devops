import type { DeliveryStatus } from '../api/types';

const NEXT: Record<DeliveryStatus, DeliveryStatus[]> = {
  ASSIGNED: ['PICKED_UP'],
  PICKED_UP: ['IN_TRANSIT'],
  IN_TRANSIT: ['DELIVERED', 'FAILED'],
  FAILED: ['IN_TRANSIT'],
  DELIVERED: [],
  CANCELLED: [],
};

export function nextDeliveryStatuses(status: DeliveryStatus): DeliveryStatus[] {
  return NEXT[status];
}

export function needsNote(status: DeliveryStatus): boolean {
  return status === 'FAILED';
}

export function isFinalDelivery(status: DeliveryStatus): boolean {
  return NEXT[status].length === 0;
}
