// TODO(branch-api): Validate restored/selected IDs against active API branches once
// branch reads are implemented; the current directory contains fictional IDs.
import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { branches } from '../data/branches';
export const STORE_CONTEXT_KEY = 'theparlor.store-context.v1';
export const useStoreContextStore = defineStore('store-context', () => {
  const valid = (value: string) =>
    ['browse', 'online'].includes(value) ||
    branches.some((branch) => branch.id === value);
  let restored = '';
  try {
    const value = localStorage.getItem(STORE_CONTEXT_KEY) || '';
    if (valid(value)) restored = value;
  } catch {
    /* Visit-only preference. */
  }
  const choice = ref(restored);
  const branch = computed(() =>
    branches.find((item) => item.id === choice.value),
  );
  function select(value: string) {
    if (!valid(value)) return;
    choice.value = value;
    try {
      localStorage.setItem(STORE_CONTEXT_KEY, value);
    } catch {
      /* Visit-only preference. */
    }
  }
  // A remembered location is a browsing preference, never an inventory result.
  return { choice, branch, select };
});
