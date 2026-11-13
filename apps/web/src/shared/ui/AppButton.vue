<script setup lang="ts">
withDefaults(
  defineProps<{
    appearance?: 'primary' | 'secondary' | 'subtle';
    intent?: 'default' | 'danger';
    type?: 'button' | 'submit' | 'reset';
    disabled?: boolean;
  }>(),
  {
    appearance: 'secondary',
    intent: 'default',
    type: 'button',
    disabled: false,
  },
);
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    class="app-button"
    :class="[
      `app-button--${appearance}`,
      { 'app-button--danger': intent === 'danger' },
    ]"
  >
    <slot />
  </button>
</template>

<style scoped>
.app-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 32px;
  padding: 5px 12px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 100ms ease,
    border-color 100ms ease;
}
.app-button:hover:enabled {
  background: var(--ui-surface-hover);
  border-color: var(--ui-stroke-strong);
}
.app-button--primary {
  background: var(--ui-brand);
  border-color: var(--ui-brand);
  color: white;
  font-weight: 600;
}
.app-button--primary:hover:enabled {
  background: var(--ui-brand-hover);
  border-color: var(--ui-brand-hover);
}
.app-button--subtle {
  border-color: transparent;
  background: transparent;
  color: var(--ui-brand);
}
.app-button--subtle:hover:enabled {
  background: var(--ui-selected);
  border-color: transparent;
}
.app-button--danger {
  color: var(--ui-danger);
  border-color: var(--ui-danger-stroke);
}
.app-button--danger:hover:enabled {
  color: var(--ui-danger-hover);
  background: var(--ui-danger-subtle);
  border-color: var(--ui-danger);
}
.app-button--danger.app-button--primary {
  background: var(--ui-danger);
  border-color: var(--ui-danger);
  color: white;
}
.app-button--danger.app-button--primary:hover:enabled {
  background: var(--ui-danger-hover);
  border-color: var(--ui-danger-hover);
}
.app-button--danger.app-button--subtle {
  border-color: transparent;
}
.app-button:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 2px;
}
.app-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.app-button :deep(i) {
  font-size: 12px;
}
@media (prefers-reduced-motion: reduce) {
  .app-button {
    transition: none;
  }
}
</style>
