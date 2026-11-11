<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { generateReservationQrCode, type PromptPayQrDetails } from '../data/payment';
import type { FoodOrderItem } from '../types';
import { useStoreText } from '../../home/data/store-text';

const props = defineProps<{
  totalAmount: number;
  tableTotal: number;
  foodItems: FoodOrderItem[];
  foodSubtotal: number;
  foodDiscount: number;
  foodDiscountedTotal: number;
  reservationRef: string;
}>();

const emit = defineEmits<{
  'payment-completed': [details: { reference: string; paidAtTimestamp: string }];
  cancel: [];
}>();

const { t } = useStoreText();
const qrData = ref<PromptPayQrDetails | null>(null);
const isGenerating = ref(true);
const isVerifying = ref(false);
const isPaid = ref(false);
const secondsLeft = ref(15 * 60);
let timer: ReturnType<typeof setInterval> | null = null;

const formattedTime = computed(() => {
  const m = Math.floor(secondsLeft.value / 60);
  const s = secondsLeft.value % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
});

onMounted(async () => {
  try {
    qrData.value = await generateReservationQrCode(
      props.reservationRef,
      props.totalAmount,
      'The Parlor Board Game Cafe'
    );
  } finally {
    isGenerating.value = false;
  }

  timer = setInterval(() => {
    if (secondsLeft.value > 0) {
      secondsLeft.value--;
    } else {
      if (timer) clearInterval(timer);
    }
  }, 1000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

function confirmPayment() {
  isVerifying.value = true;
  // Simulate banking transfer verification delay
  setTimeout(() => {
    isVerifying.value = false;
    isPaid.value = true;
    const paidAt = new Date().toISOString();
    emit('payment-completed', {
      reference: qrData.value?.reference ?? props.reservationRef,
      paidAtTimestamp: paidAt,
    });
  }, 800);
}
</script>

<template>
  <div class="qr-payment-container" role="dialog" aria-modal="true" aria-labelledby="qr-title">
    <div class="qr-card">
      <div class="qr-header">
        <div class="logo-strip">
          <svg class="gold-icon mini-crest" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z"></path></svg>
          <span class="brand-text">THE PARLOR</span>
          <svg class="gold-icon mini-crest" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z"></path></svg>
        </div>
        <h3 id="qr-title" class="payment-title">{{ t('PromptPay QR Checkout') }}</h3>
        <p class="payment-subtitle">
          {{ t('Scan to pay with any Thai Mobile Banking app') }}
        </p>
      </div>

      <!-- Payment Breakdown -->
      <div class="bill-breakdown">
        <div class="line-row">
          <span>{{ t('Table Fee') }}</span>
          <span class="val">฿{{ tableTotal }}</span>
        </div>
        <div v-if="foodItems.length > 0" class="line-row food-row">
          <span>
            {{ t('Pre-ordered Refreshments ({count} items)', { count: foodItems.reduce((a, b) => a + b.quantity, 0) }) }}
            <small class="discount-label">{{ t('10% OFF applied') }}</small>
          </span>
          <div class="food-vals">
            <span class="strike">฿{{ foodSubtotal }}</span>
            <span class="val">฿{{ foodDiscountedTotal }}</span>
          </div>
        </div>
        <div class="total-row">
          <span>{{ t('Total Amount to Pay') }}</span>
          <span class="grand-total">฿{{ totalAmount }}</span>
        </div>
      </div>

      <!-- QR Code Display -->
      <div class="qr-box">
        <div v-if="isGenerating" class="loading-state">
          <span>{{ t('Generating Dynamic QR Code…') }}</span>
        </div>
        <div v-else-if="qrData" class="qr-image-wrapper">
          <img :src="qrData.dataUrl" alt="PromptPay QR Code for Payment" class="qr-img" />
          <div class="qr-meta">
            <div class="ref-pill">Ref: {{ qrData.reference }}</div>
            <div class="timer-pill" :class="{ 'is-expiring': secondsLeft < 180 }">
              {{ t('Payment window expires in') }}: {{ formattedTime }}
            </div>
          </div>
        </div>
      </div>

      <!-- Action Footer -->
      <div class="action-footer">
        <button
          v-if="!isPaid"
          type="button"
          class="confirm-pay-btn"
          :disabled="isVerifying"
          @click="confirmPayment"
        >
          <span v-if="isVerifying">{{ t('Verifying Bank Transfer…') }}</span>
          <span v-else>{{ t('I Have Paid / Simulate Bank Transfer ✓') }}</span>
        </button>

        <div v-else class="paid-success-banner">
          <svg class="gold-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
          <span>{{ t('Payment Verified & Confirmed!') }}</span>
        </div>

        <button
          v-if="!isPaid"
          type="button"
          class="cancel-btn"
          @click="emit('cancel')"
        >
          {{ t('Back to Details') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.qr-payment-container {
  animation: qrModalFadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
}

.qr-card {
  animation: qrCardSlideUp 0.35s cubic-bezier(0.16, 1, 0.3, 1);
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 12px;
  max-width: 440px;
  width: 100%;
  padding: 24px 22px;
  box-shadow: 0 10px 30px color-mix(in srgb, var(--color-parlor-green-900) 12%, transparent);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.qr-header {
  text-align: center;
}

.logo-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-parlor-gold-500);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.15em;
  margin-bottom: 6px;
}

.brand-text {
  color: var(--color-parlor-green-800);
}

.payment-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--color-parlor-green-850);
}

.payment-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

.bill-breakdown {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-parlor-neutral-600);
}

.discount-label {
  display: block;
  font-size: 11px;
  color: var(--color-parlor-warning-600);
  font-weight: 700;
}

.food-vals {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strike {
  text-decoration: line-through;
  color: var(--color-parlor-neutral-500);
  font-size: 12px;
}

.val {
  font-weight: 600;
  color: var(--color-parlor-green-850);
}

.total-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--color-parlor-neutral-300);
  padding-top: 10px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.grand-total {
  font-size: 20px;
  color: var(--color-parlor-green-800);
}

.qr-box {
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--color-parlor-page);
  border: 1px dashed var(--color-parlor-gold-500);
  border-radius: 10px;
  padding: 18px;
}

