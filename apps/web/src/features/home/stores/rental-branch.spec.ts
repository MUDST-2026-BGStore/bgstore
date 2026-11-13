import { beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { queryClient } from '../../../shared/lib/api';
import { useBranchDirectoryStore } from './branch-directory';
import { RENTAL_BRANCH_KEY, useRentalBranchStore } from './rental-branch';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const ARI = '00000000-0000-4000-8000-0000000000a2';
const RIVERSIDE = '00000000-0000-4000-8000-0000000000a3';

beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
  setActivePinia(createPinia());
  request.mockReset().mockResolvedValue({
    items: [
      {
        id: SIAM,
        name: 'Siam',
        address: 'A',
        openingHours: [],
        active: true,
        fulfillsDelivery: true,
      },
      {
        id: ARI,
        name: 'Ari',
        address: 'B',
        openingHours: [],
        active: true,
        fulfillsDelivery: false,
      },
      {
        id: RIVERSIDE,
        name: 'Riverside',
        address: 'C',
        openingHours: [],
        active: true,
        fulfillsDelivery: false,
      },
    ],
  });
});

it('remembers a valid rental branch from the live directory and forgets stale preferences', async () => {
  await useBranchDirectoryStore().load();
  const rental = useRentalBranchStore();
  expect(rental.branch).toBeUndefined();
  rental.select(ARI);
  rental.select('unknown');
  expect(rental.branchId).toBe(ARI);
  expect(rental.branch?.name).toBe('Ari');
  rental.$dispose();

  setActivePinia(createPinia());
  await useBranchDirectoryStore().load();
  expect(useRentalBranchStore().branchId).toBe(ARI);
  expect(useRentalBranchStore().branch?.name).toBe('Ari');
  useRentalBranchStore().$dispose();

  localStorage.setItem(RENTAL_BRANCH_KEY, 'deleted');
  setActivePinia(createPinia());
  await useBranchDirectoryStore().load();
  expect(useRentalBranchStore().branchId).toBe('');
  expect(useRentalBranchStore().branch).toBeUndefined();
});

it('keeps the visiting preference in its own storage key, apart from the cart', async () => {
  await useBranchDirectoryStore().load();
  const rental = useRentalBranchStore();
  rental.select(RIVERSIDE);
  expect(localStorage.getItem(RENTAL_BRANCH_KEY)).toBe(RIVERSIDE);
  expect(Object.keys(localStorage)).toEqual([RENTAL_BRANCH_KEY]);
});
