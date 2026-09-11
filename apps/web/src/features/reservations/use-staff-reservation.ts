import { computed, reactive, ref } from 'vue';
import {
  createReservationDateRange,
  offsetReservationDate,
} from './reservation-date';
import {
  RESERVATION_BRANCHES,
  RESERVATION_CLIENTS,
  RESERVATION_TABLES,
} from './reservation-options';
import type {
  ReservationClientOption,
  ReservationDraft,
  ReservationStep,
} from './reservation-types';

const createInitialDraft = (today = new Date()): ReservationDraft => ({
  client: {
    clientSearch: '',
    clientId: null,
    branchId: RESERVATION_BRANCHES[0]?.id ?? '',
    firstName: '',
    lastName: '',
    nickname: '',
    phone: '',
  },
  date: offsetReservationDate(today, 1),
  startTime: '09:00',
  endTime: '12:00',
  partySize: 5,
  tableId: null,
});

/** Owns the temporary four-step UI state until reservation APIs are available. */
export const useStaffReservation = () => {
  const currentStep = ref<ReservationStep>(1);
  const isConfirmed = ref(false);
  const dateRange = createReservationDateRange();
  const draft = reactive(createInitialDraft());

  const selectedTable = computed(() =>
    RESERVATION_TABLES.find((table) => table.id === draft.tableId),
  );
  const selectedBranchName = computed(
    () =>
      RESERVATION_BRANCHES.find((branch) => branch.id === draft.client.branchId)
        ?.name ?? '',
  );

  const selectClient = (client: ReservationClientOption) => {
    draft.client.clientId = client.id;
    draft.client.clientSearch = client.displayName;
    draft.client.firstName = client.firstName;
    draft.client.lastName = client.lastName;
    draft.client.nickname = client.nickname;
    draft.client.phone = client.phone;
  };

  const selectTable = (tableId: number) => {
    draft.tableId = tableId;
  };

  const continueToTimeParty = () => {
    currentStep.value = 2;
  };

  const continueToTable = () => {
    // Availability will be refreshed here when the reservation API is connected.
    draft.tableId = null;
    currentStep.value = 3;
  };

  const continueToConfirmation = () => {
    if (selectedTable.value) {
      currentStep.value = 4;
    }
  };

  const returnToClient = () => {
    currentStep.value = 1;
  };

  const returnToTimeParty = () => {
    currentStep.value = 2;
  };

  const returnToTable = () => {
    currentStep.value = 3;
  };

  const confirmReservation = () => {
    // Final submission remains disabled until the backend endpoint is implemented.
    isConfirmed.value = true;
  };

  const reviewReservation = () => {
    isConfirmed.value = false;
  };

  return {
    branches: RESERVATION_BRANCHES,
    clients: RESERVATION_CLIENTS,
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
    tables: RESERVATION_TABLES,
  };
};
