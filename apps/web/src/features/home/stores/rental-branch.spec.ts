import { beforeEach, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { RENTAL_BRANCH_KEY, useRentalBranchStore } from './rental-branch';
import { useCartStore } from './cart';
import { rentalLibraryAt, retailStockAt } from '../data/branches';
beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
});
it('remembers a valid rental branch and rejects unknown preferences', () => {
  const rental = useRentalBranchStore();
  expect(rental.branch).toBeUndefined();
  rental.select('ari');
  rental.select('unknown');
  expect(rental.branchId).toBe('ari');
  rental.$dispose();
  setActivePinia(createPinia());
  expect(useRentalBranchStore().branchId).toBe('ari');
  useRentalBranchStore().$dispose();
  localStorage.setItem(RENTAL_BRANCH_KEY, 'deleted');
  setActivePinia(createPinia());
  expect(useRentalBranchStore().branch).toBeUndefined();
});
it('changing a rental branch never changes delivery stock, pickup location or cart contents', () => {
  const rental = useRentalBranchStore();
  const cart = useCartStore();
  cart.add('azul-en');
  rental.select('riverside');
  expect(cart.method).toBe('shipping');
  expect(cart.stockFor('azul-en')).toBe(4);
  cart.setFulfillment('pickup', 'ari');
  const items = JSON.stringify(cart.items);
  rental.select('siam');
  expect(cart.pickupBranchId).toBe('ari');
  expect(JSON.stringify(cart.items)).toBe(items);
});
it('keeps rental library membership and teaching support separate from retail stock', () => {
  expect(rentalLibraryAt('azul', 'siam').offered).toBe(true);
  expect(rentalLibraryAt('azul', 'ari')).toMatchObject({
    offered: true,
    staffCanTeach: true,
  });
  expect(rentalLibraryAt('cascadia', 'siam')).toMatchObject({
    offered: true,
    staffCanTeach: false,
  });
  expect(rentalLibraryAt('avalon', 'riverside').offered).toBe(false);
  expect(retailStockAt('wingspan-en', 'siam')).toBe(0);
  expect(retailStockAt('wingspan-en', 'ari')).toBe(2);
});
