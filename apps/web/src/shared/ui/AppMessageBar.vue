<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'error' | 'warning' }>(), {
  tone: 'info',
});
</script>
<template>
  <div
    class="app-message-bar"
    :class="tone"
    :role="tone === 'error' ? 'alert' : 'status'"
  >
    <div><slot /></div>
    <div v-if="$slots.actions"><slot name="actions" /></div>
  </div>
</template>
<style scoped>
.app-message-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-surface-subtle);
  color: var(--ui-text);
  font-size: 13px;
  line-height: 20px;
}
.error {
  border-color: var(--ui-danger-stroke);
  background: var(--ui-danger-subtle);
  color: var(--ui-danger);
}
.warning {
  border-color: var(--ui-warning-stroke);
  background: var(--ui-warning-subtle);
  color: var(--ui-warning);
}
</style>
