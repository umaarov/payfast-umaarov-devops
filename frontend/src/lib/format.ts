const dateTime = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : dateTime.format(date);
}

export function humanize(value: string | null | undefined): string {
  if (!value) {
    return '-';
  }
  const text = value.toLowerCase().replaceAll('_', ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}

export function newIdempotencyKey(): string {
  const generator = globalThis.crypto;
  if (generator && typeof generator.randomUUID === 'function') {
    return generator.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
