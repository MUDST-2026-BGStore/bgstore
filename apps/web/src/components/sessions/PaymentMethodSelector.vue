<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import type { PaymentMethod } from '../../features/sessions/checkout-fixture';

const selected = defineModel<PaymentMethod | null>({ required: true });
const { t } = useI18n();
const methods: PaymentMethod[] = ['promptpay', 'bank', 'cash'];
</script>

<template>
  <fieldset class="payment-methods">
    <legend>{{ t('checkout.selectMethod') }}</legend>
    <p class="group-hint">{{ t('checkout.selectHint') }}</p>
    <label
      v-for="method in methods"
      :key="method"
      class="method-card"
      :class="{ selected: selected === method }"
    >
      <input
        v-model="selected"
        type="radio"
        name="payment-method"
        :value="method"
        :aria-describedby="`${method}-hint`"
      />
      <span class="method-copy">
        <strong>{{ t(`checkout.${method}`) }}</strong>
        <span :id="`${method}-hint`">{{ t(`checkout.${method}Hint`) }}</span>
      </span>
      <span class="method-icon" aria-hidden="true">
        <svg v-if="method === 'promptpay'" viewBox="0 0 24 24">
          <path
            d="M3 3h6v6H3zM15 3h6v6h-6zM3 15h6v6H3zM15 15h2v2h-2zM21 14v4h-3v3M12 3v3M3 12h3M12 9v4h3M12 18v3"
          />
        </svg>
        <svg v-else-if="method === 'bank'" viewBox="0 0 24 24">
          <path
            d="m3 8 9-5 9 5v2H3zM5 10v8M10 10v8M14 10v8M19 10v8M3 21h18M3 18h18"
          />
        </svg>
        <svg v-else viewBox="0 0 24 24">
          <rect x="2" y="5" width="20" height="14" rx="2" />
          <circle cx="12" cy="12" r="3" />
          <path d="M5 12h1M18 12h1" />
        </svg>
      </span>
    </label>
  </fieldset>
</template>

<style scoped>
.payment-methods {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
legend {
  padding: 0;
  margin-bottom: 0.25rem;
  font-size: 1.1rem;
  font-weight: 600;
}
.group-hint {
  margin: 0 0 0.85rem;
  color: var(--session-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}
.method-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.85rem;
  margin-bottom: 0.5rem;
  min-height: 4rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--session-border);
  border-radius: 0.65rem;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 150ms,
    background 150ms;
}
.method-card:last-child {
  margin-bottom: 0;
}
.method-card:hover,
.selected {
  border-color: var(--session-primary);
  background: #f0f6f6;
}
input {
  width: 1rem;
  height: 1rem;
  margin: 0;
  accent-color: var(--session-primary-strong);
}
input:focus-visible {
  outline: 2px solid var(--session-primary-strong);
  outline-offset: 3px;
}
.method-copy {
  display: grid;
  gap: 0.25rem;
}
.method-copy strong {
  font-size: 0.9rem;
  font-weight: 600;
}
.method-copy > span {
  color: var(--session-muted);
  font-size: 0.8rem;
  line-height: 1.5;
}
.method-icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: 0.5rem;
  color: var(--session-primary);
  background: #edf3f4;
}
.selected .method-icon {
  background: #dcebed;
}
svg {
  width: 1.25rem;
  height: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
@media (prefers-reduced-motion: reduce) {
  .method-card {
    transition: none;
  }
}
</style>
