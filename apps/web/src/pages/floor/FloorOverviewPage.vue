<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import StaffLayout from '../../layouts/StaffLayout.vue';
import StatCard from '../../components/ui/StatCard.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiPagination from '../../components/ui/UiPagination.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import type { BadgeTone } from '../../components/ui/types';
import statAvailable from '../../assets/icons/stat-available.svg';
import statOccupied from '../../assets/icons/stat-in-use.svg';
import statReserved from '../../assets/icons/stat-reserved.svg';
import { floorOverviewQueryOptions } from '../../queries/floor-overview';
import type {
  FloorTableResponse,
  TableStatus,
} from '../../generated/api/types.gen';
import { formatReservedSlot } from './display';

/** The design shows five rows a page ("Showing 1–5 of 42"). */
const pageSize = 5;

const { t } = useI18n();

const search = ref('');
const status = ref<TableStatus | ''>('');
const page = ref(1);

// Page 3 of the old filter is rarely page 3 of the new one.
watch([search, status], () => {
  page.value = 1;
});

const overview = useQuery(
  computed(() =>
    floorOverviewQueryOptions({
      status: status.value || undefined,
      search: search.value.trim() || undefined,
      page: page.value,
      pageSize,
    }),
  ),
);

const data = computed(() => overview.data.value);
const rows = computed(() => data.value?.items ?? []);

const stats = computed(() => [
  {
    tone: 'success' as const,
    icon: statAvailable,
    label: t('tables.status.Available'),
    value: data.value?.counts.available,
  },
  {
    tone: 'warning' as const,
    icon: statOccupied,
    label: t('tables.status.Occupied'),
    value: data.value?.counts.occupied,
  },
  {
    tone: 'info' as const,
    icon: statReserved,
    label: t('tables.status.Reserved'),
    value: data.value?.counts.reserved,
  },
]);

const statusOptions: TableStatus[] = [
  'Available',
  'Occupied',
  'Reserved',
  'Unavailable',
];

// Fixed by the Badge component's domain mapping in the design system.
const statusTone: Record<TableStatus, BadgeTone> = {
  Available: 'success',
  Occupied: 'warning',
  Reserved: 'info',
  Unavailable: 'neutral',
};

const range = computed(() => {
  const answer = data.value;
  if (!answer || answer.items.length === 0) {
    return undefined;
  }
  const from = (answer.page - 1) * answer.pageSize + 1;
  return t('floor.showing', {
    from,
    to: from + answer.items.length - 1,
    total: answer.total,
  });
});

function laterSlots(table: FloorTableResponse): string {
  return table.reservedSlots.slice(1).map(formatReservedSlot).join('\n');
}
</script>

