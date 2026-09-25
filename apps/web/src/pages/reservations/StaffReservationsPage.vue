<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import StaffLayout from '../../layouts/StaffLayout.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import { branchesQueryOptions } from '../../queries/games';
import { checkInReservationRequest } from '../../queries/play-sessions';
import {
  staffReservationsPageSize,
  staffReservationsQueryOptions,
} from '../../queries/reservations';
import {
  currentUserQueryOptions,
  hasManagerAccess,
} from '../../queries/current-user';

const { t, locale } = useI18n();
const queryClient = useQueryClient();
const currentUser = useQuery(currentUserQueryOptions());

const tabs = [
  'All',
  'Reserved',
  'CheckedIn',
  'Completed',
  'Cancelled',
] as const;
type StaffTab = (typeof tabs)[number];
const statusOf = (tab: StaffTab) => (tab === 'All' ? undefined : tab);

const activeTab = ref<StaffTab>('All');
const branchFilter = ref('');
const currentPage = ref(1);

const isManager = computed(() =>
  currentUser.data.value
    ? hasManagerAccess(currentUser.data.value.roles)
    : false,
);
const branches = useQuery(branchesQueryOptions());
const branchOptions = computed(() => [
  { value: '', label: t('staff.reservations.branchAll') },
  ...(branches.data.value ?? []).map((branch) => ({
    value: branch.name,
    label: branch.name,
  })),
]);

const reservations = useQuery(
  computed(() =>
    staffReservationsQueryOptions({
      status: statusOf(activeTab.value),
      branch: branchFilter.value === '' ? undefined : branchFilter.value,
      page: currentPage.value,
      pageSize: staffReservationsPageSize,
    }),
  ),
);
const rows = computed(() => reservations.data.value?.items ?? []);
const total = computed(() => reservations.data.value?.total ?? 0);
const totalPages = computed(() => reservations.data.value?.totalPages ?? 1);
const shownRange = computed(() => {
  const size = staffReservationsPageSize;
  return `${Math.min((currentPage.value - 1) * size + 1, total.value)}–${Math.min(
    currentPage.value * size,
    total.value,
  )}`;
});

const selectTab = (tab: StaffTab) => {
  activeTab.value = tab;
  currentPage.value = 1;
};

watch(branchFilter, () => {
  currentPage.value = 1;
});

const bookingDate = (value: string) => {
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(
        parsed,
      );
};

const actionError = ref<string | null>(null);
const checkIn = useMutation({
  mutationFn: (reservationId: string) =>
    checkInReservationRequest(reservationId),
  onSuccess: () => {
    actionError.value = null;
    void queryClient.invalidateQueries({ queryKey: ['reservations'] });
    void queryClient.invalidateQueries({ queryKey: ['play-sessions'] });
  },
  onError: () => {
    actionError.value = t('staff.reservations.checkInFailed');
  },
});
</script>

