<script setup lang="ts">
import { computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import ReservationConfirmationStep from '../components/reservations/ReservationConfirmationStep.vue';
import ReservationStepper from '../components/reservations/ReservationStepper.vue';
import ReservationTableStep from '../components/reservations/ReservationTableStep.vue';
import ReservationTimePartyStep from '../components/reservations/ReservationTimePartyStep.vue';
import StaffReservationClientStep from '../components/reservations/StaffReservationClientStep.vue';
import OwnerPortalLayout from '../layouts/OwnerPortalLayout.vue';
import { useStaffReservation } from '../features/reservations/use-staff-reservation';
import {
  currentUserQueryOptions,
  hasStaffAccess,
} from '../queries/current-user';

const { t } = useI18n();
const route = useRoute();
// The branch directory links here with the branch the client picked.
const requestedBranch =
  typeof route.query.branch === 'string' ? route.query.branch : '';
const currentUser = useQuery(currentUserQueryOptions());
// Protected routes are only rendered after the session resolves. Defaulting to
// the staff form keeps the component deterministic while that cache is cold.
const clientMode = computed(() =>
  currentUser.data.value
    ? !hasStaffAccess(currentUser.data.value.roles)
    : false,
);
// Staff reach this page from the sidebar's "Reservations" shortcut, so it
// keeps that chrome; a client booking their own table keeps the focused,
// chrome-free flow.
const wrapper = computed(() => (clientMode.value ? 'div' : OwnerPortalLayout));
const wrapperProps = computed(() =>
  clientMode.value ? {} : { active: 'reservations' as const },
);
const {
  branches,
  clients,
  clientsPending,
  clientsError,
  confirmReservation,
  continueToConfirmation,
  continueToTable,
  continueToTimeParty,
  currentStep,
  dateRange,
  draft,
  isConfirmed,
  reviewReservation,
  returnToClient,
  returnToTable,
  returnToTimeParty,
  selectClient,
  selectedBranchName,
  selectedTable,
  selectTable,
  tables,
  availabilityError,
  availabilityPending,
  isSubmitting,
  submissionError,
} = useStaffReservation(requestedBranch);

watch(
  () => currentUser.data.value,
  (user) => {
    if (!user || hasStaffAccess(user.roles)) return;
    if (!draft.client.firstName) draft.client.firstName = user.firstName;
    if (!draft.client.lastName) draft.client.lastName = user.lastName;
    if (!draft.client.phone && user.clientProfile?.phone) {
      draft.client.phone = user.clientProfile.phone;
    }
  },
  { immediate: true },
);
</script>

<template>
  <component :is="wrapper" v-bind="wrapperProps">
    <section class="reservation-page" aria-labelledby="reservation-title">
      <h1 id="reservation-title" class="visually-hidden">
        {{ t(clientMode ? 'reservation.title' : 'reservation.staffTitle') }}
      </h1>

      <div v-if="isConfirmed" class="reservation-success" role="status">
        <span aria-hidden="true">✓</span>
        <h2>{{ t('reservation.readyTitle') }}</h2>
        <p>{{ t('reservation.readyDescription') }}</p>
        <button type="button" @click="reviewReservation">
          {{ t('reservation.reviewReservation') }}
        </button>
      </div>

      <div v-else class="reservation-card">
        <ReservationStepper :current-step="currentStep" />

        <StaffReservationClientStep
          v-if="currentStep === 1"
          :client-search="draft.client.clientSearch"
          :branch-id="draft.client.branchId"
          :first-name="draft.client.firstName"
          :last-name="draft.client.lastName"
          :nickname="draft.client.nickname"
          :phone="draft.client.phone"
          :clients="clients"
          :clients-pending="clientsPending"
          :clients-error="clientsError"
          :branches="branches"
          :client-mode="clientMode"
          @update:client-search="draft.client.clientSearch = $event"
          @update:branch-id="draft.client.branchId = $event"
          @update:first-name="draft.client.firstName = $event"
          @update:last-name="draft.client.lastName = $event"
          @update:nickname="draft.client.nickname = $event"
          @update:phone="draft.client.phone = $event"
          @select-client="selectClient"
          @next="continueToTimeParty"
        />

        <ReservationTimePartyStep
          v-else-if="currentStep === 2"
          :date="draft.date"
          :start-time="draft.startTime"
          :end-time="draft.endTime"
          :party-size="draft.partySize"
          :min-date="dateRange.min"
          :max-date="dateRange.max"
          @update:date="draft.date = $event"
          @update:start-time="draft.startTime = $event"
          @update:end-time="draft.endTime = $event"
          @update:party-size="draft.partySize = $event"
          @back="returnToClient"
          @next="continueToTable"
        />

        <ReservationTableStep
          v-else-if="currentStep === 3"
          :tables="tables"
          :party-size="draft.partySize"
          :selected-table-id="draft.tableId"
          :pending="availabilityPending"
          :error="availabilityError"
          @select="selectTable"
          @back="returnToTimeParty"
          @next="continueToConfirmation"
        />

        <ReservationConfirmationStep
          v-else-if="selectedTable"
          :draft="draft"
          :table="selectedTable"
          :branch-name="selectedBranchName"
          :submitting="isSubmitting"
          :error="submissionError"
          @back="returnToTable"
          @confirm="confirmReservation"
        />
      </div>
    </section>
  </component>
</template>

<style scoped>
.reservation-page {
  --reservation-green: #497883;
  width: min(100%, 70rem);
  margin: 0 auto;
  padding: clamp(3rem, 8vh, 5.5rem) clamp(1.25rem, 3vw, 3rem) 3rem;
  color: #20252d;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.reservation-card {
  overflow: visible;
  border: 1px solid #dce2e6;
  border-radius: 0.8rem;
  background: #fff;
  box-shadow: 0 0.75rem 2.4rem rgb(32 37 45 / 4%);
}

.reservation-card > :first-child {
  border-bottom: 1px solid #dce2e6;
  border-radius: 0.75rem 0.75rem 0 0;
  background: #f8fafb;
}

.reservation-success {
  display: grid;
  min-height: 26rem;
  place-items: center;
  align-content: center;
  padding: 3rem 1.5rem;
  border: 1px solid #dce2e6;
  border-radius: 0.8rem;
  background: #fff;
  text-align: center;
}

.reservation-success > span {
  display: grid;
  width: 3.4rem;
  height: 3.4rem;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--reservation-green);
  font-size: 1.5rem;
}

.reservation-success h2 {
  margin: 1rem 0 0;
  font-size: 1.4rem;
}

.reservation-success p {
  max-width: 31rem;
  margin: 0.55rem 0 1.4rem;
  color: #647080;
  line-height: 1.55;
}

.reservation-success button {
  min-height: 2.75rem;
  padding: 0.65rem 1.15rem;
  border: 1px solid var(--reservation-green);
  border-radius: 0.55rem;
  color: var(--reservation-green);
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.reservation-success button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .reservation-page {
    width: min(100%, 34rem);
    padding: 1.75rem clamp(1rem, 4vw, 1.5rem) 2rem;
  }
}
</style>
