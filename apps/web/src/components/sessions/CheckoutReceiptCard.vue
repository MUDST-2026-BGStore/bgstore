<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { ActiveSessionResponse } from '../../generated/api/types.gen';

const props = defineProps<{ session: ActiveSessionResponse; hours: number }>();
const { t, locale } = useI18n();
const money = (amount: number) =>
  new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: props.session.currency,
    minimumFractionDigits: 2,
  }).format(amount);
const total = computed(() => money(props.session.accruedAmount));
</script>

<template>
  <section class="receipt-card" aria-labelledby="receipt-title">
    <header>
      <h2 id="receipt-title">{{ t('checkout.receipt') }}</h2>
      <span>{{ session.tableName }}</span>
    </header>
    <div class="receipt-summary">
      <div class="receipt-item">
        <div>
          <h3>{{ t('checkout.timeCharge') }}</h3>
          <p>
            {{
              t('checkout.chargeDetail', {
                people: session.partySize,
                hours,
                rate: money(session.ratePerHour),
              })
            }}
          </p>
        </div>
        <strong>{{ total }}</strong>
      </div>
      <footer>
        <span>{{ t('checkout.total') }}</span>
        <strong>{{ total }}</strong>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.receipt-card {
  display: grid;
  min-width: 0;
  align-content: start;
  border: 1px solid var(--session-border);
  border-radius: 0.75rem;
  background: #fff;
  padding: 1.25rem;
  box-shadow: 0 0.35rem 1rem rgb(49 91 101 / 4%);
}
.receipt-summary {
  width: 100%;
  padding-top: 1.25rem;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--session-border);
}
h2 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}
header span {
  color: var(--session-muted);
  font-size: 0.8rem;
}
.receipt-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 1rem;
  padding: 0 0 1.1rem;
}
h3 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
}
.receipt-item strong {
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}
p {
  margin: 0.5rem 0 0;
  color: var(--session-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
footer {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding-top: 1.1rem;
  border-top: 1px dashed var(--session-border);
}
footer span {
  font-size: 1rem;
  font-weight: 600;
}
footer strong {
  color: var(--session-primary-strong);
  font-size: clamp(1.5rem, 2.5vw, 1.75rem);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}
@media (max-width: 420px) {
  .receipt-card {
    padding: 1rem;
  }
  .receipt-item {
    grid-template-columns: 1fr;
    gap: 0.65rem;
  }
}
</style>
