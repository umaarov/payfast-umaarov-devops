import { useSyncExternalStore } from 'react';
import { getApiKey, subscribeToApiKey } from './keyStore';

export function useApiKey(): string | null {
  return useSyncExternalStore(subscribeToApiKey, getApiKey, () => null);
}
