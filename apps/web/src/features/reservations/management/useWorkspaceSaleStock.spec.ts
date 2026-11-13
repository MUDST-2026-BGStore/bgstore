import { ref } from 'vue';
import { describe, expect, it } from 'vitest';
import { createPantrySamples } from './pantry.samples';
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
});
