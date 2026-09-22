<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { ActiveSessionResponse } from '../../generated/api/types.gen';

const props = defineProps<{
  session: ActiveSessionResponse;
  elapsedTime: string;
}>();

const { locale, t } = useI18n();
const formattedFee = computed(() =>
  new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: props.session.currency,
    minimumFractionDigits: 2,
  }).format(props.session.accruedAmount),
);
</script>

<template>
  <section class="session-overview" aria-labelledby="elapsed-time-title">
    <header class="session-context">
      <div class="session-identity">
        <span class="active-badge">
          <span class="active-dot" aria-hidden="true" />
          {{ t('activeSession.active') }}
        </span>
        <div class="session-location">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
            <circle cx="12" cy="10" r="2.5" />
          </svg>
          <p>{{ session.locationName }}</p>
        </div>
      </div>

      <strong class="table-badge">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 8h14M7 8V5h10v3M7 8v11M17 8v11M4 19h16" />
        </svg>
        {{ session.tableName }}
      </strong>
    </header>

    <div class="metric-grid">
      <section class="metric-card metric-card--primary">
        <div class="metric-label">
          <span class="metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <circle cx="12" cy="13" r="8" />
              <path d="M12 9v4l3 2M9 3h6" />
            </svg>
          </span>
          <h2 id="elapsed-time-title">
            {{ t('activeSession.elapsedTime') }}
          </h2>
        </div>
        <strong class="elapsed-time" aria-live="off">{{ elapsedTime }}</strong>
        <small>{{ t('activeSession.timeTracking') }}</small>
      </section>

      <section class="metric-card metric-card--secondary">
        <div class="metric-label">
          <span class="metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M4 7h15a2 2 0 0 1 2 2v10H6a2 2 0 0 1-2-2V7Z" />
              <path d="M4 7V6a2 2 0 0 1 2-2h11v3M16 12h5v4h-5a2 2 0 0 1 0-4Z" />
            </svg>
          </span>
          <h2>{{ t('activeSession.accumulatedFee') }}</h2>
        </div>
        <strong class="fee">{{ formattedFee }}</strong>
        <small>{{ t('activeSession.estimatedFee') }}</small>
      </section>
    </div>
  </section>
</template>

<style scoped>
.session-overview {
  min-width: 0;
  container-type: inline-size;
  display: grid;
  grid-template-rows: auto 1fr;
  border: 1px solid var(--session-border, #dce2e6);
  border-radius: var(--session-radius-lg, 0.75rem);
  overflow: hidden;
  background: var(--session-surface, #fff);
  box-shadow: 0 4px 20px #315b6506;
}

.session-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.15rem 1.5rem;
  border-bottom: 1px solid var(--session-border, #dce2e6);
  color: var(--session-muted, #647080);
  background: var(--session-surface, #fff);
  font-size: 0.86rem;
}

.session-identity,
.session-location,
.table-badge {
  display: flex;
  align-items: center;
}

.session-identity {
  min-width: 0;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.session-location {
  min-width: 0;
  gap: 0.45rem;
}

.session-location svg,
.table-badge svg {
  width: 1rem;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.session-context p {
  margin: 0;
  overflow-wrap: anywhere;
  line-height: 1.5;
}

.table-badge {
  flex: 0 0 auto;
  gap: 0.4rem;
  color: var(--session-primary-strong, #315b65);
  font-size: 0.8rem;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--session-border, #dce2e6);
  border-radius: 0.5rem;
  white-space: nowrap;
}

.active-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.6rem;
  border-radius: 2rem;
  background: #edf7f2;
  color: #286c58;
  white-space: nowrap;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.active-dot {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 50%;
  background: #3d8d79;
  box-shadow: 0 0 0 3px #3d8d7912;
}

.metric-grid {
  display: grid;
  grid-template-rows: 1.12fr 0.88fr;
  background: var(--session-surface, #fff);
}

.metric-card {
  display: grid;
  min-width: 0;
  min-height: 9.5rem;
  align-content: center;
  justify-items: start;
  padding: 1.5rem clamp(1.25rem, 6cqi, 2.75rem);
  text-align: left;
}

.metric-card--primary {
  background: linear-gradient(110deg, #f0f6f6, #f8fafb);
}

.metric-card--secondary {
  background: var(--session-surface, #fff);
}

.metric-card + .metric-card {
  border-top: 1px solid var(--session-border, #dce2e6);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--session-primary, #497883);
}

.metric-icon svg {
  width: 1.2rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.metric-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.metric-card h2 {
  margin: 0;
  color: #53606b;
  font-size: 0.76rem;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.elapsed-time {
  color: var(--session-primary-strong, #315b65);
  font-size: clamp(2rem, 8cqi, 3rem);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.045em;
  line-height: 1.1;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.fee {
  color: var(--session-primary, #497883);
  font-size: clamp(1.5rem, 5.5cqi, 2rem);
  letter-spacing: -0.035em;
  max-width: 100%;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
  line-height: 1.05;
}

.metric-card small {
  max-width: 30rem;
  margin-top: 0.75rem;
  color: var(--session-muted, #647080);
  font-size: 0.8rem;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .session-overview {
    border-radius: 0.65rem;
  }

  .session-context {
    align-items: flex-start;
    padding: 1rem 1.25rem;
  }

  .session-identity {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.55rem;
  }

  .session-location p {
    white-space: normal;
  }

  .metric-card {
    min-height: 10rem;
    padding: 1.5rem 1.25rem;
  }
}
</style>
