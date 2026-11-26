const STORAGE_KEY = 'logistics.apiKey';

const listeners = new Set<() => void>();
let memoryKey: string | null = null;

function notify(): void {
  listeners.forEach((listener) => listener());
}

export function getApiKey(): string | null {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY) ?? memoryKey;
  } catch {
    return memoryKey;
  }
}

export function setApiKey(key: string): void {
  memoryKey = key;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, key);
  } catch {
    memoryKey = key;
  }
  notify();
}

export function clearApiKey(): void {
  memoryKey = null;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    memoryKey = null;
  }
  notify();
}

export function subscribeToApiKey(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
