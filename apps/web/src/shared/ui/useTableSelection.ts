import { computed, ref, watch, type ComputedRef } from 'vue';

// Selection is limited to the current result set; filters never leave hidden rows selected.
export function useTableSelection(visibleIds: ComputedRef<readonly string[]>) {
  const selectedIds = ref<Set<string>>(new Set());
  const count = computed(() => selectedIds.value.size);
  const allSelected = computed(
    () =>
      visibleIds.value.length > 0 &&
      visibleIds.value.every((id) => selectedIds.value.has(id)),
  );
  const indeterminate = computed(() => count.value > 0 && !allSelected.value);
  function clear() {
    selectedIds.value = new Set();
  }
  function toggle(id: string, checked: boolean) {
    if (!visibleIds.value.includes(id)) return;
    const next = new Set(selectedIds.value);
    if (checked) next.add(id);
    else next.delete(id);
    selectedIds.value = next;
  }
  function toggleAll(checked: boolean) {
    selectedIds.value = new Set(checked ? visibleIds.value : []);
  }
  watch(
    visibleIds,
    () => {
      const visible = new Set(visibleIds.value);
      selectedIds.value = new Set(
        [...selectedIds.value].filter((id) => visible.has(id)),
      );
    },
    { flush: 'sync' },
  );
  return {
    selectedIds,
    count,
    allSelected,
    indeterminate,
    clear,
    toggle,
    toggleAll,
  };
}
