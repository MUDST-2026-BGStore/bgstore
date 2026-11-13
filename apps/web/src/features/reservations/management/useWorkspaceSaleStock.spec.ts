import { ref } from 'vue';
import { describe, expect, it } from 'vitest';
import {
  toFulfillmentSample,
  toRentalCopySample,
  toRetailStockSamples,
} from '../components/management-inventory.samples';
import { createPantrySamples, toPantryStockItem } from './pantry.samples';
import { toCatalogGame } from './useWorkspaceGames';
import { useWorkspaceSaleStock } from './useWorkspaceSaleStock';

function stock() {
  const food = ref(createPantrySamples());
  return { food, ...useWorkspaceSaleStock(food) };
}

describe('sale stock operations', () => {
  it('receives games and food in one batch and preserves reservations', () => {
    const inventory = stock();
    expect(
      inventory.receive([
        { id: 'retail:AZUL-EN-B', quantity: 3 },
        { id: 'food:bev-coca-cola', quantity: 6 },
      ]),
    ).toBe(true);
    expect(
      inventory.items.value.find((item) => item.sku === 'AZUL-EN-B'),
    ).toMatchObject({ onHand: 15, reserved: 5, available: 10 });
    expect(
      inventory.items.value.find((item) => item.sku === 'BEV-COCA-COLA'),
    ).toMatchObject({ onHand: 54, reserved: 0, available: 54 });
    expect(inventory.food.value[0].currentStock).toBe(54);
  });

  it('rejects counts below reservations and recalculates availability at the reservation floor', () => {
    const inventory = stock();
    for (const quantity of [
      4,
      -1,
      5.5,
      Number.NaN,
      Number.POSITIVE_INFINITY,
      Number.MAX_SAFE_INTEGER + 1,
    ]) {
      expect(inventory.setOnHand('retail:AZUL-EN-B', quantity)).toBe(false);
    }
    expect(inventory.items.value[0].onHand).toBe(12);
    expect(inventory.setOnHand('retail:AZUL-EN-B', 5)).toBe(true);
    expect(inventory.items.value[0]).toMatchObject({
      onHand: 5,
      reserved: 5,
      available: 0,
      status: 'Fully reserved',
    });
    expect(inventory.setOnHand('food:bev-coca-cola', 0)).toBe(true);
    expect(
      inventory.items.value.find((item) => item.sku === 'BEV-COCA-COLA'),
    ).toMatchObject({ onHand: 0, available: 0, status: 'Out of stock' });
  });

  it('validates a complete receipt before mutating any stock', () => {
    const inventory = stock();
    const first = { id: 'retail:AZUL-EN-B', quantity: 3 };
    for (const invalid of [
      { id: 'missing', quantity: 1 },
      { id: 'food:bev-coca-cola', quantity: 0 },
      { id: 'food:bev-coca-cola', quantity: -1 },
      { id: 'food:bev-coca-cola', quantity: 1.5 },
      { id: 'food:bev-coca-cola', quantity: Number.MAX_SAFE_INTEGER },
      first,
    ]) {
      expect(inventory.receive([first, invalid])).toBe(false);
      expect(inventory.items.value[0].onHand).toBe(12);
      expect(inventory.food.value[0].currentStock).toBe(48);
    }
    expect(inventory.receive([])).toBe(false);
    expect(inventory.setOnHand('missing', 10)).toBe(false);
  });

  it('keeps game balances inside their workspace and observes pantry mutations', () => {
    const first = stock();
    const second = stock();
    first.receive([{ id: 'retail:AZUL-EN-B', quantity: 3 }]);
    expect(second.items.value[0].onHand).toBe(12);
    first.food.value[0].currentStock = 10;
    expect(
      first.items.value.find((item) => item.sku === 'BEV-COCA-COLA'),
    ).toMatchObject({ onHand: 10, available: 10, status: 'Low stock' });
  });

  it('maps live branch games, pantry menu items, rental copies and orders', () => {
    const offering = {
      branchId: 'branch-siam',
      game: {
        id: 'game-azul',
        name: 'Azul',
        designer: 'Michael Kiesling',
        difficulty: 'EASY' as const,
        categories: ['Strategy', 'Quick & Light'],
        mechanics: ['Tile placement'],
        minPlayers: 2,
        maxPlayers: 4,
        playMinutesMin: 30,
        playMinutesMax: 45,
        teachMinutes: 5,
        setupMinutes: 5,
        descriptions: { 'en-US': 'Draft tiles and score patterns.' },
        active: true,
      },
      saleEnabled: true,
      rentalEnabled: true,
      staffCanTeach: true,
      rentalHourlyRate: { amountMinor: 2000 },
      editions: [
        {
          editionId: 'ed-azul-en',
          editionKind: 'BASE_GAME' as const,
          languageCode: 'en',
          active: true,
          salePrice: { amountMinor: 149000 },
          saleOnHand: 9,
          saleHeld: 2,
          saleAvailable: 7,
        },
      ],
      rentalTotal: 2,
      rentalUsable: 2,
      rentalAvailableNow: 1,
    };

    expect(toCatalogGame(offering)).toMatchObject({
      id: 'game-azul',
      name: 'Azul',
      designer: 'Michael Kiesling',
      difficulty: 'Easy',
      players: [2, 4],
      playMinutes: [30, 45],
      retailPrice: 1490,
    });

    expect(toRetailStockSamples([offering])).toEqual([
      {
        editionId: 'ed-azul-en',
        gameId: 'game-azul',
        sku: 'AZUL-EN-B',
        name: 'Azul',
        edition: 'Base game · English',
        onHand: 9,
        shippingHeld: 0,
        pickupHeld: 2,
        reorderAt: 3,
      },
    ]);

    expect(
      toPantryStockItem({
        item: {
          id: 'menu-coke',
          sku: 'bev-coca-cola',
          category: 'BEVERAGES',
          names: {
            'en-US': 'Coca-Cola Original (Can 325ml)',
            'th-TH': 'โคคา-โคล่า รสชาติต้นตำรับ (กระป๋อง)',
          },
          price: { amountMinor: 3500 },
          popular: true,
        },
        available: 42,
      }),
    ).toMatchObject({
      id: 'bev-coca-cola',
      menuItemId: 'menu-coke',
      price: 35,
      costPrice: 18,
      unit: 'cans',
      currentStock: 42,
      lowStockThreshold: 12,
    });

    expect(
      toRentalCopySample(
        {
          id: 'copy-1',
          branchId: 'branch-siam',
          gameId: 'game-azul',
          assetCode: 'azul-siam-01',
          operationalStatus: 'USABLE',
          state: 'IN_USE',
        },
        'Azul',
        'Table 1',
      ),
    ).toEqual({
      id: 'copy-1',
      gameId: 'game-azul',
      sku: 'PZ-AZUL-RE',
      assetCode: 'azul-siam-01',
      name: 'Azul',
      state: 'At table',
      location: 'Table 1',
    });

    expect(
      toFulfillmentSample({
        id: '1042abcd-0000-0000-0000-000000000000',
        branchId: 'branch-siam',
        customer: { kind: 'GUEST', displayName: 'Guest' },
        source: 'CUSTOMER',
        status: 'PLACED',
        items: [
          {
            editionId: 'ed-azul-en',
            gameName: 'Azul',
            editionLanguage: 'en',
            quantity: 2,
            unitPrice: { amountMinor: 149000 },
            lineTotal: { amountMinor: 298000 },
          },
        ],
        total: { amountMinor: 298000 },
        contact: { name: 'Guest', phone: '0812345678' },
        fulfillmentMethod: 'SHIPPING',
        payment: {
          method: 'CARD',
          status: 'CAPTURED',
          amount: { amountMinor: 298000 },
        },
        createdAt: '2026-11-13T10:00:00Z',
        createdBySubject: 'guest',
      }),
    ).toEqual({
      orderId: '1042abcd-0000-0000-0000-000000000000',
      reference: 'PO-1042ABCD',
      channel: 'shipping',
      sku: 'AZUL-EN-B',
      itemName: 'Azul',
      quantity: 2,
      state: 'Awaiting dispatch',
    });
  });
});
