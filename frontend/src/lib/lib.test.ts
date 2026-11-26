import { describe, expect, it } from 'vitest';
import { courier, order, product } from '../test/utils';
import { isFinalDelivery, needsNote, nextDeliveryStatuses } from './deliveryFlow';
import { formatDateTime, humanize, newIdempotencyKey, shortId } from './format';
import { countOrdersByStatus, lowStock, summarizeCouriers } from './stats';
import {
  isValidPhone,
  isValidSku,
  isValidTrackingNumber,
  normalizeTrackingNumber,
  parseWholeNumber,
} from './validation';

describe('validation', () => {
  it('normalizes and validates tracking numbers', () => {
    expect(normalizeTrackingNumber('  lgk4m9x2pq7a ')).toBe('LGK4M9X2PQ7A');
    expect(isValidTrackingNumber('LGK4M9X2PQ7A')).toBe(true);
    expect(isValidTrackingNumber('SHORT')).toBe(false);
    expect(isValidTrackingNumber('LGK4M9X2PQ7A-1')).toBe(false);
    expect(isValidTrackingNumber('A'.repeat(33))).toBe(false);
  });

  it('validates phones', () => {
    expect(isValidPhone('+998901234567')).toBe(true);
    expect(isValidPhone('998901234567')).toBe(true);
    expect(isValidPhone('12345')).toBe(false);
    expect(isValidPhone('+99890abc4567')).toBe(false);
  });

  it('validates skus', () => {
    expect(isValidSku('BOX-M_1.2')).toBe(true);
    expect(isValidSku('')).toBe(false);
    expect(isValidSku('has space')).toBe(false);
    expect(isValidSku('a'.repeat(65))).toBe(false);
  });

  it('parses whole numbers only', () => {
    expect(parseWholeNumber('12')).toBe(12);
    expect(parseWholeNumber(' 7 ')).toBe(7);
    expect(parseWholeNumber('1.5')).toBeNull();
    expect(parseWholeNumber('-3')).toBeNull();
    expect(parseWholeNumber('')).toBeNull();
    expect(parseWholeNumber('99999999999999999999')).toBeNull();
  });
});

describe('delivery flow', () => {
  it('offers only the allowed next steps', () => {
    expect(nextDeliveryStatuses('ASSIGNED')).toEqual(['PICKED_UP']);
    expect(nextDeliveryStatuses('PICKED_UP')).toEqual(['IN_TRANSIT']);
    expect(nextDeliveryStatuses('IN_TRANSIT')).toEqual(['DELIVERED', 'FAILED']);
    expect(nextDeliveryStatuses('FAILED')).toEqual(['IN_TRANSIT']);
    expect(nextDeliveryStatuses('DELIVERED')).toEqual([]);
    expect(nextDeliveryStatuses('CANCELLED')).toEqual([]);
  });

  it('knows which steps are final and which need a note', () => {
    expect(isFinalDelivery('DELIVERED')).toBe(true);
    expect(isFinalDelivery('FAILED')).toBe(false);
    expect(needsNote('FAILED')).toBe(true);
    expect(needsNote('DELIVERED')).toBe(false);
  });
});

describe('format', () => {
  it('humanizes status names', () => {
    expect(humanize('IN_DELIVERY')).toBe('In delivery');
    expect(humanize(null)).toBe('-');
  });

  it('formats dates and survives bad input', () => {
    expect(formatDateTime('2026-11-25T08:00:00Z')).toMatch(/2026/);
    expect(formatDateTime('not a date')).toBe('-');
    expect(formatDateTime(null)).toBe('-');
  });

  it('shortens ids and creates distinct idempotency keys', () => {
    expect(shortId('0b3c9a0e-8c62')).toBe('0b3c9a0e');
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey());
    expect(newIdempotencyKey().length).toBeLessThanOrEqual(64);
  });
});

describe('stats', () => {
  it('counts orders by status', () => {
    const counts = countOrdersByStatus([order({ status: 'CONFIRMED' }), order({ status: 'CONFIRMED' }), order({ status: 'REJECTED' })]);
    expect(counts.CONFIRMED).toBe(2);
    expect(counts.REJECTED).toBe(1);
    expect(counts.DELIVERED).toBe(0);
  });

  it('lists low stock, lowest first', () => {
    const rows = lowStock([product({ sku: 'A', available: 4 }), product({ sku: 'B', available: 50 }), product({ sku: 'C', available: 0 })], 5);
    expect(rows.map((row) => row.sku)).toEqual(['C', 'A']);
  });

  it('summarizes courier load and counts capacity of available couriers only', () => {
    const load = summarizeCouriers([
      courier({ status: 'AVAILABLE', maxActiveDeliveries: 5, activeDeliveries: 2 }),
      courier({ status: 'OFF_DUTY', maxActiveDeliveries: 5, activeDeliveries: 1 }),
    ]);
    expect(load).toEqual({ available: 1, offDuty: 1, active: 3, capacity: 5 });
  });
});
