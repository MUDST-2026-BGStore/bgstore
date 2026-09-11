<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRouter } from 'vue-router';
import mapPin from '../assets/icons/map-pin.svg';
import searchIcon from '../assets/icons/search.svg';
import { useBranches, type Branch } from '../composables/useBranches';

export interface BranchWithMeta extends Branch {
  phone?: string | null;
  status?: 'ACTIVE' | 'INACTIVE' | string;
  latitude?: number | null;
  longitude?: number | null;
}

const router = useRouter();
const { t } = useI18n();
const { branches, isError, isPending, refetch } = useBranches();

const rawSearchQuery = ref('');
const searchQuery = ref('');
const selectedBranchId = ref<string | null>(null);
const userCoords = ref<{ latitude: number; longitude: number } | null>(null);
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

// AC 4: Graceful Geolocation handling
onMounted(() => {
  if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        userCoords.value = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        };
      },
      () => {
        // Fallback gracefully without tracking unused status
      },
      { timeout: 5000 },
    );
  }
});

watch(rawSearchQuery, (value) => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    searchQuery.value = value.trim().toLowerCase();
  }, 300);
});

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});

// AC 2: Filter by branch name OR address
const filteredBranches = computed<BranchWithMeta[]>(() => {
  const list = branches.value as BranchWithMeta[];
  if (!searchQuery.value) return list;
  return list.filter((branch) => {
    const nameMatch = branch.name.toLowerCase().includes(searchQuery.value);
    const addressMatch = branch.address
      ? branch.address.toLowerCase().includes(searchQuery.value)
      : false;
    return nameMatch || addressMatch;
  });
});

// AC 2: Keep selection in sync without navigating away
watch(
  filteredBranches,
  (list) => {
    if (list.length > 0) {
      const exists = list.some((b) => b.id === selectedBranchId.value);
      if (!exists) {
        selectedBranchId.value = list[0].id;
      }
    } else {
      selectedBranchId.value = null;
    }
  },
  { immediate: true },
);

const selectedBranch = computed<BranchWithMeta | null>(() => {
  if (!selectedBranchId.value) return null;
  return (
    (branches.value as BranchWithMeta[]).find(
      (b) => b.id === selectedBranchId.value,
    ) ?? null
  );
});

function selectBranch(id: string) {
  selectedBranchId.value = id;
}

// AC 6: Check if branch is bookable
function isBranchBookable(branch: BranchWithMeta): boolean {
  return branch.status !== 'INACTIVE';
}

function formatHours(branch: BranchWithMeta): string {
  if (branch.opensAt && branch.closesAt) {
    return `${branch.opensAt} – ${branch.closesAt}`;
  }
  return t('branch.noHours');
}

