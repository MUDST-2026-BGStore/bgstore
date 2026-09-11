<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import type { ReservationStatus } from '../../generated/api/types.gen';
import {
  reservationsPageSize,
  reservationsQueryOptions,
} from '../../queries/reservations';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

const tabs = ['All', 'Reserved', 'Completed', 'Cancelled'] as const;
type HistoryTab = (typeof tabs)[number];

function tabFromQuery(value: unknown): HistoryTab {
  const match = tabs.find(
    (tab) => tab.toLowerCase() === String(value ?? '').toLowerCase(),
  );
  return match ?? 'All';
}

function pageFromQuery(value: unknown): number {
  const page = Number.parseInt(String(value ?? ''), 10);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

const activeTab = ref<HistoryTab>(tabFromQuery(route.query.tab));
const currentPage = ref(pageFromQuery(route.query.page));

const reservations = useQuery(
  computed(() =>
    reservationsQueryOptions({
      status:
        activeTab.value === 'All'
          ? undefined
          : (activeTab.value as ReservationStatus),
      page: currentPage.value,
      pageSize: reservationsPageSize,
    }),
  ),
);

const data = computed(() => reservations.data.value);
const paginatedData = computed(
  () =>
    data.value ?? {
      items: [],
      total: 0,
      totalPages: 1,
      page: currentPage.value,
      pageSize: reservationsPageSize,
    },
);
const isLoading = computed(() => reservations.isPending.value);

function selectTab(tab: HistoryTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  currentPage.value = 1;
  void updateQueryParams();
}

function goToPage(page: number) {
  if (
    page < 1 ||
    page > paginatedData.value.totalPages ||
    page === currentPage.value
  ) {
    return;
  }
  currentPage.value = page;
  void updateQueryParams();
}

async function updateQueryParams() {
  await router.replace({
    query: {
      ...route.query,
      tab: activeTab.value.toLowerCase(),
      page: currentPage.value.toString(),
    },
  });
}

function statusToneClass(status: ReservationStatus) {
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

// Generate pagination numbers matching TableManagementView: e.g. [1, 2, 3, 'ellipsis', 9]
const pageNumbers = computed(() => {
  const total = paginatedData.value.totalPages;
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  if (currentPage.value <= 3) return [1, 2, 3, 'ellipsis', total];
  if (currentPage.value >= total - 2)
    return [1, 'ellipsis', total - 2, total - 1, total];
  return [
    1,
    'ellipsis',
    currentPage.value - 1,
    currentPage.value,
    currentPage.value + 1,
    'ellipsis',
    total,
  ];
});

watch(
  () => route.query,
  (query) => {
    activeTab.value = tabFromQuery(query.tab);
    currentPage.value = pageFromQuery(query.page);
  },
);
</script>

<template>
  <main
    class="m-0 min-h-screen w-full bg-white p-0 font-[Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,'Segoe_UI',Roboto,Helvetica,Arial,sans-serif] text-[#1e293b] antialiased [box-sizing:border-box]"
  >
    <div class="w-full px-8 py-6">
      <!-- Title -->
      <div>
        <h1 class="text-2xl font-bold tracking-tight text-gray-900">
          {{ t('history.title') }}
        </h1>
      </div>

      <!-- Filter Tabs -->
      <div
        role="tablist"
        aria-label="Reservation status filter"
        class="mt-5 flex items-center gap-8 border-b border-gray-200 pb-1 text-[15px]"
      >
        <button
          v-for="tab in tabs"
          :key="tab"
          role="tab"
          type="button"
          :aria-selected="activeTab === tab"
          :data-testid="`tab-${tab.toLowerCase()}`"
          class="relative pb-3 transition-colors cursor-pointer"
          :class="
            activeTab === tab
              ? 'font-semibold text-ink'
              : 'text-ink-secondary hover:text-ink'
          "
          @click="selectTab(tab)"
        >
          {{ t(`history.tabs.${tab.toLowerCase()}`) }}
          <span
            v-if="activeTab === tab"
            class="absolute bottom-0 left-0 h-[2px] w-full bg-ink transition-all"
          />
        </button>
      </div>

      <!-- Content Area -->
      <div class="mt-4 flex flex-col gap-4 min-h-[300px]">
        <!-- Skeleton Loading State -->
        <template v-if="isLoading">
          <div
            v-for="i in 4"
            :key="i"
            data-testid="history-skeleton"
            class="animate-pulse flex items-center justify-between rounded-lg border border-line bg-surface px-6 py-5 shadow-xs"
          >
            <div class="flex flex-col gap-2">
              <div class="h-5 w-28 rounded bg-gray-200" />
              <div class="h-4 w-72 rounded bg-gray-100" />
            </div>
            <div class="flex items-center gap-6">
              <div class="h-7 w-20 rounded-full bg-gray-200" />
              <div class="h-4 w-10 rounded bg-gray-200" />
            </div>
          </div>
        </template>

        <!-- Empty State -->
        <template v-else-if="paginatedData.items.length === 0">
          <div
            data-testid="empty-state"
            class="flex w-full flex-col items-center justify-center rounded-lg border border-dashed border-line bg-surface py-16 text-center"
          >
            <p class="text-[15px] font-medium text-ink-secondary">
              {{ t('history.empty') }}
            </p>
          </div>
        </template>

        <!-- Reservation Cards List -->
        <template v-else>
          <article
            v-for="item in paginatedData.items"
            :key="item.id"
            data-testid="reservation-card"
            class="flex items-center justify-between rounded-lg border border-line bg-surface px-6 py-4 transition-shadow hover:shadow-xs"
          >
            <div class="flex flex-col gap-1">
              <h2 class="text-[15px] font-semibold text-ink">
                {{ item.title }}
              </h2>
              <p class="text-[13px] text-ink-secondary">
                {{ item.date }} · {{ item.timeSlot }} ·
                {{ item.partySize }} People · {{ item.tableName }}
              </p>
            </div>

            <div class="flex items-center gap-6">
              <span
                data-testid="status-badge"
                class="inline-flex items-center justify-center rounded-full border px-3 py-0.5 text-[12px] font-medium"
                :class="statusToneClass(item.status)"
              >
                {{ t(`history.status.${item.status.toLowerCase()}`) }}
              </span>

              <router-link
                :to="{
                  name: 'history-detail',
                  params: { id: item.id },
                  query: {
                    tab: activeTab.toLowerCase(),
                    page: currentPage.toString(),
                  },
                }"
                data-testid="view-button"
                class="text-[14px] font-medium text-ink-secondary hover:text-ink transition-colors cursor-pointer"
              >
                {{ t('history.actions.view') }}
              </router-link>
            </div>
          </article>
        </template>
      </div>

      <!-- Pagination -->
      <div
        v-if="!isLoading && paginatedData.total > 0"
        class="mt-6 flex flex-wrap items-center justify-between gap-4 py-2"
        aria-label="Table pagination"
      >
        <div class="text-sm font-normal text-slate-500">
          Showing
          {{
            Math.min(
              (currentPage - 1) * reservationsPageSize + 1,
              paginatedData.total,
            )
          }}-{{
            Math.min(currentPage * reservationsPageSize, paginatedData.total)
          }}
          of
          {{ paginatedData.total }}
        </div>

        <div class="flex items-center gap-6">
          <button
            type="button"
            aria-label="Previous page"
            class="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100/90 text-slate-700 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-30 cursor-pointer"
            :disabled="currentPage === 1"
            @click="goToPage(currentPage - 1)"
          >
            <svg
              class="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M19 12H5M12 19l-7-7 7-7" />
            </svg>
          </button>

          <div class="flex items-center gap-6 text-sm">
            <template
              v-for="(page, index) in pageNumbers"
              :key="`page-${String(page)}-${index}`"
            >
              <button
                v-if="page !== 'ellipsis'"
                type="button"
                :data-testid="`page-${page}`"
                :class="[
                  'relative flex flex-col items-center justify-center px-1 py-1 transition cursor-pointer',
                  page === currentPage
                    ? 'font-bold text-slate-900'
                    : 'font-normal text-slate-500 hover:text-slate-900',
                ]"
                :aria-current="page === currentPage ? 'page' : undefined"
                @click="goToPage(Number(page))"
              >
                <span>{{ page }}</span>
                <span
                  v-if="page === currentPage"
                  class="absolute -bottom-1 h-[2.5px] w-4 rounded-full bg-slate-900"
                  aria-hidden="true"
                />
              </button>
              <span v-else class="px-0.5 text-sm font-normal text-slate-500">
                ...
              </span>
            </template>
          </div>

          <button
            type="button"
            aria-label="Next page"
            class="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100/90 text-slate-700 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-30 cursor-pointer"
            :disabled="currentPage >= paginatedData.totalPages"
            @click="goToPage(currentPage + 1)"
          >
            <svg
              class="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
