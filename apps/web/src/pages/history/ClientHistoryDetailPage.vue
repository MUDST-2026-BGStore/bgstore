<script setup lang="ts">
import { computed, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import type { ReservationStatus } from '../../generated/api/types.gen';
import {
  cancelReservationRequest,
  reservationQueryOptions,
} from '../../queries/reservations';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const queryClient = useQueryClient();

const reservationId = computed(() => String(route.params.id || ''));

const reservationQuery = useQuery(
  computed(() => reservationQueryOptions(reservationId.value)),
);
const reservation = computed(() => reservationQuery.data.value ?? null);
const isLoading = computed(() => reservationQuery.isPending.value);
const cancelError = ref<string | null>(null);
const errorMessage = computed(
  () =>
    cancelError.value ??
    (reservationQuery.isError.value ? 'Reservation not found' : null),
);

// Modal state
const isModalOpen = ref(false);
const isCancelling = ref(false);

function handleBack() {
  const query: Record<string, string> = {};
  if (route.query.tab) {
    query.tab = String(route.query.tab);
  }
  if (route.query.page) {
    query.page = String(route.query.page);
  }
  void router.push({ name: 'history', query });
}

function openCancelModal() {
  if (reservation.value?.status === 'Reserved' && reservation.value.canCancel) {
    isModalOpen.value = true;
  }
}

function closeCancelModal() {
  if (!isCancelling.value) {
    isModalOpen.value = false;
  }
}

async function confirmCancel() {
  if (!reservation.value) return;
  isCancelling.value = true;
  cancelError.value = null;
  try {
    const updated = await cancelReservationRequest(reservation.value.id);
    queryClient.setQueryData(
      reservationQueryOptions(reservation.value.id).queryKey,
      updated,
    );
    isModalOpen.value = false;
  } catch (err) {
    cancelError.value =
      err instanceof Error ? err.message : 'Failed to cancel reservation';
  } finally {
    isCancelling.value = false;
  }
}

const canCancel = computed(() => {
  return (
    reservation.value?.status === 'Reserved' && reservation.value.canCancel
  );
});

function statusToneClass(status?: ReservationStatus) {
  switch (status) {
    case 'Reserved':
      return 'bg-[#eff6ff] text-[#1d4ed8] border-[#bfdbfe]';
    case 'Cancelled':
      return 'bg-[#fef2f2] text-[#dc2626] border-[#fecaca]';
    case 'Completed':
    default:
      return 'bg-[#eef8f0] text-[#276635] border-[#b0dcb9]';
  }
}
</script>

<template>
  <div class="min-h-screen w-full bg-canvas">
    <div class="flex w-full flex-col items-start gap-6 px-8 py-8">
      <!-- Title -->
      <h1 class="text-[28px] font-bold text-ink">
        {{ t('history.detailTitle') }}
      </h1>

      <!-- Loading State -->
      <div
        v-if="isLoading"
        data-testid="detail-skeleton"
        class="flex w-full animate-pulse flex-col rounded-xl border border-line bg-surface p-8 shadow-xs"
      >
        <div class="grid grid-cols-1 gap-8 md:grid-cols-3">
          <div class="flex flex-col items-center gap-4">
            <div class="size-48 rounded-xl bg-gray-200" />
            <div class="h-5 w-24 rounded bg-gray-200" />
            <div class="h-4 w-36 rounded bg-gray-100" />
          </div>
          <div class="col-span-2 flex flex-col gap-4">
            <div class="h-6 w-20 rounded bg-gray-200" />
            <div class="grid grid-cols-2 gap-4">
              <div
                v-for="i in 8"
                :key="i"
                class="h-10 rounded-md bg-gray-100"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Error State -->
      <div
        v-else-if="errorMessage || !reservation"
        data-testid="detail-error"
        class="flex w-full flex-col items-center justify-center rounded-xl border border-line bg-surface py-16 text-center"
      >
        <p class="text-[16px] font-medium text-ink-secondary">
          {{ errorMessage || 'Reservation not found' }}
        </p>
        <button
          type="button"
          class="mt-4 rounded-md border border-line px-4 py-2 text-[14px] text-ink hover:bg-gray-50 cursor-pointer"
          @click="handleBack"
        >
          {{ t('history.actions.back') }}
        </button>
      </div>

      <!-- Detail Card (2-column container) -->
      <div
        v-else
        data-testid="reservation-detail-card"
        class="flex w-full flex-col rounded-xl border border-line bg-surface p-8 shadow-xs"
      >
        <div class="grid grid-cols-1 gap-10 lg:grid-cols-12">
          <!-- Left Column: Table Info & Thumbnail -->
          <div class="flex flex-col items-start lg:col-span-4">
            <div
              class="flex size-48 w-full max-w-[220px] items-center justify-center rounded-xl border border-line bg-surface-sunken"
              data-testid="table-thumbnail"
            >
              <svg
                class="size-16 text-gray-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="1.5"
                  d="M4 6h16M4 10h16M4 14h16M4 18h16"
                />
              </svg>
            </div>

            <h2 class="mt-4 text-[17px] font-bold text-ink">
              {{ reservation.tableName }}
            </h2>
            <p class="text-[13px] text-ink-secondary">
              {{
                t('history.tableMeta', {
                  seats: reservation.seats,
                  rate: reservation.ratePerHour,
                })
              }}
            </p>

            <div class="mt-4">
              <span
                data-testid="detail-status-badge"
                class="inline-flex items-center justify-center rounded-full border px-3 py-1 text-[12px] font-medium"
                :class="statusToneClass(reservation.status)"
              >
                {{ t(`history.status.${reservation.status.toLowerCase()}`) }}
              </span>
            </div>
          </div>

          <!-- Right Column: Details Breakdown Form -->
          <div class="flex flex-col lg:col-span-8">
            <h3 class="mb-4 text-[16px] font-semibold text-ink">
              {{ t('history.detailsHeading') }}
            </h3>

            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <!-- Name -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-name"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.name') }}
                </label>
                <input
                  id="field-name"
                  type="text"
                  readonly
                  :value="reservation.customerName"
                  data-testid="field-name"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Phone Number -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-phone"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.phoneNumber') }}
                </label>
                <input
                  id="field-phone"
                  type="text"
                  readonly
                  :value="reservation.phoneNumber"
                  data-testid="field-phone"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Date -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-date"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.date') }}
                </label>
                <input
                  id="field-date"
                  type="text"
                  readonly
                  :value="reservation.date"
                  data-testid="field-date"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Time -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-time"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.time') }}
                </label>
                <input
                  id="field-time"
                  type="text"
                  readonly
                  :value="reservation.timeSlot"
                  data-testid="field-time"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Check-in Time -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-checkin"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.checkInTime') }}
                </label>
                <input
                  id="field-checkin"
                  type="text"
                  readonly
                  :value="reservation.checkInTime"
                  data-testid="field-checkin"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Actual Check-out -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-checkout"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.actualCheckOut') }}
                </label>
                <input
                  id="field-checkout"
                  type="text"
                  readonly
                  :value="reservation.actualCheckOut"
                  data-testid="field-checkout"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>

              <!-- Overtime (with green badge/indicator) -->
              <div class="flex flex-col gap-1.5">
                <span
                  id="field-overtime-label"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.overtime') }}
                </span>
                <div
                  data-testid="field-overtime"
                  aria-labelledby="field-overtime-label"
                  class="flex h-10 items-center rounded-md border border-success-border bg-success-bg px-3.5 text-[14px] font-medium text-success-fg"
                >
                  {{
                    t('history.overtimeValue', {
                      minutes: reservation.overtimeMinutes,
                    })
                  }}
                </div>
              </div>

              <!-- Total Price -->
              <div class="flex flex-col gap-1.5">
                <label
                  for="field-price"
                  class="text-[13px] font-medium text-ink-secondary"
                >
                  {{ t('history.fields.price') }}
                </label>
                <input
                  id="field-price"
                  type="text"
                  readonly
                  :value="
                    t('history.priceValue', { amount: reservation.totalPrice })
                  "
                  data-testid="field-price"
                  class="h-10 rounded-md border border-line bg-surface-sunken px-3.5 text-[14px] text-ink outline-none"
                />
              </div>
            </div>

            <!-- Action Buttons at bottom right -->
            <div class="mt-8 flex items-center justify-end gap-3">
              <button
                type="button"
                data-testid="back-button"
                class="inline-flex h-10 items-center justify-center rounded-md border border-line px-6 text-[14px] font-medium text-ink-secondary hover:bg-gray-50 transition-colors cursor-pointer"
                @click="handleBack"
              >
                {{ t('history.actions.back') }}
              </button>

              <button
                v-if="canCancel"
                type="button"
                data-testid="cancel-button"
                class="inline-flex h-10 items-center justify-center rounded-md bg-[#991b1b] px-6 text-[14px] font-medium text-white transition-colors hover:bg-[#7f1d1d] cursor-pointer"
                @click="openCancelModal"
              >
                {{ t('history.actions.cancel') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Cancel Confirmation Modal -->
      <div
        v-if="isModalOpen"
        data-testid="cancel-modal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
        @click.self="closeCancelModal"
      >
        <div
          role="dialog"
          aria-modal="true"
          class="flex w-full max-w-md flex-col rounded-xl border border-line bg-surface p-6 shadow-lg"
        >
          <h4 class="text-[18px] font-bold text-ink">
            {{ t('history.modal.title') }}
          </h4>
          <p class="mt-2 text-[14px] text-ink-secondary">
            {{ t('history.modal.prompt') }}
          </p>

          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              data-testid="modal-keep-button"
              :disabled="isCancelling"
              class="inline-flex h-9 items-center justify-center rounded-md border border-line px-4 text-[13px] font-medium text-ink-secondary hover:bg-gray-50 transition-colors cursor-pointer"
              @click="closeCancelModal"
            >
              {{ t('history.actions.keepReservation') }}
            </button>

            <button
              type="button"
              data-testid="modal-confirm-cancel-button"
              :disabled="isCancelling"
              class="inline-flex h-9 items-center justify-center rounded-md bg-[#991b1b] px-4 text-[13px] font-medium text-white transition-colors hover:bg-[#7f1d1d] disabled:opacity-50 cursor-pointer"
              @click="confirmCancel"
            >
              <span v-if="isCancelling">...</span>
              <span v-else>{{ t('history.actions.confirmCancel') }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
