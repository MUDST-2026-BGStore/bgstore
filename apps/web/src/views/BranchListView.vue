<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useBranches } from '../composables/useBranches';

const router = useRouter();
const { t } = useI18n();
const { branches, isError, isPending, refetch } = useBranches();

const rawSearchQuery = ref('');
const searchQuery = ref('');
const currentPage = ref(1);
const itemsPerPage = 10;
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

watch(rawSearchQuery, (value) => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    searchQuery.value = value.trim().toLowerCase();
    currentPage.value = 1;
  }, 300);
});

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});

const filteredBranches = computed(() => {
  if (!searchQuery.value) return branches.value;
  return branches.value.filter((branch) =>
    branch.name.toLowerCase().includes(searchQuery.value),
  );
});

const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredBranches.value.length / itemsPerPage)),
);

const paginatedBranches = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage;
  return filteredBranches.value.slice(start, start + itemsPerPage);
});

watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages;
});

const goToBranch = (id: string) => {
  void router.push(`/branches/${id}`);
};
</script>

<template>
  <section
    class="w-full bg-white px-6 py-8 md:px-12"
    aria-labelledby="branch-title"
  >
    <div class="mx-auto max-w-6xl">
      <div class="mb-6 flex items-center justify-between gap-4">
        <div>
          <p
            class="text-xs font-semibold uppercase tracking-wide text-[#386671]"
          >
            {{ t('branch.list') }}
          </p>
          <h1 id="branch-title" class="mt-1 text-2xl font-bold text-gray-900">
            {{ t('branch.management') }}
          </h1>
        </div>
        <span class="text-sm text-gray-500">{{ filteredBranches.length }}</span>
      </div>

      <label class="mb-5 block max-w-md">
        <span class="sr-only">{{ t('branch.searchPlaceholder') }}</span>
        <input
          v-model="rawSearchQuery"
          type="search"
          :placeholder="t('branch.searchPlaceholder')"
          class="h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-[#386671]"
        />
      </label>

      <div
        v-if="isPending"
        class="rounded-xl border border-gray-200 p-8 text-center text-gray-500"
        aria-live="polite"
      >
        {{ t('status.connecting') }}
      </div>
      <div
        v-else-if="isError"
        class="rounded-xl border border-red-200 bg-red-50 p-6 text-center"
        role="alert"
      >
        <p class="text-sm text-red-700">
          {{ t('status.serviceUnavailableHint') }}
        </p>
        <button
          type="button"
          class="mt-4 rounded-lg bg-[#386671] px-4 py-2 text-sm font-semibold text-white"
          @click="() => refetch()"
        >
          {{ t('branch.retry') }}
        </button>
      </div>
      <div
        v-else-if="filteredBranches.length === 0"
        class="rounded-xl border border-gray-200 p-8 text-center"
      >
        <p class="font-semibold text-gray-900">{{ t('branch.empty.title') }}</p>
        <p class="mt-1 text-sm text-gray-500">
          {{ t('branch.empty.description') }}
        </p>
      </div>
      <div v-else class="overflow-hidden rounded-xl border border-gray-200">
        <table class="w-full text-left text-sm">
          <thead
            class="border-b border-gray-200 bg-gray-50 text-xs font-semibold text-gray-600"
          >
            <tr>
              <th class="px-5 py-3">{{ t('branch.table.branch') }}</th>
              <th class="px-5 py-3">{{ t('branch.id') }}</th>
              <th class="px-5 py-3 text-right">{{ t('branch.view') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="branch in paginatedBranches"
              :key="branch.id"
              class="border-b border-gray-100 last:border-0"
            >
              <td class="px-5 py-4 font-medium text-gray-900">
                {{ branch.name }}
              </td>
              <td class="px-5 py-4 font-mono text-xs text-gray-500">
                {{ branch.id }}
              </td>
              <td class="px-5 py-4 text-right">
                <button
                  type="button"
                  class="font-semibold text-[#386671] hover:underline"
                  @click="goToBranch(branch.id)"
                >
                  {{ t('branch.view') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <nav
          v-if="totalPages > 1"
          class="flex items-center justify-between border-t border-gray-200 px-5 py-3"
          :aria-label="t('branch.list')"
        >
          <button
            type="button"
            class="text-sm text-gray-600 disabled:opacity-40"
            :disabled="currentPage === 1"
            @click="currentPage--"
          >
            ‹
          </button>
          <span class="text-xs text-gray-500">
            {{ currentPage }} / {{ totalPages }}
          </span>
          <button
            type="button"
            class="text-sm text-gray-600 disabled:opacity-40"
            :disabled="currentPage === totalPages"
            @click="currentPage++"
          >
            ›
          </button>
        </nav>
      </div>
    </div>
  </section>
</template>
