<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { ActiveSessionSnapshot } from '../../features/sessions/active-session-types';

const props = defineProps<{ session: ActiveSessionSnapshot }>();
const { locale, t } = useI18n();

const startTime = computed(() =>
  new Intl.DateTimeFormat(locale.value, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(props.session.startedAt)),
);
const hourlyRate = computed(() =>
  new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: props.session.currency,
    maximumFractionDigits: 0,
  }).format(props.session.hourlyRatePerPerson),
);
</script>

<template>
  <section class="fee-card" aria-labelledby="fee-breakdown-title">
    <header>
      <span class="receipt-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" />
          <path d="M9 8h6M9 12h6" />
        </svg>
      </span>
      <div>
        <h2 id="fee-breakdown-title">
          {{ t('activeSession.feeBreakdown') }}
        </h2>
        <p>{{ t('activeSession.currentPricing') }}</p>
      </div>
    </header>

    <dl>
      <div>
        <dt>{{ t('activeSession.startTime') }}</dt>
        <dd>{{ startTime }}</dd>
      </div>
      <div>
        <dt>{{ t('activeSession.partySize') }}</dt>
        <dd>{{ t('activeSession.people', { count: session.partySize }) }}</dd>
      </div>
      <div>
        <dt>{{ t('activeSession.hourlyRate') }}</dt>
        <dd>
          {{ t('activeSession.ratePerPerson', { rate: hourlyRate }) }}
        </dd>
      </div>
    </dl>

    <footer>
      <span class="refresh-dot" aria-hidden="true" />
      {{ t('activeSession.refreshNotice') }}
    </footer>
  </section>
</template>

<style scoped>
.fee-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--session-border, #dce2e6);
  border-radius: var(--session-radius-lg, 0.75rem);
  overflow: hidden;
  background: var(--session-surface, #fff);
}

header {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--session-border, #dce2e6);
}

.receipt-icon {
  display: grid;
  width: 1.25rem;
  height: 1.25rem;
  flex: 0 0 auto;
  place-items: center;
  color: var(--session-primary, #497883);
}

.receipt-icon svg {
  width: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

h2 {
  margin: 0;
  font-size: 1.05rem;
}

header p {
  margin: 0.25rem 0 0;
  color: #778391;
  font-size: 0.75rem;
}

dl {
  display: grid;
  grid-template-rows: repeat(3, minmax(2.75rem, 1fr));
  flex: 1;
  margin: 0;
  padding: 0.35rem 1.25rem;
}

dl div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid #edf0f2;
}

dl div:last-child {
  border-bottom: 0;
}

dt {
  color: #647080;
  font-size: 0.84rem;
}

dd {
  margin: 0;
  color: #20252d;
  font-size: 0.86rem;
  font-weight: 700;
  text-align: right;
  overflow-wrap: anywhere;
}

footer {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.9rem 1.25rem;
  border-top: 1px solid var(--session-border, #dce2e6);
  color: var(--session-muted, #647080);
  font-size: 0.73rem;
}

.refresh-dot {
  width: 0.45rem;
  height: 0.45rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--session-primary, #497883);
}
</style>
