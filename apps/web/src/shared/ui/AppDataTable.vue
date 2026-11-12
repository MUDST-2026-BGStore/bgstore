<script setup lang="ts">
import { ref } from 'vue';
// Slotted inspector rows use data-record-row and data-active for consistent states.
defineProps<{
  label: string;
  caption?: string;
  columns: readonly { label: string; numeric?: boolean; action?: boolean }[];
  tableClass?: string;
}>();
const scrollRegion = ref<HTMLDivElement>();
defineExpose({
  focus: () => scrollRegion.value?.focus({ preventScroll: true }),
});
</script>

<template>
  <div
    ref="scrollRegion"
    class="app-table-scroll"
    role="region"
    tabindex="0"
    :aria-label="label"
  >
    <table class="app-data-table" :class="tableClass">
      <caption class="table-caption">
        {{
          caption || label
        }}
      </caption>
      <thead>
        <tr>
          <slot name="selection-header" />
          <th
            v-for="column in columns"
            :key="column.label"
            scope="col"
            :class="{
              numeric: column.numeric,
              'action-heading': column.action,
            }"
          >
            <span :class="{ 'table-caption': column.action }">{{
              column.label
            }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <slot />
      </tbody>
      <tfoot v-if="$slots.footer">
        <slot name="footer" />
      </tfoot>
    </table>
  </div>
</template>

<style scoped>
.app-table-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}
.app-table-scroll:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
.app-data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 14px;
  line-height: 20px;
  color: var(--ui-text);
}
.table-caption {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
.app-data-table :deep(thead th) {
  padding: 8px 16px;
  border-block: 1px solid var(--ui-divider);
  background: var(--ui-surface);
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
}
.app-data-table :deep(tbody > tr > :is(th, td)) {
  padding: 8px 16px;
  border-bottom: 1px solid var(--ui-divider);
  vertical-align: middle;
}
.app-data-table :deep(tbody > tr:last-child > *) {
  border-bottom: 0;
}
.app-data-table :deep(tbody > tr:hover > *) {
  background: var(--ui-surface-hover);
}
.app-data-table :deep(tbody > tr[data-record-row]) {
  cursor: pointer;
}
.app-data-table :deep(tbody strong) {
  font-weight: 600;
}
.app-data-table :deep(tbody > tr[aria-selected='true'] > *),
.app-data-table :deep(tbody > tr[data-active='true'] > *) {
  background: var(--ui-selected);
}
.app-data-table :deep(tbody th) {
  font-weight: 400;
}
.app-data-table :deep(tfoot > tr > *) {
  padding: 12px 16px;
  border-top: 1px solid var(--ui-divider);
  background: var(--ui-surface-subtle);
  font-weight: 600;
}
.app-data-table :deep(.numeric) {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.app-data-table :deep(.selection-cell) {
  width: 44px;
  padding-inline: 12px;
  text-align: center;
}
</style>
