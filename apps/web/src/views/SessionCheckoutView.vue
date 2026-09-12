<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink } from 'vue-router';
import CheckoutReceiptCard from '../components/sessions/CheckoutReceiptCard.vue';
import PaymentMethodSelector from '../components/sessions/PaymentMethodSelector.vue';
import {
  checkoutFixture,
  type PaymentMethod,
} from '../features/sessions/checkout-fixture';

const { t } = useI18n();
const paymentMethod = ref<PaymentMethod>('promptpay');
const isPaymentComplete = ref(false);
</script>

<template>
  <section
    class="checkout-page"
    :class="{ 'checkout-page--complete': isPaymentComplete }"
  >
    <section
      v-if="isPaymentComplete"
      class="payment-complete"
      aria-labelledby="payment-complete-title"
    >
      <span class="complete-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="m7 12 3 3 7-7" />
        </svg>
      </span>
      <div role="status">
        <h1 id="payment-complete-title">{{ t('checkout.paymentComplete') }}</h1>
        <p>{{ t('checkout.paymentCompleteDescription') }}</p>
      </div>
      <RouterLink class="success-back" :to="{ name: 'client-active-session' }">
        {{ t('checkout.back') }}
      </RouterLink>
    </section>

    <template v-else>
      <header class="checkout-heading">
        <div>
          <h1 id="checkout-title">{{ t('checkout.title') }}</h1>
          <p>{{ t('checkout.description') }}</p>
        </div>
        <RouterLink class="back-link" :to="{ name: 'client-active-session' }"
          ><span aria-hidden="true">←</span>
          {{ t('checkout.back') }}</RouterLink
        >
      </header>
      <div class="preview-notice" role="note">
        <span aria-hidden="true">i</span>
        <p>{{ t('checkout.preview') }}</p>
      </div>
      <div class="checkout-panel">
        <CheckoutReceiptCard :receipt="checkoutFixture" />
        <section class="payment-pane">
          <PaymentMethodSelector v-model="paymentMethod" />
          <div class="payment-actions">
            <button
              class="confirm-payment"
              type="button"
              @click="isPaymentComplete = true"
            >
              {{ t('checkout.confirmPayment') }}
            </button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.checkout-page {
  --session-primary: #497883;
  --session-primary-strong: #315b65;
  --session-muted: #647080;
  --session-border: #dce2e6;
  width: min(88vw, 66rem);
  margin: 0 auto;
  padding: clamp(3rem, 7vh, 5rem) 0 3rem;
  color: #20252d;
}
.checkout-page--complete {
  display: grid;
  min-height: 100dvh;
  place-items: center;
  padding: 2rem 0;
}
.payment-complete {
  display: grid;
  width: min(100%, 28rem);
  justify-items: center;
  padding: 3rem 2rem;
  border: 1px solid var(--session-border);
  border-radius: 0.85rem;
  background: #fff;
  box-shadow: 0 0.75rem 2rem rgb(49 91 101 / 7%);
  text-align: center;
}
.complete-icon {
  display: grid;
  width: 4rem;
  height: 4rem;
  margin-bottom: 1.25rem;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #34796a;
  box-shadow: 0 0 0 0.5rem #e8f4ef;
}
.complete-icon svg {
  width: 2rem;
  height: 2rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.2;
}
.payment-complete h1 {
  margin: 0;
  font-size: clamp(1.65rem, 3vw, 2rem);
}
.payment-complete p {
  margin: 0.65rem 0 1.5rem;
  color: var(--session-muted);
  font-size: 0.86rem;
  line-height: 1.6;
}
.success-back {
  display: inline-flex;
  min-height: 2.6rem;
  align-items: center;
  justify-content: center;
  padding: 0.55rem 1.1rem;
  border: 1px solid var(--session-primary);
  border-radius: 0.55rem;
  color: #fff;
  background: var(--session-primary);
  font-size: 0.82rem;
  font-weight: 700;
}
.success-back:hover {
  background: var(--session-primary-strong);
}
.success-back:focus-visible {
  outline: 2px solid var(--session-primary-strong);
  outline-offset: 3px;
}
.checkout-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.25rem;
  margin-bottom: 1rem;
}
h1 {
  margin: 0;
  font-size: clamp(1.65rem, 3vw, 2.05rem);
  line-height: 1.2;
  letter-spacing: -0.025em;
}
.checkout-heading p {
  margin: 0.45rem 0 0;
  color: var(--session-muted);
  font-size: 0.92rem;
  line-height: 1.5;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2.75rem;
  flex-shrink: 0;
  color: var(--session-primary-strong);
  font-size: 0.85rem;
  font-weight: 600;
  text-underline-offset: 0.25rem;
}
.back-link:focus-visible {
  outline: 2px solid var(--session-primary);
  outline-offset: 4px;
  border-radius: 0.2rem;
}
.preview-notice {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 1.25rem;
  color: var(--session-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}
.preview-notice span {
  display: grid;
  width: 1.1rem;
  height: 1.1rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--session-primary);
  font-size: 0.68rem;
  font-weight: 700;
}
.preview-notice p {
  margin: 0;
}
.checkout-panel {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.1fr);
  gap: 1.5rem;
  align-items: start;
}
.payment-pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 1.25rem;
  border: 1px solid var(--session-border);
  border-radius: 0.75rem;
  background: #fff;
  box-shadow: 0 0.35rem 1rem rgb(49 91 101 / 4%);
}
.payment-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px solid #edf0f2;
}
.confirm-payment {
  width: auto;
  min-width: 8.5rem;
  min-height: 2.5rem;
  padding: 0.55rem 1.15rem;
  border: 1px solid var(--session-primary);
  border-radius: 0.55rem;
  color: #fff;
  background: var(--session-primary);
  font: inherit;
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms ease;
}
.confirm-payment:hover {
  background: var(--session-primary-strong);
}
.confirm-payment:focus-visible {
  outline: 2px solid var(--session-primary-strong);
  outline-offset: 3px;
}
@media (max-width: 850px) {
  .checkout-panel {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .checkout-page {
    width: min(92vw, 34rem);
    padding: 1.75rem 0 2rem;
  }
  .checkout-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.5rem;
  }
  .payment-pane {
    padding: 1.1rem;
  }
  .payment-actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }
  .confirm-payment {
    width: 100%;
  }
}
</style>