// AC 1 & 4: Haversine distance calculation
function calculateDistance(branch: BranchWithMeta): string | null {
  if (
    !userCoords.value ||
    branch.latitude == null ||
    branch.longitude == null
  ) {
    return null;
  }
  const R = 6371;
  const dLat = ((branch.latitude - userCoords.value.latitude) * Math.PI) / 180;
  const dLon =
    ((branch.longitude - userCoords.value.longitude) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((userCoords.value.latitude * Math.PI) / 180) *
      Math.cos((branch.latitude * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return (R * c).toFixed(1);
}

// AC 3: Book at this branch
function startReservation(branch: BranchWithMeta) {
  if (!isBranchBookable(branch)) return;
  void router.push({
    path: '/tables',
    query: { branchId: branch.id },
  });
}
</script>

<template>
  <section
    class="w-full bg-[#f8fafc] px-4 py-3 md:px-8"
    aria-labelledby="branch-title"
  >
    <div class="mx-auto max-w-7xl">
      <!-- AC 7: Staff Navigation Bar -->
      <nav
        class="mb-3 flex flex-wrap items-center gap-2 rounded-xl border border-gray-200 bg-white px-3 py-1.5 text-xs md:text-sm shadow-sm"
        aria-label="Staff quick navigation"
      >
        <span class="mr-2 font-semibold text-gray-500"
          >{{ t('navigation.staff') }}:</span
        >
        <RouterLink
          to="/"
          class="rounded-lg px-2.5 py-1 font-medium text-gray-700 hover:bg-gray-100 hover:text-gray-900"
        >
          {{ t('branch.staffNav.dashboard') }}
        </RouterLink>
        <RouterLink
          to="/history"
          class="rounded-lg px-2.5 py-1 font-medium text-gray-700 hover:bg-gray-100 hover:text-gray-900"
        >
          {{ t('branch.staffNav.history') }}
        </RouterLink>
        <RouterLink
          to="/tables"
          class="rounded-lg px-2.5 py-1 font-medium text-gray-700 hover:bg-gray-100 hover:text-gray-900"
        >
          {{ t('branch.staffNav.checkInOut') }}
        </RouterLink>
      </nav>

      <!-- Header Section -->
      <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p
            class="text-[11px] font-semibold uppercase tracking-wider text-[#386671]"
          >
            {{ t('branch.list') }}
          </p>
          <h1
            id="branch-title"
            class="text-lg font-bold text-gray-900 md:text-xl"
          >
            {{ t('branch.management') }}
          </h1>
        </div>
        <span
          v-if="!isPending && !isError"
          class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-semibold text-gray-600"
        >
          {{ filteredBranches.length }} {{ t('branch.list') }}
        </span>
      </div>

      <!-- AC 5: Loading State -->
      <div
        v-if="isPending"
        class="rounded-2xl border border-gray-200 bg-white p-8 text-center text-gray-500 shadow-sm"
        aria-live="polite"
      >
        <div
          class="mx-auto mb-3 size-8 animate-spin rounded-full border-4 border-gray-200 border-t-[#386671]"
        />
        {{ t('status.connecting') }}
      </div>

      <!-- AC 5: Error State with Retry -->
      <div
        v-else-if="isError"
        class="rounded-2xl border border-red-200 bg-red-50 p-6 text-center shadow-sm"
        role="alert"
      >
        <p class="text-sm font-medium text-red-700">
          {{ t('status.serviceUnavailableHint') }}
        </p>
        <button
          type="button"
          class="mt-3 rounded-lg bg-[#386671] px-4 py-2 text-sm font-semibold text-white shadow hover:bg-[#2c525b]"
          @click="() => refetch()"
        >
          {{ t('branch.retry') }}
        </button>
      </div>

      <!-- Master-Detail Content -->
      <div v-else class="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <!-- ฝั่งซ้าย: Selected Branch Detail Card (Col 1-7) -->
        <div class="lg:col-span-7">
          <div
            v-if="selectedBranch"
            class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm"
          >
            <!-- Map Preview Box -->
            <div
              class="relative flex h-48 w-full items-center justify-center overflow-hidden bg-[#e9edf1] md:h-52"
            >
              <svg
                class="absolute inset-0 size-full stroke-gray-300/70"
                xmlns="http://www.w3.org/2000/svg"
              >
                <defs>
                  <pattern
                    id="map-grid"
                    width="36"
                    height="36"
                    patternUnits="userSpaceOnUse"
                  >
                    <path
                      d="M 36 0 L 0 0 0 36"
                      fill="none"
                      stroke="#d5dde5"
                      stroke-width="0.75"
                    />
                  </pattern>
                </defs>
                <rect width="100%" height="100%" fill="url(#map-grid)" />
                <path
                  d="M -20 80 Q 220 140 420 60 T 850 110"
                  fill="none"
                  stroke="#cbd5e1"
                  stroke-width="7"
                />
                <path
                  d="M 160 -20 L 300 280"
                  fill="none"
                  stroke="#cbd5e1"
                  stroke-width="5"
                />
                <path
                  d="M -10 180 L 700 30"
                  fill="none"
                  stroke="#cbd5e1"
                  stroke-width="3"
                  stroke-dasharray="6,4"
                />
              </svg>

              <!-- Pin -->
              <div class="relative z-10 flex flex-col items-center">
                <div
                  class="flex size-10 items-center justify-center rounded-full bg-[#386671] text-white shadow-lg ring-4 ring-[#386671]/20"
                >
                  <img
                    :src="mapPin"
                    alt=""
                    class="size-5 brightness-0 invert"
                  />
                </div>
                <span
                  class="mt-1.5 rounded-full bg-white/95 px-3 py-0.5 text-xs font-semibold text-gray-800 shadow-sm backdrop-blur"
                >
                  {{ selectedBranch.name }}
                </span>
              </div>

              <!-- Controls -->
              <div
                class="absolute bottom-2.5 right-2.5 z-10 flex flex-col overflow-hidden rounded-md border border-gray-300/80 bg-white/95 shadow-sm"
              >
                <span
                  class="flex size-6 items-center justify-center border-b border-gray-200 text-xs font-bold text-gray-500 select-none"
                  >+</span
                >
                <span
                  class="flex size-6 items-center justify-center text-xs font-bold text-gray-500 select-none"
                  >−</span
                >
              </div>
            </div>

            <!-- Detail Information -->
            <div class="p-4 md:p-5">
              <div
                class="flex items-center justify-between gap-3 border-b border-gray-100 pb-2.5"
              >
                <h2 class="text-base font-bold text-gray-900 md:text-lg">
                  {{ selectedBranch.name }}
                </h2>
                <span
                  class="rounded-full px-2 py-0.5 text-xs font-semibold"
                  :class="
                    isBranchBookable(selectedBranch)
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'bg-red-50 text-red-700'
                  "
                >
                  {{
                    isBranchBookable(selectedBranch)
                      ? t('branch.active')
                      : t('branch.inactive')
                  }}
                </span>
              </div>

              <!-- Information Rows -->
              <div
                class="mt-3 flex flex-col gap-2 text-xs md:text-sm text-gray-600"
              >
                <!-- Phone -->
                <div class="flex items-center gap-2.5">
                  <span class="text-sm">📞</span>
                  <span>{{
                    selectedBranch.phone || t('branch.phoneFallback')
                  }}</span>
                </div>

                <!-- Address -->
                <div class="flex items-start gap-2.5">
                  <span class="mt-0.5 text-sm">📍</span>
                  <span class="leading-relaxed">
                    {{ selectedBranch.address || t('branch.noAddress') }}
                  </span>
                </div>

                <!-- Opening Hours -->
                <div class="flex items-center gap-2.5">
                  <span class="text-sm">⏰</span>
                  <span>{{ formatHours(selectedBranch) }}</span>
                </div>

                <!-- Distance -->
                <div class="flex items-center gap-2.5 text-xs text-gray-500">
                  <span class="text-sm">🧭</span>
                  <span v-if="calculateDistance(selectedBranch)">
                    {{
                      t('branch.distanceKm', {
                        distance: calculateDistance(selectedBranch),
                      })
                    }}
                  </span>
                  <span v-else class="text-gray-400">
                    {{ t('branch.distanceUnavailable') }}
                  </span>
                </div>
              </div>

              <!-- Warning for Inactive Branch (AC 6) -->
              <div
                v-if="!isBranchBookable(selectedBranch)"
                class="mt-2.5 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs font-medium text-amber-800"
                role="alert"
              >
                {{ t('branch.bookingUnavailable') }}
              </div>

              <!-- Book Action Button (AC 3 & 6) -->
              <div class="mt-3 border-t border-gray-100 pt-3">
                <button
                  type="button"
                  :disabled="!isBranchBookable(selectedBranch)"
                  class="flex h-10 w-full items-center justify-center rounded-xl px-4 text-sm font-bold shadow transition"
                  :class="
                    isBranchBookable(selectedBranch)
                      ? 'bg-[#386671] text-white hover:bg-[#2c525b] active:scale-[0.99]'
                      : 'cursor-not-allowed bg-gray-100 text-gray-400 shadow-none'
                  "
                  @click="startReservation(selectedBranch)"
                >
                  {{ t('branch.bookAtBranch') }}
                </button>
              </div>
            </div>
          </div>

          <div
            v-else
            class="rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center text-gray-400"
          >
            {{ t('branch.selectBranchPrompt') }}
          </div>
        </div>

        <!-- ฝั่งขวา: Search และ Scrollable Branch List (Col 8-12) -->
        <div class="flex flex-col gap-2.5 lg:col-span-5">
          <!-- Search Input -->
          <div class="relative flex items-center">
            <img
              :src="searchIcon"
              alt=""
              class="absolute left-3 size-3.5 opacity-50"
            />
            <input
              v-model="rawSearchQuery"
              type="search"
              :placeholder="t('branch.searchPlaceholder')"
              class="h-9 w-full rounded-lg border border-gray-300 bg-[#ebebeb] pl-8 pr-3 text-xs md:text-sm text-gray-900 outline-none transition focus:border-gray-400 focus:bg-white focus:ring-2 focus:ring-[#386671]/20"
            />
          </div>

          <!-- Empty State -->
          <div
            v-if="filteredBranches.length === 0"
            class="rounded-xl border border-dashed border-gray-300 bg-white p-6 text-center"
          >
            <p class="font-semibold text-gray-900">
              {{ t('branch.empty.title') }}
            </p>
            <p class="mt-1 text-xs text-gray-500">
              {{ t('branch.empty.description') }}
            </p>
          </div>

          <!-- Branch List Items -->
          <ul
            v-else
            class="flex max-h-[440px] flex-col gap-1.5 overflow-y-auto pr-1"
          >
            <li v-for="branch in filteredBranches" :key="branch.id">
              <button
                type="button"
                :aria-selected="selectedBranchId === branch.id"
                class="flex w-full items-center justify-between rounded-lg px-3.5 py-2.5 text-left text-sm font-medium transition"
                :class="
                  selectedBranchId === branch.id
                    ? 'bg-[#d9d9d9] font-bold text-gray-900 shadow-sm'
                    : 'bg-[#eeeeee] text-gray-700 hover:bg-[#e2e2e2]'
                "
                @click="selectBranch(branch.id)"
              >
                <span class="truncate">{{ branch.name }}</span>
                <span class="shrink-0 text-xs text-gray-500">
                  <span v-if="calculateDistance(branch)">
                    {{ calculateDistance(branch) }} km
                  </span>
                  <span v-else class="text-gray-400">—</span>
                </span>
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>
