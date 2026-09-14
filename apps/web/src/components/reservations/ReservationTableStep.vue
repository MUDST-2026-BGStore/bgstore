<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { ReservationTableOption } from '../../features/reservations/reservation-types';

const props = defineProps<{
  tables: ReservationTableOption[];
  partySize: number;
  selectedTableId: number | null;
  pending?: boolean;
  error?: unknown;
}>();

const emit = defineEmits<{
  back: [];
  next: [];
  select: [tableId: number];
}>();

const { t } = useI18n();
const selectedTable = computed(() =>
  props.tables.find((table) => table.id === props.selectedTableId),
);
const isUnavailable = (table: ReservationTableOption) =>
  table.reserved || table.seats < props.partySize;
</script>

<template>
  <div class="table-step">
    <section aria-labelledby="select-table-title">
      <h2 id="select-table-title">{{ t('reservation.selectTable') }}</h2>
      <p v-if="pending" role="status">{{ t('reservation.loadingTables') }}</p>
      <p v-else-if="error" role="alert">{{ t('reservation.tablesError') }}</p>
      <div class="table-grid">
        <button
          v-for="table in tables"
          :key="table.id"
          type="button"
          class="table-option"
          :class="{
            'table-option--selected': selectedTableId === table.id,
            'table-option--unavailable': isUnavailable(table),
          }"
          :disabled="isUnavailable(table)"
          :aria-pressed="selectedTableId === table.id"
          @click="emit('select', table.id)"
        >
          <strong>{{
            t('reservation.tableNumber', { number: table.id })
          }}</strong>
          <span v-if="table.reserved">{{ t('reservation.reserved') }}</span>
          <span v-else-if="table.seats < partySize">
            {{ t('reservation.tooSmall') }}
          </span>
          <span v-else>{{
            t('reservation.seatCount', { count: table.seats })
          }}</span>
        </button>
      </div>
    </section>

    <aside class="selected-card" aria-live="polite">
      <span>{{ t('reservation.selectedTable') }}</span>
      <template v-if="selectedTable">
        <strong>
          {{ t('reservation.tableNumber', { number: selectedTable.id }) }}
        </strong>
        <p>
          {{
            t('reservation.tableFits', {
              seats: selectedTable.seats,
              partySize,
            })
          }}
        </p>
      </template>
      <p v-else>{{ t('reservation.selectTableHint') }}</p>
    </aside>

    <div class="step-actions">
      <button class="secondary-button" type="button" @click="emit('back')">
        {{ t('reservation.back') }}
      </button>
      <button
        class="primary-button"
        type="button"
        :disabled="selectedTableId === null"
        @click="emit('next')"
      >
        {{ t('reservation.next') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.table-step {
  display: grid;
  grid-template-columns: minmax(0, 2.4fr) minmax(13rem, 1fr);
  gap: 1.5rem;
  padding: 1.8rem 2.35rem 2.1rem;
}

h2 {
  margin: 0 0 1.15rem;
  font-size: 0.95rem;
  font-weight: 500;
}

.table-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid #dce2e6;
  border-radius: 0.6rem;
}

.table-option {
  display: grid;
  min-height: 4.1rem;
  align-content: center;
  gap: 0.2rem;
  padding: 0.55rem;
  border: 1px solid #91a0b3;
  border-radius: 0.55rem;
  color: #20252d;
  background: #fff;
  font: inherit;
  cursor: pointer;
}

.table-option strong {
  font-size: 0.86rem;
  font-weight: 600;
}

.table-option span {
  color: #647080;
  font-size: 0.75rem;
}

.table-option--selected {
  border-color: #497883;
  color: #fff;
  background: #497883;
}

.table-option--selected span {
  color: #fff;
}

.table-option--unavailable {
  border-color: #e3e6ea;
  color: #647080;
  background: #e7eaee;
  cursor: not-allowed;
}

.selected-card {
  align-self: start;
  min-height: 6.9rem;
  margin-top: 2.3rem;
  padding: 1.1rem;
  border: 1px solid #dce2e6;
  border-radius: 0.6rem;
  background: #f8fafb;
}

.selected-card > span {
  display: block;
  margin-bottom: 0.4rem;
  color: #647080;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
}

.selected-card strong {
  display: block;
  font-size: 1.2rem;
}

.selected-card p {
  margin: 0.3rem 0 0;
  color: #647080;
  font-size: 0.78rem;
  line-height: 1.4;
}

.step-actions {
  display: flex;
  grid-column: 1 / -1;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.25rem;
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

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 980px) {
  .table-step {
    grid-template-columns: 1fr;
  }

  .selected-card {
    margin: 0;
  }
}

@media (max-width: 720px) {
  .table-step {
    padding: 1.4rem 1.15rem 1.6rem;
  }

  .table-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding: 0.9rem;
  }
}

@media (max-width: 480px) {
  .table-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
