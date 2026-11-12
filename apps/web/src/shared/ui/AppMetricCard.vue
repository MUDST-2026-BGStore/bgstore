<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    total?: string | number;
    caption?: string;
    as?: 'button' | 'div';
    appearance?: 'surface' | 'plain';
    tone?: 'neutral' | 'success' | 'warning' | 'danger';
  }>(),
  {
    as: 'div',
    appearance: 'surface',
    tone: 'neutral',
    total: undefined,
    caption: undefined,
  },
);
</script>
<template>
  <component
    :is="as"
    :type="as === 'button' ? 'button' : undefined"
    class="app-metric-card"
    :class="{ interactive: as === 'button', plain: appearance === 'plain' }"
  >
    <span class="metric-label"
      >{{ label
      }}<i
        v-if="as === 'button'"
        class="fa-solid fa-arrow-right"
        aria-hidden="true"
      ></i
    ></span>
    <strong class="metric-value" :class="`metric-value--${tone}`"
      >{{ value
      }}<span v-if="total !== undefined" class="metric-total">
        / {{ total }}</span
      ></strong
    >
    <span v-if="caption" class="metric-caption">{{ caption }}</span>
  </component>
</template>
<style scoped>
.app-metric-card {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 16px 20px;
  border: 1px solid var(--ui-stroke-subtle);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
  text-align: left;
  color: var(--ui-text);
  font: inherit;
  font-size: 14px;
  line-height: 20px;
}
.plain {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.metric-value--success {
  color: var(--ui-brand);
}
.metric-value--warning {
  color: var(--ui-warning);
}
.metric-value--danger {
  color: var(--ui-danger);
}
.interactive {
  cursor: pointer;
  transition:
    border-color 120ms ease,
    box-shadow 120ms ease;
}
.interactive:hover {
  border-color: var(--ui-stroke-strong);
  box-shadow: 0 2px 4px rgb(0 0 0 / 7%);
}
.interactive:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 2px;
}
.metric-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 600;
}
.metric-label i {
  color: var(--ui-muted);
  font-size: 12px;
}
.metric-value {
  font-size: 32px;
  line-height: 40px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.metric-total {
  font-size: 20px;
  font-weight: 400;
  color: var(--ui-muted);
}
.metric-caption {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
}
@media (max-width: 600px) {
  .app-metric-card {
    padding: 12px;
  }
  .metric-label {
    font-size: 12px;
    font-weight: 500;
  }
  .metric-label i {
    display: none;
  }
  .metric-value {
    font-size: 28px;
    line-height: 36px;
  }
  .metric-total {
    font-size: 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .interactive {
    transition: none;
  }
}
</style>
