import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { useBranchDirectoryStore } from './branch-directory';
export const STORE_CONTEXT_KEY = 'theparlor.store-context.v1';

/**
 * Where the shopper is browsing: all games, online (delivery) or one branch. It is
 * a browsing preference that decides which branch's prices and stock product pages
 * show; it never changes a cart's fulfillment or an order.
 */
export const useStoreContextStore = defineStore('store-context', () => {
  const directory = useBranchDirectoryStore();
  let restored = '';
  try {
    restored = localStorage.getItem(STORE_CONTEXT_KEY) || '';
  } catch {
    /* Visit-only preference. */
  }
  const choice = ref(restored);

  const known = (value: string) =>
    ['browse', 'online'].includes(value) || Boolean(directory.byId(value));

  // A saved branch can only be checked once the directory has loaded. A branch
  // that has since closed, or an ID from another environment, is forgotten so
  // the shopper is asked again instead of seeing prices for nowhere.
  watch(
    () => directory.status,
    (status) => {
      if (status === 'ready' && choice.value && !known(choice.value))
        choice.value = '';
    },
    { immediate: true },
  );

  const branch = computed(() => directory.byId(choice.value));
  /** The branch whose prices and stock apply: the chosen one, or the delivery branch online. */
  const stockBranch = computed(() =>
    choice.value === 'online' ? directory.deliveryBranch : branch.value,
  );

  function select(value: string) {
    if (!known(value)) return;
    choice.value = value;
    try {
      localStorage.setItem(STORE_CONTEXT_KEY, value);
    } catch {
      /* Visit-only preference. */
    }
  }
  return { choice, branch, stockBranch, select };
});