<template>
  <StaffLayout active="reservations">
    <div
      class="staff-page-content flex w-full flex-col items-start gap-6 px-10 pt-8 pb-10"
    >
      <div class="flex w-full flex-wrap items-start justify-between gap-4">
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
        <UiButton :href="'/staff/reservations/new'">
          {{ t('staff.reservations.new') }}
        </UiButton>
      </div>

      <div
        role="tablist"
        :aria-label="t('staff.reservations.filterLabel')"
        class="flex items-center gap-8 border-b border-line pb-1 text-[15px]"
      >
        <button
          v-for="tab in tabs"
          :key="tab"
          role="tab"
          type="button"
          :aria-selected="activeTab === tab"
          class="relative cursor-pointer pb-3 transition-colors"
          :class="
            activeTab === tab
              ? 'font-semibold text-ink'
              : 'text-ink-secondary hover:text-ink'
          "
          @click="selectTab(tab)"
        >
          {{ t(`staff.reservations.tabs.${tab.toLowerCase()}`) }}
          <span
            v-if="activeTab === tab"
            class="absolute bottom-0 left-0 h-[2px] w-full bg-ink transition-all"
          />
        </button>
      </div>

      <div v-if="isManager" class="w-full max-w-xs">
        <label
          class="mb-1 block text-[13px] text-ink-secondary"
          for="staff-reservation-branch"
        >
          {{ t('staff.reservations.branchLabel') }}
        </label>
        <UiSelect
          id="staff-reservation-branch"
          v-model="branchFilter"
          :options="branchOptions"
        />
      </div>

      <div
        v-if="actionError"
        class="w-full rounded-lg border border-danger-border bg-surface p-4 text-danger-fg"
        role="alert"
      >
        {{ actionError }}
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
        class="flex w-full flex-col items-center justify-center rounded-lg border border-dashed border-line bg-surface py-16 text-center"
      >
        <p class="text-[15px] font-medium text-ink-secondary">
          {{ t('staff.reservations.empty') }}
        </p>
      </div>

      <div
        v-else
        class="staff-table-shell w-full overflow-x-auto rounded-lg border border-line bg-surface"
      >
        <table class="w-full min-w-[860px] table-fixed border-collapse">
          <thead
            class="border-b border-line bg-surface-sunken text-[13px] leading-5 font-medium text-ink-secondary"
          >
            <tr class="h-11">
              <th scope="col" class="w-[150px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.booking') }}
              </th>
              <th scope="col" class="w-[160px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.client') }}
              </th>
              <th scope="col" class="w-[130px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.date') }}
              </th>
              <th scope="col" class="w-[110px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.slot') }}
              </th>
              <th scope="col" class="w-[90px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.party') }}
              </th>
              <th scope="col" class="w-[150px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.table') }}
              </th>
              <th scope="col" class="w-[150px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.branch') }}
              </th>
              <th scope="col" class="w-[130px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.status') }}
              </th>
              <th scope="col" class="w-[120px] px-4 text-left font-medium">
                {{ t('staff.reservations.columns.actions') }}
              </th>
            </tr>
          </thead>
          <tbody class="text-[14px] leading-[22px] text-ink">
            <tr
              v-for="item in rows"
              :key="item.id"
              class="h-[52px] border-b border-line last:border-0"
            >
              <td class="truncate px-4">{{ item.title }}</td>
              <td class="truncate px-4">{{ item.customerName }}</td>
              <td class="truncate px-4">{{ bookingDate(item.date) }}</td>
              <td class="truncate px-4">{{ item.timeSlot }}</td>
              <td class="truncate px-4">{{ item.partySize }}</td>
              <td class="truncate px-4">{{ item.tableName }}</td>
              <td class="truncate px-4">{{ item.branchName ?? '—' }}</td>
              <td class="px-4">
                <UiBadge
                  :tone="
                    item.status === 'CheckedIn'
                      ? 'info'
                      : item.status === 'Reserved'
                        ? 'warning'
                        : 'neutral'
                  "
                >
                  {{ t(`sessions.status.${item.status}`) }}
                </UiBadge>
              </td>
              <td class="px-4">
                <UiButton
                  v-if="item.status === 'Reserved'"
                  size="sm"
                  :disabled="checkIn.isPending.value"
                  @click="checkIn.mutate(item.id)"
                >
                  {{ t('staff.reservations.checkIn') }}
                </UiButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav
        v-if="!reservations.isPending.value && total > 0"
        class="flex w-full flex-wrap items-center justify-between gap-4 py-2"
        :aria-label="t('staff.reservations.paginationLabel')"
      >
        <p class="text-[13px] text-ink-secondary">
          {{ t('staff.reservations.showing', { shown: shownRange, total }) }}
        </p>
        <div class="flex items-center gap-2">
          <UiButton
            variant="outline"
            size="sm"
            :disabled="currentPage <= 1"
            @click="currentPage -= 1"
          >
            {{ t('staff.reservations.previous') }}
          </UiButton>
          <UiButton
            variant="outline"
            size="sm"
            :disabled="currentPage >= totalPages"
            @click="currentPage += 1"
          >
            {{ t('staff.reservations.next') }}
          </UiButton>
        </div>
      </nav>
    </div>
  </StaffLayout>
</template>
