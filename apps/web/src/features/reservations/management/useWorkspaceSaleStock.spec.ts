import { defineComponent, ref } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BranchGame, Order, RentalCopy } from '@theparlor/api-client';
import {
  toFulfillmentRow,
  toRentalCopyRow,
  toRetailStockRows,
} from '../components/management-inventory.samples';
import { toCatalogGame } from './useWorkspaceGames';
import { useWorkspaceSaleStock } from './useWorkspaceSaleStock';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const BRANCH_ID = '00000000-0000-4000-8000-000000000001';
const EDITION_ID = 'a1b2c3d4-0000-4000-8000-000000000011';
const GAME_ID = '00000000-0000-4000-8000-000000000021';

const offering = (saleOnHand: number, saleHeld = 1): BranchGame =>
  ({
    branchId: BRANCH_ID,
    game: {
      id: GAME_ID,
      name: 'Azul',
      categories: ['Abstract & Strategy'],
      mechanics: ['Tile placement'],
      minPlayers: 2,
      maxPlayers: 4,
      playMinutesMin: 30,
      playMinutesMax: 45,
      active: true,
    },
    saleEnabled: true,
    rentalEnabled: true,
    staffCanTeach: true,
    editions: [
      {
        editionId: EDITION_ID,
        editionKind: 'BASE_GAME',
        languageCode: 'en',
        active: true,
        salePrice: { amountMinor: 149000 },
        saleOnHand,
        saleHeld,
        saleAvailable: saleOnHand - saleHeld,
      },
    ],
    rentalTotal: 1,
    rentalUsable: 1,
    rentalAvailableNow: 1,
  }) as unknown as BranchGame;

let client: QueryClient;
let mounted: { unmount(): void }[] = [];

function stock(branchId = '') {
  let result!: ReturnType<typeof useWorkspaceSaleStock>;
  const wrapper = mount(
    defineComponent({
      setup() {
        result = useWorkspaceSaleStock(ref(branchId));
        return () => null;
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } },
  );
  mounted.push(wrapper);
  return result;
}

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  request.mockReset();
});
afterEach(() => {
  for (const wrapper of mounted) wrapper.unmount();
  mounted = [];
  client.clear();
});

describe('sample sale stock', () => {
  it('receives a batch and preserves reservations', async () => {
    const inventory = stock();
    expect(
      await inventory.receive([
        { id: 'retail:AZUL-EN-B', quantity: 3 },
        { id: 'retail:PAND-EN-B', quantity: 6 },
      ]),
    ).toBe(true);
    expect(
      inventory.items.value.find((item) => item.sku === 'AZUL-EN-B'),
    ).toMatchObject({ onHand: 15, reserved: 5, available: 10 });
    expect(
      inventory.items.value.find((item) => item.sku === 'PAND-EN-B'),
    ).toMatchObject({ onHand: 14, reserved: 1, available: 13 });
  });

  it('rejects counts below reservations and invalid batches before changing stock', async () => {
    const inventory = stock();
    for (const quantity of [4, -1, 5.5, Number.NaN])
      expect(await inventory.setOnHand('retail:AZUL-EN-B', quantity)).toBe(
        false,
      );
    expect(await inventory.setOnHand('retail:AZUL-EN-B', 5)).toBe(true);
    expect(
      inventory.items.value.find((item) => item.sku === 'AZUL-EN-B'),
    ).toMatchObject({ onHand: 5, available: 0, status: 'Fully reserved' });
    for (const quantity of [0, -1, 1.5, Number.MAX_SAFE_INTEGER])
      expect(
        await inventory.receive([
          { id: 'retail:PAND-EN-B', quantity: 2 },
          { id: 'retail:CATAN-TH-B', quantity },
        ]),
      ).toBe(false);
    expect(
      inventory.items.value.find((item) => item.sku === 'PAND-EN-B')?.onHand,
    ).toBe(8);
  });

  it('keeps balances inside their workspace', async () => {
    const first = stock();
    const second = stock();
    await first.setOnHand('retail:PAND-EN-B', 20);
    expect(
      second.items.value.find((item) => item.sku === 'PAND-EN-B')?.onHand,
    ).toBe(8);
  });
});

