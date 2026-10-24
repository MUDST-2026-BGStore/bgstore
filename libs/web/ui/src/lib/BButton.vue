<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
}>();
</script>

<template>
  <button
    class="b-button"
    :class="[`b-button--${variant ?? 'primary'}`]"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.b-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1.25rem;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  line-height: 1.5;
}

.b-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.b-button--primary {
  background: var(--color-primary, #6366f1);
  color: white;
}

.b-button--primary:hover:not(:disabled) {
  background: var(--color-primary-hover, #4f46e5);
}

.b-button--secondary {
  background: var(--color-surface, #1e293b);
  color: var(--color-text, #e2e8f0);
  border: 1px solid var(--color-border, #334155);
}

.b-button--secondary:hover:not(:disabled) {
  background: var(--color-surface-hover, #334155);
}

.b-button--danger {
  background: var(--color-danger, #ef4444);
  color: white;
}

.b-button--danger:hover:not(:disabled) {
  background: var(--color-danger-hover, #dc2626);
}
</style>
