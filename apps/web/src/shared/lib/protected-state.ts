import { queryClient } from './api';

/**
 * Account-scoped browser state: cached API responses and anything a feature keeps
 * in memory or storage for the signed-in account. It is discarded on sign-out and
 * whenever the server reports a different (or no) account than the one that wrote it,
 * including after a reload in another account.
 */
const OWNER_KEY = 'theparlor.session-owner';
/**
 * Storage keys with this prefix belong to the current account. They are removed
 * even when the lazily loaded feature that wrote them has not loaded yet.
 */
export const ACCOUNT_STORAGE_PREFIX = 'theparlor.account:';
// Written under an unscoped key before the prefix existed.
const LEGACY_ACCOUNT_KEYS = ['theparlor_customer_reservations_v3'];
const resets = new Set<() => void>();

function removeAccountStorage(): void {
  try {
    const keys: string[] = [];
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index);
      if (key?.startsWith(ACCOUNT_STORAGE_PREFIX)) keys.push(key);
    }
    for (const key of [...keys, ...LEGACY_ACCOUNT_KEYS])
      localStorage.removeItem(key);
  } catch {
    /* Storage is unavailable, so nothing was persisted. */
  }
}

/** Loaded features register a reset for in-memory state tied to the account. */
export function registerProtectedState(reset: () => void): void {
  resets.add(reset);
}

export function clearProtectedState(): void {
  // Public catalog reads are the same for everyone and stay cached.
  queryClient.removeQueries({
    predicate: (query) => query.meta?.public !== true,
  });
  removeAccountStorage();
  for (const reset of resets) {
    try {
      reset();
    } catch {
      /* One feature's storage failure must not keep the others' data. */
    }
  }
}

/** Clears protected state when `subject` differs from the account that last held it. */
export function claimProtectedState(subject: string | null): void {
  const owner = subject ?? '';
  let previous: string | null = null;
  try {
    previous = localStorage.getItem(OWNER_KEY);
  } catch {
    /* Without storage nothing persists across loads; clear in memory below. */
  }
  if (previous === owner) return;
  clearProtectedState();
  try {
    localStorage.setItem(OWNER_KEY, owner);
  } catch {
    /* Nothing was persisted, so nothing can leak to the next account. */
  }
}
