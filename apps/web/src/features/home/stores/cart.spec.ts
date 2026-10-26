import { beforeEach, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  CART_STORAGE_KEY,
  FULFILLMENT_STORAGE_KEY,
  useCartStore,
} from './cart';
beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
});

it('merges identical editions while keeping different language editions separate', () => {
  const cart = useCartStore();
  expect(cart.add('azul-en')).toBe(true);
  expect(cart.add('azul-en')).toBe(true);
  expect(cart.add('azul-th')).toBe(true);
  expect(cart.lines.map((line) => [line.variantId, line.quantity])).toEqual([
    ['azul-en', 2],
    ['azul-th', 1],
  ]);
  expect(cart.count).toBe(3);
  expect(cart.subtotal).toBe(4470);
});

it('enforces retail stock independently of rental copies and rejects invalid quantities', () => {
  const cart = useCartStore();
  expect(cart.add('catan-en')).toBe(true); // Its rental copy is currently rented.
  expect(cart.add('wingspan-en')).toBe(false); // A play copy is available, retail is sold out.
  expect(cart.add('avalon-en')).toBe(false); // Rental-only title.
  expect(cart.add('azul-en', 4)).toBe(true);
  expect(cart.add('azul-en')).toBe(false);
  for (const quantity of [NaN, Infinity, -1, 0, 1.5])
    expect(cart.add('catan-en', quantity)).toBe(false);
  expect(cart.count).toBe(5);
});

it('updates quantities and totals and removes a line at zero', () => {
  const cart = useCartStore();
  cart.add('codenames-en', 2);
  expect(cart.update('codenames-en', 3)).toBe(true);
  expect(cart.subtotal).toBe(2970);
  expect(cart.update('codenames-en', 7)).toBe(false);
  expect(cart.update('codenames-en', NaN)).toBe(false);
  expect(cart.update('codenames-en', 0)).toBe(true);
  expect(cart.lines).toEqual([]);
  expect(cart.subtotal).toBe(0);
});

it('restores only recognized variants and safe quantities using current fixture prices', () => {
  localStorage.setItem(
    CART_STORAGE_KEY,
    JSON.stringify([
      { variantId: 'azul-en', quantity: 99, price: 1 },
      { variantId: 'azul-en', quantity: 2 },
      { variantId: 'wingspan-en', quantity: 1 },
      { variantId: 'unknown', quantity: 2 },
      { variantId: 'codenames-en', quantity: 0 },
      { variantId: 'catan-en', quantity: 1.5 },
      null,
    ]),
  );
  const cart = useCartStore();
  expect(cart.items).toEqual([
    { variantId: 'azul-en', quantity: 99 },
    { variantId: 'wingspan-en', quantity: 1 },
  ]);
  expect(cart.subtotal).toBe(150000);
  expect(cart.issues).toHaveLength(2);
  expect(cart.canCheckout).toBe(false);
});

it('persists only cart identifiers and quantities, then restores after a new session', () => {
  const cart = useCartStore();
  cart.add('pandemic-en', 2);
  expect(JSON.parse(localStorage.getItem(CART_STORAGE_KEY) ?? 'null')).toEqual([
    { variantId: 'pandemic-en', quantity: 2 },
  ]);
  cart.$dispose();
  setActivePinia(createPinia());
  expect(useCartStore().count).toBe(2);
  expect(useCartStore().subtotal).toBe(3380);
});

it('ignores corrupted cart storage', () => {
  localStorage.setItem(CART_STORAGE_KEY, '{bad json');
  expect(useCartStore().items).toEqual([]);
});

it('rechecks a whole cart on pickup changes without removing or reducing lines', () => {
  const cart = useCartStore();
  cart.add('azul-en', 2);
  cart.add('catan-en');
  const original = JSON.stringify(cart.items);
  cart.setFulfillment('pickup', 'ari');
  expect(cart.issues.map((line) => line.variantId)).toEqual([
    'azul-en',
    'catan-en',
  ]);
  expect(cart.canCheckout).toBe(false);
  expect(JSON.stringify(cart.items)).toBe(original);
  cart.setFulfillment('pickup', 'riverside');
  expect(cart.issues.map((line) => line.variantId)).toEqual(['azul-en']);
  cart.setFulfillment('shipping');
  expect(cart.canCheckout).toBe(true);
  expect(JSON.stringify(cart.items)).toBe(original);
});
it('allows pickup-only stock but never pools other branches for delivery', () => {
  const cart = useCartStore();
  expect(cart.add('wingspan-en')).toBe(false);
  cart.setFulfillment('pickup', 'ari');
  expect(cart.add('wingspan-en', 2)).toBe(true);
  expect(cart.canCheckout).toBe(true);
  cart.setFulfillment('shipping');
  expect(cart.count).toBe(2);
  expect(cart.canCheckout).toBe(false);
  expect(cart.update('wingspan-en', 1)).toBe(true); // Can reduce a conflicting line.
  expect(cart.canCheckout).toBe(false);
  cart.setFulfillment('pickup', 'riverside');
  expect(cart.canCheckout).toBe(true);
});
it('requires a pickup branch and restores the same fulfillment context with the cart', () => {
  const cart = useCartStore();
  cart.add('azul-en');
  cart.setFulfillment('pickup');
  expect(cart.canCheckout).toBe(false);
  expect(cart.add('catan-en')).toBe(false);
  cart.setFulfillment('pickup', 'ari');
  expect(cart.canCheckout).toBe(true);
  expect(
    JSON.parse(localStorage.getItem(FULFILLMENT_STORAGE_KEY) || 'null'),
  ).toEqual({ method: 'pickup', branchId: 'ari' });
  cart.$dispose();
  setActivePinia(createPinia());
  expect(useCartStore().method).toBe('pickup');
  expect(useCartStore().pickupBranchId).toBe('ari');
  expect(useCartStore().count).toBe(1);
});
