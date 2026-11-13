import { beforeEach, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { RENTAL_BRANCH_KEY, useRentalBranchStore } from './rental-branch';
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
it('keeps the visiting preference in its own storage key, apart from the cart', () => {
  const rental = useRentalBranchStore();
  rental.select('riverside');
  expect(localStorage.getItem(RENTAL_BRANCH_KEY)).toBe('riverside');
  expect(Object.keys(localStorage)).toEqual([RENTAL_BRANCH_KEY]);
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
