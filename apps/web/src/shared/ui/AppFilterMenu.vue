<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import AppCheckbox from './AppCheckbox.vue';
import AppDropdown from './AppDropdown.vue';
import type { FilterGroup, FilterValues } from './dropdown-types';

const props = defineProps<{
  modelValue: FilterValues;
  groups: readonly FilterGroup[];
  disabled?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: FilterValues] }>();
const id = useId();
const expanded = ref(
  new Set(props.groups.slice(0, 1).map((group) => group.id)),
);
const selectedCount = computed(() =>
  Object.values(props.modelValue).reduce(
    (count, values) => count + values.length,
    0,
  ),
);
function toggleGroup(groupId: string) {
  const next = new Set(expanded.value);
  if (next.has(groupId)) next.delete(groupId);
  else next.add(groupId);
  expanded.value = next;
}
function setOption(groupId: string, value: string, checked: boolean) {
  const values = new Set(props.modelValue[groupId] || []);
  if (checked) values.add(value);
  else values.delete(value);
  emit('update:modelValue', { ...props.modelValue, [groupId]: [...values] });
}
</script>

<template>
  <AppDropdown
    class="app-filter-menu"
    :label="selectedCount ? `Filter (${selectedCount})` : 'Filter'"
    panel-label="Filters"
    :disabled="disabled"
    :width="320"
  >
    <template #trigger>
      <svg
        width="18"
        height="18"
        viewBox="0 0 18 18"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M2 4h14M5 9h8M7 14h4"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
        />
      </svg>
      <span>Filter</span
      ><span v-if="selectedCount" class="filter-count">{{
        selectedCount
      }}</span>
    </template>
    <button
      type="button"
      class="filter-clear"
      @click="emit('update:modelValue', {})"
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 18 18"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M2 4h7M4 9h8M6 14h4M12 3l4 4m0-4-4 4"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
        /></svg
      >Clear all
    </button>
    <section
      v-for="(group, index) in groups"
      :key="group.id"
      class="filter-group"
    >
      <button
        type="button"
        class="filter-group-toggle"
        :aria-expanded="expanded.has(group.id)"
        :aria-controls="`${id}-group-${index}`"
        @click="toggleGroup(group.id)"
      >
        <span>{{ group.label }}</span>
        <svg
          :class="{ expanded: expanded.has(group.id) }"
          width="12"
          height="12"
          viewBox="0 0 12 12"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="m2 4 4 4 4-4"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
      </button>
      <div
        v-show="expanded.has(group.id)"
        :id="`${id}-group-${index}`"
        role="group"
        :aria-label="group.label"
        class="filter-options"
      >
        <label
          v-for="option in group.options"
          :key="option.value"
          class="filter-option"
          :class="{ disabled: option.disabled }"
        >
          <AppCheckbox
            :label="option.label"
            :model-value="(modelValue[group.id] || []).includes(option.value)"
            :disabled="option.disabled"
            @update:model-value="setOption(group.id, option.value, $event)"
          />
          <span>{{ option.label }}</span>
        </label>
      </div>
    </section>
  </AppDropdown>
</template>

<style scoped>
.filter-count {
  min-width: 18px;
  padding-inline: 4px;
  border-radius: var(--ui-radius);
  background: var(--ui-selected);
  color: var(--ui-brand);
  font-size: 12px;
  text-align: center;
}
.filter-clear,
.filter-group-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 42px;
  padding: 10px 14px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.filter-clear {
  border-bottom: 1px solid var(--ui-divider);
}
.filter-clear:hover,
.filter-group-toggle:hover {
  background: var(--ui-surface-hover);
}
.filter-group + .filter-group {
  border-top: 1px solid var(--ui-divider);
}
.filter-group-toggle {
  justify-content: space-between;
}
.filter-group-toggle svg {
  flex-shrink: 0;
  transition: transform 100ms ease;
}
.filter-group-toggle svg.expanded {
  transform: rotate(180deg);
}
.filter-options {
  display: grid;
}
.filter-option {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 6px 14px;
  cursor: pointer;
}
.filter-option:not(.disabled):hover {
  background: var(--ui-selected);
}
.filter-option:not(.disabled):has(:focus-visible) {
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
.filter-option span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.filter-option.disabled {
  color: var(--ui-muted);
  cursor: not-allowed;
}
.filter-option :deep(.app-checkbox) {
  flex: 0 0 16px;
  width: 16px;
  height: 16px;
}
.filter-clear:focus-visible,
.filter-group-toggle:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
@media (prefers-reduced-motion: reduce) {
  .filter-group-toggle svg {
    transition: none;
  }
}
</style>
