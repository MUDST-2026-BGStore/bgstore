import { beforeEach, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { STORE_CONTEXT_KEY, useStoreContextStore } from './store-context';
import { useCartStore } from './cart';
beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
});
it('keeps browsing preference and cart fulfillment independent in both directions', () => {
  const context = useStoreContextStore();
  const cart = useCartStore();
  context.select('online');
  cart.add('azul-en');
  context.select('ari');
  expect(cart.method).toBe('shipping');
  cart.setFulfillment('pickup', 'riverside');
  cart.update('azul-en', 0);
  expect(context.branch?.id).toBe('ari');
  context.select('online');
  expect(cart.pickupBranchId).toBe('riverside');
  expect(cart.method).toBe('pickup');
});
it('restores valid preferences and rejects unknown branches', () => {
  localStorage.setItem(STORE_CONTEXT_KEY, 'ari');
  const context = useStoreContextStore();
  expect(context.branch?.id).toBe('ari');
  context.select('missing');
  expect(context.choice).toBe('ari');
  context.select('browse');
  expect(context.branch).toBeUndefined();
  expect(localStorage.getItem(STORE_CONTEXT_KEY)).toBe('browse');
});
