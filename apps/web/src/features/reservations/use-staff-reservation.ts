import { computed, reactive, ref, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import {
  createReservationDateRange,
  offsetReservationDate,
} from './reservation-date';
import { branchesQueryOptions } from '../../queries/games';
import {
  currentUserQueryOptions,
  hasStaffAccess,
} from '../../queries/current-user';
import {
  createReservationRequest,
  reservationClientsQueryOptions,
  reservationAvailabilityQueryOptions,
} from '../../queries/reservations';
import type {
  ReservationClientOption,
  ReservationDraft,
  ReservationStep,
} from './reservation-types';

const createInitialDraft = (today = new Date()): ReservationDraft => ({
  client: {
    clientSearch: '',
    clientId: null,
    branchId: '',
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

/** Owns the four-step staff reservation flow and its server-backed state. */
export const useStaffReservation = () => {
  const currentStep = ref<ReservationStep>(1);
  const isConfirmed = ref(false);
  const isSubmitting = ref(false);
  const submissionError = ref<string | null>(null);
  const dateRange = createReservationDateRange();
  const draft = reactive(createInitialDraft());
  const currentUser = useQuery(currentUserQueryOptions());
  const branchQuery = useQuery(branchesQueryOptions());
  const clientsQuery = useQuery(
    computed(() =>
      reservationClientsQueryOptions(
        hasStaffAccess(currentUser.data.value?.roles ?? []),
      ),
    ),
  );
  watch(
    () => branchQuery.data.value,
    (loaded) => {
      if (draft.client.branchId === '' && loaded?.[0]) {
        draft.client.branchId = loaded[0].name;
      }
    },
    { immediate: true },
  );
  const selectedBranchName = computed(
    () =>
      branchQuery.data.value?.find(
        (branch) => branch.name === draft.client.branchId,
      )?.name ?? draft.client.branchId,
  );

  const availabilityQuery = useQuery(
    computed(() =>
      reservationAvailabilityQueryOptions({
        branch: selectedBranchName.value,
        date: draft.date,
        startTime: draft.startTime,
        endTime: draft.endTime,
        partySize: draft.partySize,
      }),
    ),
  );

  const availableTables = computed(() =>
    (availabilityQuery.data.value?.tables ?? []).map((table) => ({
      id: table.id,
      seats: table.capacity,
      reserved: !table.available,
    })),
  );

  const selectedTable = computed(() =>
    availableTables.value.find((table) => table.id === draft.tableId),
  );

  const selectClient = (client: ReservationClientOption) => {
    draft.client.clientId = client.id;
    draft.client.clientSearch = client.displayName;
    draft.client.firstName = client.firstName ?? '';
    draft.client.lastName = client.lastName ?? '';
    draft.client.nickname = client.nickname;
    draft.client.phone = client.phone ?? '';
  };

  const selectTable = (tableId: number) => {
    draft.tableId = tableId;
  };

  const continueToTimeParty = () => {
    currentStep.value = 2;
  };

  const continueToTable = () => {
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

  const confirmReservation = async () => {
    if (!selectedTable.value) return;
    isSubmitting.value = true;
    submissionError.value = null;
    try {
      await createReservationRequest({
        branch: selectedBranchName.value,
        date: draft.date,
        startTime: draft.startTime,
        endTime: draft.endTime,
        partySize: draft.partySize,
        tableId: selectedTable.value.id,
        customerName:
          `${draft.client.firstName} ${draft.client.lastName}`.trim(),
        phoneNumber: draft.client.phone,
        ...(draft.client.clientId
          ? { clientSubject: draft.client.clientId }
          : {}),
      });
      isConfirmed.value = true;
    } catch (error) {
      submissionError.value =
        error instanceof Error ? error.message : 'Reservation failed.';
    } finally {
      isSubmitting.value = false;
    }
  };

  const reviewReservation = () => {
    isConfirmed.value = false;
  };

  return {
    branches: computed(() =>
      (branchQuery.data.value ?? []).map((branch) => ({
        id: branch.name,
        name: branch.name,
      })),
    ),
    clients: computed(() =>
      (clientsQuery.data.value?.items ?? []).map((client) => ({
        id: client.subject,
        displayName: client.displayName,
        firstName: client.firstName ?? '',
        lastName: client.lastName ?? '',
        nickname: '',
        phone: client.phone ?? '',
      })),
    ),
    clientsPending: clientsQuery.isPending,
    clientsError: clientsQuery.error,
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
    tables: availableTables,
    availabilityError: availabilityQuery.error,
    availabilityPending: availabilityQuery.isPending,
    isSubmitting,
    submissionError,
  };
};
