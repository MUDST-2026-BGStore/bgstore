<script setup lang="ts">
defineProps<{ pressed: boolean }>();
</script>
<template>
  <button type="button" class="app-filter-button" :aria-pressed="pressed">
    <slot />
  </button>
</template>
<style scoped>
.app-filter-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid transparent;
  border-radius: var(--ui-radius);
  background: transparent;
  color: var(--ui-muted);
  font: inherit;
  font-size: 13px;
  line-height: 20px;
  cursor: pointer;
}
.app-filter-button:hover {
  background: var(--ui-surface-hover);
  color: var(--ui-text);
}
.app-filter-button[aria-pressed='true'] {
  border-color: var(--ui-stroke);
  background: var(--ui-selected);
  color: var(--ui-brand);
  font-weight: 600;
}
.app-filter-button:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 2px;
}
</style>