<template>
  <StaffLayout active="home">
    <div
      class="flex w-full flex-col items-start gap-6 px-10 pt-8 pb-10"
      data-page="floor-overview"
    >
      <h1
        class="w-full text-[28px] leading-[34px] font-semibold tracking-[-0.084px] text-ink"
      >
        {{ t('floor.title') }}
      </h1>

      <div class="flex w-full flex-col gap-6 md:flex-row">
        <StatCard
          v-for="stat in stats"
          :key="stat.tone"
          data-testid="floor-stat"
          :tone="stat.tone"
          :icon="stat.icon"
          :label="stat.label"
          :value="stat.value === undefined ? '–' : String(stat.value)"
        />
      </div>

      <div class="flex w-full flex-wrap items-center gap-4">
        <label for="floor-search" class="sr-only">{{
          t('floor.search')
        }}</label>
        <UiTextInput
          id="floor-search"
          v-model="search"
          class="w-full sm:w-[360px]"
          :placeholder="t('floor.search')"
          search
        />
        <label for="floor-status" class="sr-only">
          {{ t('floor.statusFilter') }}
        </label>
        <UiSelect
          id="floor-status"
          v-model="status"
          class="w-full sm:w-[200px]"
          :placeholder="t('tables.allStatuses')"
          placeholder-selectable
          :options="
            statusOptions.map((value) => ({
              value,
              label: t(`tables.status.${value}`),
            }))
          "
        />
      </div>

      <div
        v-if="overview.isError.value"
        class="w-full rounded-lg border border-danger-border bg-surface p-6 text-danger-fg"
        role="alert"
      >
        <p>{{ t('floor.loadError') }}</p>
        <UiButton
          class="mt-3"
          variant="outline"
          size="sm"
          @click="overview.refetch()"
        >
          {{ t('floor.retry') }}
        </UiButton>
      </div>
      <div
        v-else-if="overview.isPending.value"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
        aria-live="polite"
      >
        {{ t('floor.loading') }}
      </div>
      <div
        v-else-if="rows.length === 0"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
      >
        {{ t('floor.empty') }}
      </div>
      <template v-else>
        <div
          class="w-full overflow-x-auto rounded-lg border border-line bg-surface"
        >
          <table class="w-full min-w-[900px] table-fixed border-collapse">
            <colgroup>
              <col class="w-[90px]" />
              <col class="w-[160px]" />
              <col class="w-[120px]" />
              <col class="w-[140px]" />
              <col class="w-[170px]" />
              <col class="w-[220px]" />
              <col />
            </colgroup>
            <thead
              class="border-b border-line bg-surface-sunken text-[13px] leading-5 font-medium text-ink-secondary"
            >
              <tr class="h-11">
                <th scope="col" class="px-4 text-left font-medium">
                  {{ t('floor.columns.id') }}
                </th>
                <th scope="col" class="px-4 text-left font-medium">
                  {{ t('floor.columns.name') }}
                </th>
                <th scope="col" class="px-4 text-center font-medium">
                  {{ t('floor.columns.capacity') }}
                </th>
                <th scope="col" class="px-4 text-center font-medium">
                  {{ t('floor.columns.status') }}
                </th>
                <th scope="col" class="px-4 text-left font-medium">
                  {{ t('floor.columns.type') }}
                </th>
                <th scope="col" class="px-4 text-right font-medium">
                  {{ t('floor.columns.reservedSlots') }}
                </th>
                <th aria-hidden="true" />
              </tr>
            </thead>
            <tbody class="text-[14px] leading-[22px] text-ink">
              <tr
                v-for="table in rows"
                :key="table.id"
                class="h-[52px] border-b border-line last:border-0"
              >
                <td class="px-4">{{ table.id }}</td>
                <td class="truncate px-4">{{ table.name }}</td>
                <td class="px-4 text-center">
                  {{ t('tables.seats', { count: table.capacity }) }}
                </td>
                <td class="px-4 text-center">
                  <UiBadge :tone="statusTone[table.status]">
                    {{ t(`tables.status.${table.status}`) }}
                  </UiBadge>
                </td>
                <td class="px-4">{{ t(`floor.type.${table.shape}`) }}</td>
                <td class="px-4 text-right whitespace-nowrap">
                  <template v-if="table.reservedSlots.length > 0">
                    {{ formatReservedSlot(table.reservedSlots[0]) }}
                    <span
                      v-if="table.reservedSlots.length > 1"
                      class="text-[12px] text-ink-muted"
                      :title="laterSlots(table)"
                    >
                      {{
                        t('floor.moreSlots', {
                          count: table.reservedSlots.length - 1,
                        })
                      }}
                    </span>
                  </template>
                  <span v-else :title="t('floor.noSlots')">—</span>
                </td>
                <td aria-hidden="true" />
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex w-full flex-wrap items-center justify-between gap-4">
          <p class="text-[13px] leading-5 text-ink-muted">{{ range }}</p>
          <UiPagination v-model="page" :total-pages="data?.totalPages ?? 1" />
        </div>
      </template>
    </div>
  </StaffLayout>
</template>