.qr-image-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.qr-img {
  width: 220px;
  height: 220px;
  border-radius: 8px;
  border: 4px solid var(--color-parlor-white);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--color-parlor-green-950) 8%, transparent);
}

.qr-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  font-weight: 600;
}

.ref-pill {
  background: var(--color-parlor-neutral-300);
  color: var(--color-parlor-neutral-800);
  padding: 3px 8px;
  border-radius: 4px;
}

.timer-pill {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
  padding: 3px 8px;
  border-radius: 4px;
}

.timer-pill.is-expiring {
  background: var(--color-parlor-danger-100);
  color: var(--color-parlor-danger-700);
}

.action-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.confirm-pay-btn {
  background: var(--color-parlor-green-800);
  color: var(--color-parlor-white);
  border: none;
  padding: 13px 18px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 140ms ease;
}

.confirm-pay-btn:hover:not(:disabled) {
  background: var(--color-parlor-green-900);
}

.confirm-pay-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.paid-success-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
  padding: 12px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
}

.check-icon {
  font-size: 16px;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--color-parlor-neutral-300);
  color: var(--color-parlor-neutral-600);
  padding: 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.cancel-btn:hover {
  background: var(--color-parlor-neutral-100);
}

@keyframes qrModalFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes qrCardSlideUp {
  from { opacity: 0; transform: translateY(18px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.gold-icon {
  width: 18px;
  height: 18px;
  color: var(--color-parlor-gold-300);
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
  filter: drop-shadow(0 0 4px color-mix(in srgb, var(--color-parlor-gold-500) 35%, transparent));
}
.gold-icon.mini-crest {
  width: 14px;
  height: 14px;
}
</style>

