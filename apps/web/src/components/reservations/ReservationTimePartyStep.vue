<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  PARTY_SIZE_OPTIONS,
  TIME_OPTIONS,
} from '../../features/reservations/reservation-options';
import ReservationDatePicker from './ReservationDatePicker.vue';

const props = defineProps<{
  date: string;
  startTime: string;
  endTime: string;
  partySize: number;
  minDate: string;
  maxDate: string;
}>();

const emit = defineEmits<{
  back: [];
  next: [];
  'update:date': [value: string];
  'update:startTime': [value: string];
  'update:endTime': [value: string];
  'update:partySize': [value: number];
}>();

const { t } = useI18n();
const isTimeRangeValid = computed(
  () => props.startTime.length > 0 && props.endTime > props.startTime,
);
const canContinue = computed(
  () =>
    props.date >= props.minDate &&
    props.date <= props.maxDate &&
    isTimeRangeValid.value,
);

const setPartySize = (size: number) => {
  emit('update:partySize', Math.min(13, Math.max(1, size)));
};
</script>

<template>
  <form class="step-form" @submit.prevent="emit('next')">
    <ReservationDatePicker
      class="field--wide"
      :model-value="date"
      :min-date="minDate"
      :max-date="maxDate"
      @update:model-value="emit('update:date', $event)"
    />

    <label class="field">
      <span>{{ t('reservation.startTime') }} <b aria-hidden="true">*</b></span>
      <select
        :value="startTime"
        required
        @change="
          emit('update:startTime', ($event.target as HTMLSelectElement).value)
        "
      >
        <option v-for="time in TIME_OPTIONS" :key="time" :value="time">
          {{ time }}
        </option>
      </select>
    </label>

    <label class="field">
      <span>{{ t('reservation.endTime') }} <b aria-hidden="true">*</b></span>
      <select
        :value="endTime"
        required
        @change="
          emit('update:endTime', ($event.target as HTMLSelectElement).value)
        "
      >
        <option v-for="time in TIME_OPTIONS" :key="time" :value="time">
          {{ time }}
        </option>
      </select>
    </label>

    <fieldset class="party-fieldset field--wide">
      <legend>{{ t('reservation.party') }}</legend>
      <div class="party-adjuster">
        <button
          type="button"
          class="circle-button"
          :aria-label="t('reservation.decreaseParty')"
          :disabled="partySize <= 1"
          @click="setPartySize(partySize - 1)"
        >
          −
        </button>
        <strong aria-live="polite">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="9" cy="8" r="3" />
            <path d="M3.5 19c.4-4 2.2-6 5.5-6s5.1 2 5.5 6" />
            <circle cx="17" cy="9" r="2.2" />
            <path d="M15.5 14c2.9-.5 4.7 1.2 5 4" />
          </svg>
          {{ partySize }}
        </strong>
        <button
          type="button"
          class="circle-button"
          :aria-label="t('reservation.increaseParty')"
          :disabled="partySize >= 13"
          @click="setPartySize(partySize + 1)"
        >
          +
        </button>
      </div>
      <div class="party-options" :aria-label="t('reservation.partySize')">
        <button
          v-for="option in PARTY_SIZE_OPTIONS"
          :key="option"
          type="button"
          class="party-option"
          :class="{ 'party-option--selected': partySize === option }"
          :aria-pressed="partySize === option"
          @click="setPartySize(option)"
        >
          {{ option }}
        </button>
      </div>
    </fieldset>

    <p v-if="!isTimeRangeValid" class="validation-message" role="alert">
      {{ t('reservation.invalidTime') }}
    </p>

    <div class="step-actions field--wide">
      <button class="secondary-button" type="button" @click="emit('back')">
        {{ t('reservation.back') }}
      </button>
      <button class="primary-button" type="submit" :disabled="!canContinue">
        {{ t('reservation.next') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.step-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.45rem 2rem;
  padding: 1.8rem 2.35rem 2.1rem;
}

.field--wide,
.validation-message {
  grid-column: 1 / -1;
}

.field {
  display: grid;
  gap: 0.55rem;
  min-width: 0;
  color: #20252d;
  font-size: 0.9rem;
}

.field b {
  color: #8d1d24;
}

select {
  width: 100%;
  min-height: 2.9rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid #dce2e6;
  border-radius: 0.55rem;
  color: #20252d;
  background: #fff;
  font: inherit;
}

select:focus {
  border-color: #497883;
  outline: none;
  box-shadow: 0 0 0 3px rgb(73 120 131 / 16%);
}

.party-fieldset {
  display: grid;
  gap: 0.9rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.party-adjuster {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.1rem;
}

.party-adjuster strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 2.8rem;
  text-align: center;
}

.party-adjuster svg {
  width: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.circle-button,
.party-option {
  display: grid;
  width: 2.65rem;
  height: 2.65rem;
  place-items: center;
  border: 1px solid #91a0b3;
  border-radius: 50%;
  color: #20252d;
  background: #fff;
  font: inherit;
  cursor: pointer;
}

.party-options {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.65rem;
}

.party-option--selected {
  border-color: #497883;
  color: #fff;
  background: #497883;
}

button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.validation-message {
  margin: -0.65rem 0 0;
  color: #a3343b;
  font-size: 0.82rem;
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

@media (max-width: 640px) {
  .step-form {
    grid-template-columns: 1fr;
    padding: 1.4rem 1.15rem 1.6rem;
  }

  .field--wide,
  .validation-message {
    grid-column: auto;
  }

  .party-options {
    gap: 0.45rem;
  }

  .circle-button,
  .party-option {
    width: 2.4rem;
    height: 2.4rem;
  }

  .primary-button,
  .secondary-button {
    width: 50%;
  }
}
</style>
