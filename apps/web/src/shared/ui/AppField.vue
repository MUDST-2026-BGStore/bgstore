<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    inputId: string;
    hint?: string;
    layout?: 'stacked' | 'inline';
  }>(),
  { layout: 'stacked', hint: undefined },
);
</script>
<template>
  <div class="app-field" :class="layout">
    <label :for="inputId" class="field-label">{{ label }}</label
    ><slot /><span v-if="hint" class="field-hint">{{ hint }}</span>
  </div>
</template>
<style scoped>
.app-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  color: var(--ui-text);
  font-size: 13px;
  line-height: 20px;
}
.app-field.inline {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.field-label {
  font-weight: 500;
  white-space: nowrap;
}
.field-hint {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
}
.app-field > :deep(:is(input:not([type='checkbox']), select, textarea)) {
  width: 100%;
  min-width: 0;
  min-height: 34px;
  padding: 6px 10px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  line-height: 20px;
}
.app-field > :deep(select:not([multiple]):not([size])) {
  appearance: none;
  padding-inline-end: 32px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath d='m2 4 4 4 4-4' fill='none' stroke='%234e5b53' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  background-size: 12px 12px;
}
.app-field > :deep(select:not([multiple]):not([size]):dir(rtl)) {
  padding-inline-end: 32px;
  background-position: left 10px center;
}
@media (forced-colors: active) {
  .app-field > :deep(select:not([multiple]):not([size])) {
    appearance: auto;
    padding-inline-end: 10px;
    background-image: none;
  }
}
.app-field > :deep(:is(input, select, textarea):focus-visible) {
  outline: 2px solid var(--ui-focus);
  outline-offset: 1px;
}
.app-field.inline > :deep(select) {
  width: auto;
}
</style>
