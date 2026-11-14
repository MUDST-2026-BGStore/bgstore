import { defineComponent } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useWorkspacePantry } from './useWorkspacePantry';

const { request, auth } = vi.hoisted(() => ({
  request: vi.fn(),
  auth: { value: { subject: 'operator' } },
}));
vi.mock('@theparlor/auth', () => ({ useAuth: () => auth }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

let client: QueryClient;
let wrapper: ReturnType<typeof mount>;
beforeEach(() => {
  localStorage.clear();
  auth.value.subject = 'operator';
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  request.mockReset();
});
afterEach(() => {
  wrapper?.unmount();
  client.clear();
  vi.restoreAllMocks();
});

function openPantry() {
  let pantry!: ReturnType<typeof useWorkspacePantry>;
  wrapper = mount(
    defineComponent({
      setup() {
        pantry = useWorkspacePantry();
        return () => null;
      },
    }),
    {
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    },
  );
  pantry.openAddSnacks({
    id: 'visit-1',
    tableId: 'table-1',
    live: true,
    branchId: 'siam',
    rentalId: 'rental-1',
  });
  return pantry;
}

it('orders live water through the server and refreshes stock and the bill', async () => {
  let available = 5;
  request.mockImplementation(
    (config: { url: string; method?: string; data?: unknown }) => {
      if (config.method === 'POST') {
        expect(config.url).toBe(
          '/api/v1/branches/siam/rentals/rental-1/refreshments',
        );
        expect(config.data).toEqual({
          items: [{ menuItemId: 'water', quantity: 2 }],
        });
        available = 3;
        return Promise.resolve({ id: 'rental-1', refreshments: [] });
      }
      return Promise.resolve({
        items: [
          {
            item: {
              id: 'water',
              sku: 'WATER',
              category: 'BEVERAGES',
              names: { 'en-US': 'Water' },
              price: { amountMinor: 2000 },
              popular: false,
            },
            available,
          },
        ],
      });
    },
  );
  let pantry!: ReturnType<typeof useWorkspacePantry>;
  wrapper = mount(
    defineComponent({
      setup() {
        pantry = useWorkspacePantry();
        return () => null;
      },
    }),
    {
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    },
  );
  pantry.openAddSnacks({
    id: 'visit-1',
    tableId: 'table-1',
    live: true,
    branchId: 'siam',
    rentalId: 'rental-1',
  });
  await flushPromises();
  expect(pantry.branchStockItems.value).toHaveLength(1);
  const water = pantry.branchStockItems.value[0];
  expect(water).toMatchObject({
    id: 'water',
    name: 'Water',
    price: 20,
    currentStock: 5,
  });
  pantry.adjustStaffSnackQty(water, 2);
  expect(pantry.staffSnackSubtotal.value).toBe(40);
  await pantry.submitStaffSnackOrder();
  expect(pantry.staffSnackReservation.value).toBeNull();
  expect(client.getQueryData(['rental', 'siam', 'rental-1'])).toMatchObject({
    id: 'rental-1',
  });
  pantry.openAddSnacks({
    id: 'visit-1',
    tableId: 'table-1',
    live: true,
    branchId: 'siam',
    rentalId: 'rental-1',
  });
  await flushPromises();
  expect(pantry.branchStockItems.value[0].currentStock).toBe(3);
});

it('keeps a rejected order open with the server error and leaves stock unchanged', async () => {
  request.mockImplementation((config: { method?: string }) => {
    if (config.method === 'POST')
      return Promise.reject({
        response: { status: 409, data: { message: 'Not enough water' } },
      });
    return Promise.resolve({
      items: [
        {
          item: {
            id: 'water',
            sku: 'WATER',
            category: 'BEVERAGES',
            names: { 'en-US': 'Water' },
            price: { amountMinor: 2000 },
          },
          available: 1,
        },
      ],
    });
  });
  const pantry = openPantry();
  await flushPromises();
  pantry.adjustStaffSnackQty(pantry.branchStockItems.value[0], 1);
  await pantry.submitStaffSnackOrder();
  expect(pantry.staffSnackReservation.value).not.toBeNull();
  expect(pantry.staffSnackError.value).toBe('Not enough water');
  expect(pantry.branchStockItems.value[0].currentStock).toBe(1);
  expect(pantry.staffSnackUncertain.value).toBe(false);
});

it('recovers an unanswered order after workspace recreation without leaking it to another operator', async () => {
  const posted: Array<{ body: unknown; key: string }> = [];
  let resolvePost!: (value: unknown) => void;
  request.mockImplementation(
    (
      config: { method?: string; data?: unknown },
      options?: { headers?: Record<string, string> },
    ) => {
      if (config.method === 'POST') {
        posted.push({
          body: config.data,
          key: options!.headers!['Idempotency-Key'],
        });
        if (posted.length === 1)
          return Promise.reject(new Error('Connection lost'));
        return new Promise((resolve) => {
          resolvePost = resolve;
        });
      }
      return Promise.resolve({
        items: [
          {
            item: {
              id: 'water',
              sku: 'WATER',
              category: 'BEVERAGES',
              names: { 'en-US': 'Water' },
              price: { amountMinor: 2000 },
            },
            available: 5,
          },
        ],
      });
    },
  );
  let pantry = openPantry();
  await flushPromises();
  pantry.adjustStaffSnackQty(pantry.branchStockItems.value[0], 2);
  await pantry.submitStaffSnackOrder();
  expect(pantry.staffSnackError.value).toContain('No confirmed answer');
  wrapper.unmount();
  auth.value.subject = 'another-operator';
  pantry = openPantry();
  expect(pantry.getStaffSnackQty('water')).toBe(0);
  wrapper.unmount();
  auth.value.subject = 'operator';
  pantry = openPantry();
  await flushPromises();
  pantry.adjustStaffSnackQty(pantry.branchStockItems.value[0], 1);
  expect(pantry.getStaffSnackQty('water')).toBe(2);
  const pending = pantry.submitStaffSnackOrder();
  await pantry.submitStaffSnackOrder();
  expect(posted).toHaveLength(2);
  expect(posted[1]).toEqual(posted[0]);
  expect(posted[0].key).toMatch(/^[0-9a-f-]{36}$/);
  resolvePost({ id: 'rental-1', refreshments: [] });
  await pending;
  expect(pantry.staffSnackReservation.value).toBeNull();
});

it('does not send an order when its retry key cannot be persisted', async () => {
  request.mockResolvedValue({
    items: [
      {
        item: {
          id: 'water',
          sku: 'WATER',
          category: 'BEVERAGES',
          names: { 'en-US': 'Water' },
          price: { amountMinor: 2000 },
        },
        available: 5,
      },
    ],
  });
  const pantry = openPantry();
  await flushPromises();
  pantry.adjustStaffSnackQty(pantry.branchStockItems.value[0], 2);
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new Error('Quota exceeded');
  });
  await pantry.submitStaffSnackOrder();
  expect(pantry.staffSnackError.value).toContain('Allow site storage');
  expect(request.mock.calls.some(([config]) => config.method === 'POST')).toBe(
    false,
  );
});

it.each([false, true])(
  'never substitutes sample menu items when the live menu is empty or unavailable (%s)',
  async (failed) => {
    request.mockImplementation(() =>
      failed
        ? Promise.reject(new Error('Offline'))
        : Promise.resolve({ items: [] }),
    );
    const pantry = openPantry();
    await flushPromises();
    expect(pantry.branchStockItems.value).toEqual([]);
    expect(pantry.staffSnackMenuError.value).toBe(failed);
  },
);
