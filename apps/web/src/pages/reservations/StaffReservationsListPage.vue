<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useQuery } from '@tanstack/vue-query';
import OwnerPortalLayout from '../../layouts/OwnerPortalLayout.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiPagination from '../../components/ui/UiPagination.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import {
  staffReservationsPageSize,
  staffReservationsQueryOptions,
} from '../../queries/reservations';
import type { BadgeTone, SelectOption } from '../../components/ui/types';
import type { ReservationStatus } from '../../generated/api/types.gen';

const { t, locale } = useI18n();

const statusFilter = ref<'' | ReservationStatus>('');
const dateFilter = ref('');
const page = ref(1);

const query = computed(() => ({
  status: statusFilter.value === '' ? undefined : statusFilter.value,
  date: dateFilter.value === '' ? undefined : dateFilter.value,
  page: page.value,
  pageSize: staffReservationsPageSize,
}));

const reservations = useQuery(
  computed(() => staffReservationsQueryOptions(query.value)),
);
const rows = computed(() => reservations.data.value?.items ?? []);
const total = computed(() => reservations.data.value?.total ?? 0);
const totalPages = computed(() =>
  Math.max(1, reservations.data.value?.totalPages ?? 1),
);
const showingFrom = computed(() =>
  total.value === 0 ? 0 : (page.value - 1) * staffReservationsPageSize + 1,
);
const showingTo = computed(() =>
  Math.min(total.value, page.value * staffReservationsPageSize),
);

watch([statusFilter, dateFilter], () => {
  page.value = 1;
});

const statusOptions: SelectOption[] = (
  ['Reserved', 'CheckedIn', 'Completed', 'Cancelled'] as ReservationStatus[]
).map((value) => ({ value, label: t(`staff.reservations.status.${value}`) }));

const statusTone: Record<ReservationStatus, BadgeTone> = {
  Reserved: 'warning',
  CheckedIn: 'info',
  Completed: 'success',
  Cancelled: 'danger',
};

/** The booking date is an ISO string for new records and free text for old ones. */
const bookingDate = (value: string) => {
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(
        parsed,
      );
};
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
            {{ t('staff.reservations.title') }}
          </h1>
          <p class="mt-1 text-[14px] leading-[22px] text-ink-muted">
            {{ t('staff.reservations.description') }}
          </p>
        </div>
        <UiButton to="/staff/reservations/new">
          {{ t('staff.reservations.newWalkIn') }}
        </UiButton>
      </div>

      <div class="flex w-full flex-wrap items-center gap-3">
        <div
          class="flex h-10 items-center gap-2 rounded-md border border-line bg-surface px-3 focus-within:border-primary"
        >
          <input
            id="staff-reservation-date"
            v-model="dateFilter"
            type="date"
            :aria-label="t('staff.reservations.dateFilter')"
            class="min-w-0 flex-1 bg-transparent text-[14px] leading-[22px] text-ink outline-none"
          />
        </div>
        <div class="w-[180px]">
          <UiSelect
            id="staff-reservation-status"
            v-model="statusFilter"
            :options="statusOptions"
            placeholder-selectable
            :placeholder="t('staff.reservations.allStatuses')"
            :aria-label="t('staff.reservations.statusFilter')"
          />
        </div>
      </div>

      <div
        v-if="reservations.isError.value"
        class="w-full rounded-lg border border-danger-border bg-surface p-6 text-danger-fg"
        role="alert"
      >
        <p>{{ t('staff.reservations.loadError') }}</p>
        <UiButton
          class="mt-3"
          variant="outline"
          size="sm"
          @click="reservations.refetch()"
        >
          {{ t('staff.reservations.retry') }}
        </UiButton>
      </div>
      <div
        v-else-if="reservations.isPending.value"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
        aria-live="polite"
      >
        {{ t('staff.reservations.loading') }}
      </div>
      <div
        v-else-if="rows.length === 0"
        class="w-full rounded-lg border border-dashed border-line bg-surface p-8 text-ink-muted"
        data-testid="staff-reservations-empty"
      >
        {{ t('staff.reservations.empty') }}
      </div>
      <template v-else>
        <div
          class="staff-table-shell w-full overflow-x-auto rounded-lg border border-line bg-surface"
        >
          <table class="w-full min-w-[1000px] table-fixed border-collapse">
            <thead
              class="border-b border-line bg-surface-sunken text-[13px] leading-5 font-medium text-ink-secondary"
            >
              <tr class="h-11">
                <th scope="col" class="w-[250px] px-4 text-left font-medium">
                  {{ t('staff.reservations.columns.date') }}
                </th>
                <th scope="col" class="px-4 text-left font-medium">
                  {{ t('staff.reservations.columns.client') }}
                </th>
                <th scope="col" class="w-[110px] px-4 text-center font-medium">
                  {{ t('staff.reservations.columns.party') }}
                </th>
                <th scope="col" class="w-[150px] px-4 text-left font-medium">
                  {{ t('staff.reservations.columns.table') }}
                </th>
                <th scope="col" class="w-[170px] px-4 text-left font-medium">
                  {{ t('staff.reservations.columns.branch') }}
                </th>
                <th scope="col" class="w-[140px] px-4 text-center font-medium">
                  {{ t('staff.reservations.columns.status') }}
                </th>
              </tr>
            </thead>
            <tbody class="text-[14px] leading-[22px] text-ink">
              <tr
                v-for="reservation in rows"
                :key="reservation.id"
                data-testid="staff-reservation-row"
                class="h-[52px] border-b border-line last:border-0"
              >
                <td class="px-4">
                  {{ bookingDate(reservation.date) }} ·
                  {{ reservation.timeSlot }}
                </td>
                <td class="truncate px-4">
                  {{ reservation.customerName }}
                  <span
                    class="block truncate text-[12px] leading-4 text-ink-muted"
                  >
                    {{ reservation.phoneNumber }}
                  </span>
                </td>
                <td class="px-4 text-center">
                  {{
                    t('staff.reservations.seats', {
                      count: reservation.partySize,
                    })
                  }}
                </td>
                <td class="truncate px-4">{{ reservation.tableName }}</td>
                <td class="truncate px-4">{{ reservation.branchName }}</td>
                <td class="px-4 text-center">
                  <UiBadge :tone="statusTone[reservation.status]">
                    {{ t(`staff.reservations.status.${reservation.status}`) }}
                  </UiBadge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          class="flex w-full flex-wrap items-center justify-between gap-4"
          data-testid="staff-reservations-pagination"
        >
          <p class="text-[13px] leading-5 text-ink-muted">
            {{
              t('staff.reservations.showing', {
                from: showingFrom,
                to: showingTo,
                total,
              })
            }}
          </p>
          <UiPagination v-model="page" :total-pages="totalPages" />
        </div>
      </template>
    </div>
  </OwnerPortalLayout>
</template>
