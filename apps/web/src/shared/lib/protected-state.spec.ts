import { beforeEach, expect, it, vi } from 'vitest';
import { queryClient } from './api';
import {
  ACCOUNT_STORAGE_PREFIX,
  claimProtectedState,
  clearProtectedState,
  registerProtectedState,
} from './protected-state';

const accountKey = `${ACCOUNT_STORAGE_PREFIX}walk-ins`;

beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
});

function seed() {
  localStorage.setItem(accountKey, '[]');
  localStorage.setItem('theparlor_customer_reservations_v3', '[]');
  localStorage.setItem('theparlor.locale', 'th-TH');
  queryClient.setQueryData(['listBranchReservations', 'siam'], { items: [1] });
  queryClient.setQueryData(['me'], { id: 'account-1' });
  queryClient
    .getQueryCache()
    .build(queryClient, { queryKey: ['games'], meta: { public: true } })
    .setData({ items: [] });
}

it('drops account data and keeps public data and preferences', () => {
  const reset = vi.fn();
  registerProtectedState(reset);
  seed();

  clearProtectedState();

  expect(localStorage.getItem(accountKey)).toBeNull();
  expect(localStorage.getItem('theparlor_customer_reservations_v3')).toBeNull();
  expect(localStorage.getItem('theparlor.locale')).toBe('th-TH');
  expect(queryClient.getQueryData(['listBranchReservations', 'siam'])).toBe(
    undefined,
  );
  expect(queryClient.getQueryData(['me'])).toBeUndefined();
  expect(queryClient.getQueryData(['games'])).toEqual({ items: [] });
  expect(reset).toHaveBeenCalledOnce();
});

it('keeps data for the same account and clears it for a different one', () => {
  claimProtectedState('staff-1');
  seed();

  claimProtectedState('staff-1');
  expect(localStorage.getItem(accountKey)).toBe('[]');

  claimProtectedState('client-1');
  expect(localStorage.getItem(accountKey)).toBeNull();
  expect(queryClient.getQueryData(['me'])).toBeUndefined();
});

it('clears data left by an account when the next load has no session', () => {
  claimProtectedState('staff-1');
  seed();

  claimProtectedState(null);

  expect(localStorage.getItem(accountKey)).toBeNull();
});
