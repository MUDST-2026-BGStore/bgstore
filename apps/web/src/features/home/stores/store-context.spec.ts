import { beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { queryClient } from '../../../shared/lib/api';
import { STORE_CONTEXT_KEY, useStoreContextStore } from './store-context';
import { useBranchDirectoryStore } from './branch-directory';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const ARI = '00000000-0000-4000-8000-0000000000a2';

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
        phone: '02-000-0000',
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
    ],
  });
});

it('shows a branch’s own stock, and the delivery branch’s stock when shopping online', async () => {
  const context = useStoreContextStore();
  await useBranchDirectoryStore().load();
  context.select(ARI);
  expect(context.branch?.name).toBe('Ari');
  expect(context.stockBranch?.id).toBe(ARI);
  context.select('online');
  expect(context.branch).toBeUndefined();
  expect(context.stockBranch?.id).toBe(SIAM);
  context.select('browse');
  expect(context.stockBranch).toBeUndefined();
  expect(localStorage.getItem(STORE_CONTEXT_KEY)).toBe('browse');
});

it('keeps a saved branch until the directory loads, then forgets one that no longer exists', async () => {
  localStorage.setItem(STORE_CONTEXT_KEY, ARI);
  const context = useStoreContextStore();
  expect(context.choice).toBe(ARI);
  expect(context.branch).toBeUndefined();
  await useBranchDirectoryStore().load();
  expect(context.branch?.name).toBe('Ari');
  context.select('missing');
  expect(context.choice).toBe(ARI);

  localStorage.setItem(STORE_CONTEXT_KEY, 'siam');
  queryClient.clear();
  setActivePinia(createPinia());
  const stale = useStoreContextStore();
  await useBranchDirectoryStore().load();
  expect(stale.choice).toBe('');
});

it('maps seeded branches to approximate positions and leaves unknown ones unpinned', async () => {
  request.mockResolvedValue({
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
        name: 'New Branch',
        address: 'B',
        openingHours: [],
        active: true,
        fulfillsDelivery: false,
      },
    ],
  });
  const directory = useBranchDirectoryStore();
  await directory.load();
  expect(directory.byId(SIAM)?.location).toEqual({
    lat: 13.7449,
    lng: 100.5338,
  });
  expect(directory.byId(ARI)?.location).toBeUndefined();
  expect(directory.deliveryBranch?.id).toBe(SIAM);
});
