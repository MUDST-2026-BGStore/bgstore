// Visiting preference is independent of purchase fulfillment. Never synchronize
// this selection with cart.pickupBranchId or change cart contents when it changes.
import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { useBranchDirectoryStore } from './branch-directory';
export const RENTAL_BRANCH_KEY = 'theparlor.rental-branch.v1';
function restore() {
  try {
    return localStorage.getItem(RENTAL_BRANCH_KEY) || '';
  } catch {
    return '';
  }
}
export const useRentalBranchStore = defineStore('rental-branch', () => {
  const directory = useBranchDirectoryStore();
  const branchId = ref(restore());
  const branch = computed(() => directory.byId(branchId.value));
  watch(
    () => directory.status,
    (status) => {
      if (
        status === 'ready' &&
        branchId.value &&
        !directory.byId(branchId.value)
      )
        branchId.value = '';
    },
    { immediate: true },
  );
  function select(id: string) {
    if (!id || directory.byId(id)) branchId.value = id;
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
