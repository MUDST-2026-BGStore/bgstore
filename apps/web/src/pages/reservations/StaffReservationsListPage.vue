<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useQuery } from '@tanstack/vue-query';
import OwnerPortalLayout from '../../layouts/OwnerPortalLayout.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiPagination from '../../components/ui/UiPagination.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import { staffReservationsQueryOptions } from '../../queries/reservations';
import type { ReservationResponse } from '../../generated/api/types.gen';

const { t, locale } = useI18n();

const statusFilters = [
  'All',
  'Reserved',
  'CheckedIn',
  'Completed',
  'Cancelled',
] as const;
type StatusFilter = (typeof statusFilters)[number];

const statusFilter = ref<StatusFilter>('All');
const currentPage = ref(1);
const pageSize = 10;

const statusOptions = computed(() =>
  statusFilters.map((value) => ({
    value,
    label:
      value === 'All'
        ? t('staffReservations.filterAll')
        : t(`sessions.status.${value}`),
  })),
);

const reservations = useQuery(
  computed(() =>
    staffReservationsQueryOptions({
      status: statusFilter.value === 'All' ? undefined : statusFilter.value,
      page: currentPage.value,
      pageSize,
    }),
  ),
);

const rows = computed(() => reservations.data.value?.items ?? []);
const data = computed(() => reservations.data.value);
const total = computed(() => data.value?.total ?? 0);

const selectStatus = (value: string) => {
  statusFilter.value = value as StatusFilter;
  currentPage.value = 1;
};

const badgeTone = (session: ReservationResponse) => {
  switch (session.status) {
    case 'Reserved':
      return 'warning';
    case 'CheckedIn':
      return 'info';
    case 'Cancelled':
      return 'danger';
    case 'Completed':
    default:
      return 'success';
  }
};

const isCheckedIn = (session: ReservationResponse) =>
  session.status === 'CheckedIn';

/** The booking date is an ISO string for new records and free text for old ones. */
const bookingDate = (value: string) => {
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(
        parsed,
      );
};

const rangeLabel = computed(() =>
  t('staffReservations.showing', {
    from: Math.min((currentPage.value - 1) * pageSize + 1, total.value),
    to: Math.min(currentPage.value * pageSize, total.value),
    total: total.value,
  }),
);
</script>

<template>
  <OwnerPortalLayout active="reservations">
    <div
      class="staff-page-content flex w-full flex-col items-start gap-6 px-10 pt-8 pb-10"
      data-page="staff-reservations"
    >
      <div class="flex w-full items-start justify-between gap-4">
        <div>
          <h1
            class="staff-page-title text-[28px] leading-[34px] font-semibold tracking-[-0.084px] text-ink"
          >
            {{ t('staffReservations.title') }}
          </h1>
          <p class="mt-1 text-[14px] leading-[22px] text-ink-muted">
            {{ t('staffReservations.description') }}
          </p>
        </div>
        <UiButton to="/staff/reservations/new" data-testid="new-reservation">
          {{ t('staffReservations.new') }}
        </UiButton>
      </div>

      <div
        v-if="reservations.isError.value"
        class="w-full rounded-lg border border-danger-border bg-surface p-6 text-danger-fg"
        role="alert"
      >
        <p>{{ t('staffReservations.loadError') }}</p>
        <UiButton
          class="mt-3"
          variant="outline"
          size="sm"
          @click="reservations.refetch()"
        >
          {{ t('staffReservations.retry') }}
        </UiButton>
      </div>
      <div
        v-else-if="reservations.isPending.value"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
        aria-live="polite"
      >
        {{ t('staffReservations.loading') }}
      </div>
      <div
        v-else-if="rows.length === 0"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
        data-testid="staff-reservations-empty"
      >
        {{ t('staffReservations.empty') }}
      </div>
      <template v-else>
        <div
          class="flex w-full items-center gap-3"
          data-testid="staff-reservations-filter"
        >
          <label
            for="staff-reservations-status"
            class="text-[13px] font-medium text-ink-secondary"
          >
            {{ t('staffReservations.statusFilter') }}
          </label>
          <UiSelect
            id="staff-reservations-status"
            :model-value="statusFilter"
            :options="statusOptions"
            class="w-56"
            @update:model-value="selectStatus"
          />
          <p class="ml-auto text-[13px] text-ink-muted" aria-live="polite">
            {{ rangeLabel }}
          </p>
        </div>

        <div
          class="staff-table-shell w-full overflow-x-auto rounded-lg border border-line bg-surface"
        >
          <table class="w-full min-w-[860px] table-fixed border-collapse">
            <thead
              class="border-b border-line bg-surface-sunken text-[13px] leading-5 font-medium text-ink-secondary"
            >
              <tr class="h-11">
                <th scope="col" class="px-4 text-left font-medium">
                  {{ t('sessions.columns.client') }}
                </th>
                <th scope="col" class="w-[160px] px-4 text-left font-medium">
                  {{ t('sessions.columns.table') }}
                </th>
                <th scope="col" class="w-[110px] px-4 text-center font-medium">
                  {{ t('sessions.columns.party') }}
                </th>
                <th scope="col" class="w-[210px] px-4 text-left font-medium">
                  {{ t('sessions.columns.booking') }}
                </th>
                <th scope="col" class="w-[150px] px-4 text-center font-medium">
                  {{ t('sessions.columns.status') }}
                </th>
              </tr>
            </thead>
            <tbody class="text-[14px] leading-[22px] text-ink">
              <tr
                v-for="reservation in rows"
                :key="reservation.id"
                class="h-[52px] border-b border-line last:border-0"
                data-testid="staff-reservation-row"
              >
                <td class="truncate px-4">
                  {{ reservation.customerName }}
                  <span
                    v-if="isCheckedIn(reservation)"
                    class="text-ink-secondary"
                  >
                    · {{ reservation.phoneNumber }}
                  </span>
                </td>
                <td class="truncate px-4">{{ reservation.tableName }}</td>
                <td class="px-4 text-center">
                  {{ t('sessions.seats', { count: reservation.partySize }) }}
                </td>
                <td class="px-4">
                  {{ bookingDate(reservation.date) }} ·
                  {{ reservation.timeSlot }}
                </td>
                <td class="px-4 text-center">
                  <UiBadge :tone="badgeTone(reservation)">
                    {{ t(`sessions.status.${reservation.status}`) }}
                  </UiBadge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          v-if="total > 0"
          class="flex w-full flex-wrap items-center justify-between gap-4 py-2"
        >
          <p class="text-sm text-ink-muted" aria-live="polite">
            {{ rangeLabel }}
          </p>
          <UiPagination v-model="currentPage" :total-pages="data?.totalPages ?? 1" />
        </div>
      </template>
    </div>
  </OwnerPortalLayout>
</template>