describe('live sale stock', () => {
  it('lists branch editions without invented thresholds and saves receipts on the server', async () => {
    let onHand = 4;
    request.mockImplementation(
      (config: { url: string; method?: string; data?: { delta: number } }) => {
        if (config.method === 'POST') {
          onHand += config.data!.delta;
          return Promise.resolve({ saleOnHand: onHand });
        }
        return Promise.resolve({ items: [offering(onHand)] });
      },
    );
    const inventory = stock(BRANCH_ID);
    await flushPromises();
    const [row] = inventory.items.value;
    expect(inventory.isLive.value).toBe(true);
    expect(row).toMatchObject({
      id: `retail:${EDITION_ID}`,
      sku: 'A1B2C3D4',
      name: 'Azul',
      description: 'Base game · English',
      onHand: 4,
      reserved: 1,
      reorderPoint: null,
      status: 'In stock',
    });

    expect(await inventory.receive([{ id: row.id, quantity: 3 }])).toBe(true);
    await flushPromises();
    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: `/api/v1/branches/${BRANCH_ID}/editions/${EDITION_ID}/stock-adjustments`,
        method: 'POST',
        data: { delta: 3, reason: 'Stock receipt' },
      }),
      undefined,
    );
    expect(inventory.items.value[0].onHand).toBe(7);
  });

  it('shows the server balance and an error when an adjustment fails', async () => {
    request.mockImplementation((config: { method?: string }) =>
      config.method === 'POST'
        ? Promise.reject(new Error('Forbidden'))
        : Promise.resolve({ items: [offering(4)] }),
    );
    const inventory = stock(BRANCH_ID);
    await flushPromises();
    const [row] = inventory.items.value;
    expect(await inventory.setOnHand(row.id, 9)).toBe(false);
    await flushPromises();
    expect(inventory.error.value).toBe(
      'Stock could not be updated. Refresh and try again.',
    );
    expect(inventory.items.value[0].onHand).toBe(4);
  });
});

describe('live record mapping', () => {
  it('describes copies, orders and games only with server facts', () => {
    expect(toRetailStockRows([offering(2, 2)])[0]).toMatchObject({
      editionId: EDITION_ID,
      pickupHeld: 2,
      shippingHeld: 0,
      reorderAt: null,
    });
    expect(
      toRentalCopyRow(
        {
          id: 'copy-1',
          branchId: BRANCH_ID,
          gameId: GAME_ID,
          assetCode: 'AZ-001',
          operationalStatus: 'OUT_OF_SERVICE',
          state: 'OUT_OF_SERVICE',
          reason: 'Missing tiles',
        } as RentalCopy,
        'Azul',
      ),
    ).toMatchObject({
      assetCode: 'AZ-001',
      state: 'Maintenance',
      location: 'Missing tiles',
    });
    expect(
      toRentalCopyRow(
        {
          id: 'copy-2',
          branchId: BRANCH_ID,
          gameId: GAME_ID,
          assetCode: 'AZ-002',
          operationalStatus: 'USABLE',
          state: 'RESERVED',
        } as RentalCopy,
        'Azul',
      ),
    ).toMatchObject({ state: 'At table', location: 'Held for a reservation' });
    expect(
      toFulfillmentRow({
        id: 'abcdef12-0000-4000-8000-000000000031',
        branchId: BRANCH_ID,
        source: 'CUSTOMER',
        status: 'PLACED',
        fulfillmentMethod: 'SHIPPING',
        items: [
          { editionId: EDITION_ID, gameName: 'Azul', quantity: 2 },
          { editionId: EDITION_ID, gameName: 'Catan', quantity: 1 },
        ],
        payment: {
          method: 'PROMPTPAY',
          status: 'PENDING',
          amount: { amountMinor: 1000 },
        },
      } as unknown as Order),
    ).toMatchObject({
      reference: 'ABCDEF12',
      channel: 'shipping',
      itemName: 'Azul and 1 more',
      quantity: 3,
      state: 'Awaiting dispatch',
      payment: 'PromptPay · Awaiting payment',
      awaitingPromptPay: true,
      awaitingCash: false,
    });
    expect(
      toFulfillmentRow({
        id: 'abcdef12-0000-4000-8000-000000000032',
        branchId: BRANCH_ID,
        source: 'CUSTOMER',
        status: 'FULFILLED',
        fulfillmentMethod: 'SHIPPING',
        items: [{ editionId: EDITION_ID, gameName: 'Azul', quantity: 1 }],
        payment: {
          method: 'COD',
          status: 'PENDING',
          amount: { amountMinor: 1000 },
        },
      } as unknown as Order),
    ).toMatchObject({
      payment: 'Cash on delivery · Awaiting cash',
      awaitingPromptPay: false,
      awaitingCash: true,
    });
    expect(toCatalogGame(offering(1))).toMatchObject({
      id: GAME_ID,
      difficulty: null,
      language: 'English',
      retailPrice: 1490,
    });
  });
});
