<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import type { ReservationStep } from '../../features/reservations/reservation-types';

const props = defineProps<{
  currentStep: ReservationStep;
}>();

const { t } = useI18n();
const steps = [
  { number: 1 as const, label: 'reservation.steps.client' },
  { number: 2 as const, label: 'reservation.steps.timeParty' },
  { number: 3 as const, label: 'reservation.steps.table' },
  { number: 4 as const, label: 'reservation.steps.confirm' },
];
</script>

<template>
  <ol class="reservation-stepper" :aria-label="t('reservation.progress')">
    <li
      v-for="step in steps"
      :key="step.number"
      :class="{
        'step--active': props.currentStep === step.number,
        'step--complete': props.currentStep > step.number,
      }"
      :aria-current="props.currentStep === step.number ? 'step' : undefined"
    >
      <span class="step-track" aria-hidden="true" />
      <span class="step-number">
        <svg
          v-if="props.currentStep > step.number"
          viewBox="0 0 20 20"
          aria-hidden="true"
        >
          <path d="m5 10 3 3 7-7" />
        </svg>
        <span v-else>{{ step.number }}</span>
      </span>
      <span class="step-label">{{ t(step.label) }}</span>
    </li>
  </ol>
</template>

<style scoped>
.reservation-stepper {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  padding: 1.35rem 2.35rem 1.25rem;
  list-style: none;
}

li {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 0.55rem;
  color: #647080;
  font-size: 0.88rem;
}

.step-track {
  position: absolute;
  top: 1rem;
  right: 50%;
  width: 100%;
  height: 2px;
  background: #e2e6e9;
  transform: translateY(-50%);
}

li:first-child .step-track {
  display: none;
}

.step-number {
  position: relative;
  z-index: 1;
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border: 1px solid #91a0b3;
  border-radius: 50%;
  background: #f8fafb;
  font-weight: 600;
}

.step-number svg {
  width: 1.1rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.2;
}

.step--active,
.step--complete {
  color: #315b65;
}

.step--active .step-number,
.step--complete .step-number {
  border-color: #497883;
  color: #fff;
  background: #497883;
}

.step--complete .step-track {
  background: #497883;
}

.step--active .step-label {
  font-weight: 700;
}

@media (max-width: 640px) {
  .reservation-stepper {
    padding: 1.1rem 0.75rem;
  }

  .step-label {
    font-size: 0.72rem;
  }
}
</style>
