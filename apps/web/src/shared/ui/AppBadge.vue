<script setup lang="ts">
withDefaults(
  defineProps<{ tone?: 'neutral' | 'success' | 'warning' | 'danger' }>(),
  { tone: 'neutral' },
);
</script>
<template>
  <span class="app-badge" :class="`app-badge--${tone}`"><slot /></span>
</template>
<style scoped>
.app-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: var(--ui-radius);
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  white-space: nowrap;
}
.app-badge--neutral {
  color: var(--ui-muted);
  background: var(--ui-neutral-subtle);
}
.app-badge--success {
  color: var(--ui-success);
  background: var(--ui-success-subtle);
}
.app-badge--warning {
  color: var(--ui-warning);
  background: var(--ui-warning-subtle);
}
.app-badge--danger {
  color: var(--ui-danger);
  background: var(--ui-danger-subtle);
}
</style>
