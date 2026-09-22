<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMutation, useQuery } from '@tanstack/vue-query';
import ActiveSessionActions from '../components/sessions/ActiveSessionActions.vue';
import ActiveSessionOverview from '../components/sessions/ActiveSessionOverview.vue';
import FeeBreakdownCard from '../components/sessions/FeeBreakdownCard.vue';
import { useActiveSession } from '../features/sessions/use-active-session';
import {
  activeSessionQueryOptions,
  requestSessionAssistance,
} from '../queries/play-sessions';
import type { SessionAssistanceKind } from '../generated/api/types.gen';

const { t } = useI18n();
const activeSession = useQuery(activeSessionQueryOptions());
const session = computed(() => activeSession.data.value ?? null);
const { elapsedTime } = useActiveSession(() => session.value);
const notice = ref<'callStaff' | 'endPlaying' | null>(null);

const assistance = useMutation({
  mutationFn: async (kind: SessionAssistanceKind) => {
    const current = session.value;
    return current
      ? requestSessionAssistance(current.reservationId, kind)
      : null;
  },
  onSuccess: (_response, kind) => {
    notice.value = kind === 'CallStaff' ? 'callStaff' : 'endPlaying';
  },
});
</script>

<template>
  <section class="active-session-page" aria-labelledby="active-session-title">
    <header class="page-heading">
      <h1 id="active-session-title">{{ t('activeSession.title') }}</h1>
      <p>{{ t('activeSession.description') }}</p>
    </header>

    <p v-if="activeSession.isPending.value" class="session-state" role="status">
      {{ t('activeSession.loading') }}
    </p>

    <div
      v-else-if="activeSession.isError.value"
      class="session-state"
      role="alert"
    >
      <p>{{ t('activeSession.loadFailed') }}</p>
      <button type="button" @click="activeSession.refetch()">
        {{ t('activeSession.retry') }}
      </button>
    </div>

    <div v-else-if="!session" class="session-state">
      <h2>{{ t('activeSession.noSessionTitle') }}</h2>
      <p>{{ t('activeSession.noSessionDescription') }}</p>
    </div>

    <template v-else>
      <p v-if="notice" class="action-notice" role="status">
        {{ t(`activeSession.${notice}Notice`) }}
      </p>
      <p
        v-else-if="assistance.isError.value"
        class="action-notice action-notice--error"
        role="alert"
      >
        {{ t('activeSession.assistanceFailed') }}
      </p>

      <div class="session-layout">
        <ActiveSessionOverview
          class="session-overview-panel"
          :session="session"
          :elapsed-time="elapsedTime"
        />
        <FeeBreakdownCard class="fee-breakdown-panel" :session="session" />
        <ActiveSessionActions
          class="session-actions-panel"
          :busy="assistance.isPending.value"
          @call-staff="assistance.mutate('CallStaff')"
          @end-playing="assistance.mutate('EndPlaying')"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.active-session-page {
  --session-primary: #497883;
  --session-primary-strong: #315b65;
  --session-primary-soft: #edf3f4;
  --session-surface: #fff;
  --session-surface-subtle: #f7f9fa;
  --session-text: #20252d;
  --session-muted: #647080;
  --session-border: #dce2e6;
  --session-radius-lg: 0.75rem;
  --session-radius-md: 0.5rem;
  --session-space-1: 0.5rem;
  --session-space-2: 1rem;
  --session-space-3: 1.5rem;
  --session-space-4: 2rem;
  width: min(88vw, 70rem);
  margin: 0 auto;
  padding: clamp(3rem, 8vh, 5.5rem) 0 3rem;
  color: var(--session-text);
}

.page-heading {
  margin-bottom: 1.55rem;
}

.page-heading h1 {
  margin: 0;
  font-size: clamp(1.65rem, 3vw, 2.05rem);
  line-height: 1.1;
  letter-spacing: -0.025em;
}

.page-heading p {
  margin: 0.45rem 0 0;
  color: var(--session-muted);
  font-size: 0.92rem;
  line-height: 1.5;
}

.session-state {
  padding: 2rem;
  border: 1px solid var(--session-border);
  border-radius: var(--session-radius-lg);
  background: var(--session-surface);
  text-align: center;
  color: var(--session-muted);
}

.session-state h2 {
  margin: 0 0 0.4rem;
  color: var(--session-text);
  font-size: 1.05rem;
}

.session-state p {
  margin: 0;
  font-size: 0.9rem;
}

.session-state button {
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  border: 1px solid var(--session-border);
  border-radius: var(--session-radius-md);
  background: var(--session-surface-subtle);
  color: var(--session-primary-strong);
  font-weight: 600;
  cursor: pointer;
}

.action-notice {
  margin: 0 0 1rem;
  padding: 0.8rem 1rem;
  border: 1px solid #b9d0d4;
  border-radius: 0.65rem;
  color: #315b65;
  background: #eff6f7;
  font-size: 0.82rem;
}

.action-notice--error {
  border-color: #e2b9b9;
  color: #8a3b3b;
  background: #fdf1f1;
}

.session-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(18rem, 1fr);
  grid-template-rows: 1fr auto;
  column-gap: var(--session-space-3);
  row-gap: var(--session-space-1);
  align-items: stretch;
}

.session-overview-panel {
  grid-column: 1;
  grid-row: 1 / span 2;
}

.fee-breakdown-panel {
  grid-column: 2;
  grid-row: 1;
}

.session-actions-panel {
  grid-column: 2;
  grid-row: 2;
  align-self: end;
}

@media (max-width: 850px) {
  .session-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }

  .session-overview-panel,
  .fee-breakdown-panel,
  .session-actions-panel {
    grid-column: 1;
  }

  .session-overview-panel {
    grid-row: 1;
  }

  .fee-breakdown-panel {
    grid-row: 2;
  }

  .session-actions-panel {
    grid-row: 3;
  }
}

@media (max-width: 640px) {
  .active-session-page {
    width: min(92vw, 34rem);
    padding: 1.75rem 0 2rem;
  }

  .page-heading {
    margin-bottom: 1.1rem;
  }
}
</style>
