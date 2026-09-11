<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type {
  ReservationDraft,
  ReservationTableOption,
} from '../../features/reservations/reservation-types';

const props = defineProps<{
  draft: ReservationDraft;
  table: ReservationTableOption;
  branchName: string;
}>();

const emit = defineEmits<{
  back: [];
  confirm: [];
}>();

const { t, locale } = useI18n();
const formattedDate = computed(() => {
  const [year, month, day] = props.draft.date.split('-').map(Number);
  return new Intl.DateTimeFormat(locale.value, {
    dateStyle: 'full',
  }).format(new Date(year, month - 1, day, 12));
});
</script>

<template>
  <dl class="confirmation-list">
    <div class="summary-item">
      <dt>{{ t('reservation.clientName') }}</dt>
      <dd>
        {{ draft.client.firstName }} {{ draft.client.lastName }}
        <span v-if="draft.client.nickname">({{ draft.client.nickname }})</span>
      </dd>
    </div>
    <div class="summary-item">
      <dt>{{ t('reservation.phoneNumber') }}</dt>
      <dd>{{ draft.client.phone }}</dd>
    </div>
    <div class="summary-item summary-item--wide">
      <dt>{{ t('reservation.branch') }}</dt>
      <dd>{{ branchName }}</dd>
    </div>
    <div class="summary-item summary-item--wide">
      <dt>{{ t('reservation.date') }}</dt>
      <dd>{{ formattedDate }}</dd>
    </div>
    <div class="summary-item">
      <dt>{{ t('reservation.startTime') }}</dt>
      <dd>{{ draft.startTime }}</dd>
    </div>
    <div class="summary-item">
      <dt>{{ t('reservation.endTime') }}</dt>
      <dd>{{ draft.endTime }}</dd>
    </div>
    <div class="summary-item">
      <dt>{{ t('reservation.table') }}</dt>
      <dd>{{ t('reservation.tableNumber', { number: table.id }) }}</dd>
    </div>
    <div class="summary-item">
      <dt>{{ t('reservation.party') }}</dt>
      <dd>{{ t('reservation.guestCount', { count: draft.partySize }) }}</dd>
    </div>

    <div class="step-actions summary-item--wide">
      <button class="secondary-button" type="button" @click="emit('back')">
        {{ t('reservation.back') }}
      </button>
      <button class="primary-button" type="button" @click="emit('confirm')">
        {{ t('reservation.confirm') }}
      </button>
    </div>
  </dl>
</template>

<style scoped>
.confirmation-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.45rem 2rem;
  margin: 0;
  padding: 1.8rem 2.35rem 2.1rem;
}

.summary-item {
  display: grid;
  gap: 0.55rem;
}

.summary-item--wide {
  grid-column: 1 / -1;
}

dt {
  font-size: 0.9rem;
}

dd {
  min-height: 2.9rem;
  margin: 0;
  padding: 0.72rem 0.85rem;
  border: 1px solid #dce2e6;
  border-radius: 0.55rem;
  color: #35404a;
  background: #f8fafb;
  font-size: 0.9rem;
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.15rem;
}

.primary-button,
.secondary-button {
  min-width: 8.7rem;
  min-height: 2.8rem;
  padding: 0.65rem 1.15rem;
  border-radius: 0.55rem;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #497883;
  color: #fff;
  background: #497883;
}

.secondary-button {
  border: 1px solid #91a0b3;
  color: #20252d;
  background: #fff;
}

button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .confirmation-list {
    grid-template-columns: 1fr;
    padding: 1.4rem 1.15rem 1.6rem;
  }

  .summary-item--wide {
    grid-column: auto;
  }

  .step-actions {
    gap: 0.75rem;
  }

  .primary-button,
  .secondary-button {
    min-width: 0;
    width: 50%;
  }
}
</style>
