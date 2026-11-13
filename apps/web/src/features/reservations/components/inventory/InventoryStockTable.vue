<script setup lang="ts">
// Shared table scaffolding for every Inventory & fulfillment tab:
// scrollable AppDataTable plus the standard compact "No results" empty state
// with a Clear filters action.
import { ref } from 'vue';
import { AppButton, AppDataTable, AppEmptyState } from '../../../../shared/ui';

defineProps<{
  label: string;
  caption?: string;
  columns: readonly { label: string; numeric?: boolean; action?: boolean }[];
  rowCount: number;
  filtersActive: boolean;
  emptyTitle?: string;
  tableClass?: string;
}>();
const emit = defineEmits<{ clearFilters: [] }>();
const table = ref<InstanceType<typeof AppDataTable>>();
defineExpose({
  focus: () => table.value?.focus(),
});
</script>

<template>
  <AppDataTable
    ref="table"
    class="inventory-table-scroll"
    :label="label"
    :caption="caption"
    :columns="columns"
    :table-class="tableClass"
  >
    <template v-if="$slots['selection-header']" #selection-header
      ><slot name="selection-header"
    /></template>
    <slot />
    <tr v-if="!rowCount">
      <td :colspan="columns.length + ($slots['selection-header'] ? 1 : 0)">
        <AppEmptyState compact :title="emptyTitle || 'No results'"
          ><AppButton v-if="filtersActive" @click="emit('clearFilters')"
            >Clear filters</AppButton
          ></AppEmptyState
        >
      </td>
    </tr>
  </AppDataTable>
</template>
