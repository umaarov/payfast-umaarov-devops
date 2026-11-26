export function normalizeTrackingNumber(value: string): string {
  return value.trim().toUpperCase();
}

export function isValidTrackingNumber(value: string): boolean {
  return /^[A-Z0-9]{8,32}$/.test(value);
}

export function isValidPhone(value: string): boolean {
  return /^\+?[0-9]{9,15}$/.test(value);
}

export function isValidSku(value: string): boolean {
  return /^[A-Za-z0-9._-]{1,64}$/.test(value);
}

export function parseWholeNumber(value: string): number | null {
  if (!/^\d+$/.test(value.trim())) {
    return null;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}
