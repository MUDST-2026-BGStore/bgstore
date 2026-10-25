// TODO(reservation-api): This legacy store saves a fixture branch preference only.
// Before wiring a visit flow, use real branches and query interval availability
// after date/time/duration selection; confirmation must allocate on the server.
import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { branches } from '../data/branches';
export const RENTAL_BRANCH_KEY = 'theparlor.rental-branch.v1';
function restore() {
  try {
    const id = localStorage.getItem(RENTAL_BRANCH_KEY);
    return branches.some((branch) => branch.id === id) ? id || '' : '';
  } catch {
    return '';
  }
}
// Visiting preference is independent of purchase fulfillment. Never synchronize
// this selection with cart.pickupBranchId or change cart contents when it changes.
export const useRentalBranchStore = defineStore('rental-branch', () => {
  const branchId = ref(restore());
  const branch = computed(() =>
    branches.find((item) => item.id === branchId.value),
  );
  function select(id: string) {
    if (!id || branches.some((item) => item.id === id)) branchId.value = id;
  }
  watch(
    branchId,
    (id) => {
      try {
        if (id) localStorage.setItem(RENTAL_BRANCH_KEY, id);
        else localStorage.removeItem(RENTAL_BRANCH_KEY);
      } catch {
        /* Preference still works for this visit. */
      }
    },
    { flush: 'sync' },
  );
  return { branchId, branch, select };
});
