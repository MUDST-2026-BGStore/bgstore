<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import StatCard from '../../components/ui/StatCard.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import statTitles from '../../assets/icons/stat-titles.svg';
import statAvailable from '../../assets/icons/stat-available.svg';
import statInUse from '../../assets/icons/stat-in-use.svg';
import {
  branchesQueryOptions,
  gamesPageSize,
  gamesQueryOptions,
  retireGameRequest,
  type GameListQuery,
} from '../../queries/games';
import { branchLabel, pageRange } from './display';
import { categoryOptions } from './form';
import { gameStatuses, statusTone } from './status';
import type {
  GameAvailability,
  GameCategory,
} from '../../generated/api/types.gen';

const { t } = useI18n();
const route = useRoute();
const queryClient = useQueryClient();

// Add game lands here carrying the saved title, which is this screen's half of
// its success state; the new row is the other half.
const savedTitle = computed(() =>
  typeof route.query.saved === 'string' ? route.query.saved : undefined,
);

const branchFilter = ref('');
const search = ref('');
const debouncedSearch = ref('');
const categoryFilter = ref<GameCategory | ''>('');
const statusFilter = ref<GameAvailability | ''>('');
const page = ref(0);

// Typing should not fire a request per keystroke, but the URL the query key is
// built from still has to settle on what was typed.
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(search, (value) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    debouncedSearch.value = value;
  }, 300);
});

// Page 3 of the old filter is rarely page 3 of the new one.
watch([branchFilter, categoryFilter, statusFilter, debouncedSearch], () => {
  page.value = 0;
});

const branches = useQuery(branchesQueryOptions());

const query = computed<GameListQuery>(() => ({
  ...(branchFilter.value ? { branchId: branchFilter.value } : {}),
  ...(categoryFilter.value ? { category: categoryFilter.value } : {}),
  ...(statusFilter.value ? { status: statusFilter.value } : {}),
  ...(debouncedSearch.value.trim()
    ? { search: debouncedSearch.value.trim() }
    : {}),
  page: page.value,
  size: gamesPageSize,
}));

const games = useQuery(computed(() => gamesQueryOptions(query.value)));

const retire = useMutation({
  mutationFn: retireGameRequest,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['games'] }),
});

const rows = computed(() => games.data.value?.items ?? []);
const stats = computed(() => games.data.value?.stats);
const pageMeta = computed(() => games.data.value?.page);

const hasFilters = computed(
  () =>
    Boolean(branchFilter.value) ||
    Boolean(categoryFilter.value) ||
    Boolean(statusFilter.value) ||
    debouncedSearch.value.trim().length > 0,
);

const range = computed(() => {
  const meta = pageMeta.value;
  if (!meta || rows.value.length === 0) {
    return undefined;
  }

  return {
    ...pageRange(meta.number, meta.size, rows.value.length),
    total: meta.totalElements,
  };
});

const branchOptions = computed(() =>
  (branches.data.value ?? []).map((branch) => ({
    value: branch.id,
    label: branch.name,
  })),
);

const categories = categoryOptions(t);

const statusOptions = gameStatuses.map((status) => ({
  value: status,
  label: t('games.status.' + status),
}));

const lastRowIndex = computed(() => rows.value.length - 1);

const canGoBack = computed(() => (pageMeta.value?.number ?? 0) > 0);
const canGoForward = computed(() => {
  const meta = pageMeta.value;
  return meta ? meta.number + 1 < meta.totalPages : false;
});

function playerRange(row: { minPlayers: number; maxPlayers: number }) {
  return t('games.inventory.playerRange', {
    min: row.minPlayers,
    max: row.maxPlayers,
  });
}

function branchOf(row: Parameters<typeof branchLabel>[0]) {
  return branchLabel(
    row,
    (count) => t('games.inventory.branchCount', { count }),
    t('games.state.unknown'),
  );
}
</script>

<template>
  <OwnerLayout>
    <div
      class="flex w-full shrink-0 items-center gap-3 border-b border-line bg-surface px-8 py-4"
    >
      <p class="shrink-0 text-[18px] leading-[1.4] font-semibold text-ink">
        {{ t('games.inventory.sectionTitle') }}
      </p>
      <div class="h-px min-w-0 flex-1" />
      <!--
        "All branches" is a real selected value here (the Figma frame renders
        this control in the Filled state), so its empty option is selectable.
      -->
      <UiSelect
        id="branch-filter"
        v-model="branchFilter"
        class="w-[190px] shrink-0"
        :placeholder="t('games.inventory.allBranches')"
        placeholder-selectable
        :options="branchOptions"
      />
      <UiButton to="/games/new">{{ t('games.inventory.addGame') }}</UiButton>
    </div>

    <div
      class="flex w-full shrink-0 flex-col items-start gap-[22px] px-8 pt-7 pb-10"
    >
      <h1 class="text-[26px] leading-[1.4] font-bold text-ink">
        {{ t('games.inventory.title') }}
      </h1>

      <div class="flex w-full items-start gap-4">
        <StatCard
          tone="neutral"
          :icon="statTitles"
          :label="t('games.inventory.stats.titles')"
          :value="String(stats?.titles ?? 0)"
        />
        <StatCard
          tone="success"
          :icon="statAvailable"
          :label="t('games.inventory.stats.availableNow')"
          :value="String(stats?.availableNow ?? 0)"
        />
        <StatCard
          tone="warning"
          :icon="statInUse"
          :label="t('games.inventory.stats.inUse')"
          :value="String(stats?.inUse ?? 0)"
        />
      </div>

      <div class="flex w-full items-center gap-3">
        <UiTextInput
          id="game-search"
          v-model="search"
          search
          class="w-[280px] shrink-0"
          :placeholder="t('games.inventory.searchPlaceholder')"
        />
        <UiSelect
          id="category-filter"
          v-model="categoryFilter"
          class="w-[170px] shrink-0"
          :placeholder="t('games.inventory.allCategories')"
          placeholder-selectable
          :options="categories"
        />
        <UiSelect
          id="status-filter"
          v-model="statusFilter"
          class="w-[170px] shrink-0"
          :placeholder="t('games.inventory.allStatuses')"
          placeholder-selectable
          :options="statusOptions"
        />
        <div class="h-px min-w-0 flex-1" />
        <p
          data-testid="inventory-range"
          class="shrink-0 text-[13px] leading-[1.5] text-ink-muted"
        >
          {{
            range
              ? t('games.inventory.showing', range)
              : t('games.inventory.showingNone')
          }}
        </p>
        <!--
          The design draws no pager, but it does report a total larger than one
          page, so the rows past the first page need a way to be reached.
        -->
        <UiButton
          variant="outline"
          size="sm"
          :disabled="!canGoBack"
          @click="page -= 1"
        >
          {{ t('games.inventory.previousPage') }}
        </UiButton>
        <UiButton
          variant="outline"
          size="sm"
          :disabled="!canGoForward"
          @click="page += 1"
        >
          {{ t('games.inventory.nextPage') }}
        </UiButton>
      </div>

      <p
        v-if="savedTitle"
        data-testid="inventory-saved"
        role="status"
        class="w-full text-[13px] leading-[1.5] text-success-fg"
      >
        {{ t('games.inventory.saved', { title: savedTitle }) }}
      </p>

      <p
        v-if="retire.isError.value"
        data-testid="inventory-delete-error"
        role="alert"
        class="w-full text-[13px] leading-[1.5] text-danger-fg"
      >
        {{ t('games.inventory.deleteFailed') }}
      </p>

      <div
        class="w-full overflow-hidden rounded-lg border border-line bg-surface"
      >
        <table class="w-full table-fixed border-collapse text-left">
          <colgroup>
            <col />
            <col class="w-[100px]" />
            <col class="w-[100px]" />
            <col class="w-[130px]" />
            <col class="w-[70px]" />
            <col class="w-[80px]" />
            <col class="w-[110px]" />
            <col class="w-[140px]" />
          </colgroup>
          <thead>
            <tr
              class="border-b border-line bg-surface-sunken text-[12px] leading-[1.4] font-semibold text-ink-muted [&>th:first-child]:pl-5 [&>th:last-child]:pr-5"
            >
              <th scope="col" class="px-[7px] py-3 font-semibold">
                {{ t('games.inventory.columns.game') }}
              </th>
              <th scope="col" class="px-[7px] py-3 font-semibold">
                {{ t('games.inventory.columns.category') }}
              </th>
              <th scope="col" class="px-[7px] py-3 font-semibold">
                {{ t('games.inventory.columns.players') }}
              </th>
              <th scope="col" class="px-[7px] py-3 font-semibold">
                {{ t('games.inventory.columns.branch') }}
              </th>
              <th scope="col" class="px-[7px] py-3 text-right font-semibold">
                {{ t('games.inventory.columns.copies') }}
              </th>
              <th scope="col" class="px-[7px] py-3 text-right font-semibold">
                {{ t('games.inventory.columns.available') }}
              </th>
              <th scope="col" class="px-[7px] py-3 font-semibold">
                {{ t('games.inventory.columns.status') }}
              </th>
              <th scope="col" class="px-[7px] py-3 font-semibold">
                <span class="sr-only">
                  {{ t('games.inventory.columns.actions') }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="games.isPending.value">
              <td
                colspan="8"
                data-testid="inventory-loading"
                class="px-5 py-8 text-center text-[13.5px] text-ink-muted"
              >
                {{ t('games.state.loading') }}
              </td>
            </tr>
            <tr v-else-if="games.isError.value">
              <td colspan="8" class="px-5 py-8 text-center">
                <p
                  data-testid="inventory-error"
                  role="alert"
                  class="text-[13.5px] text-danger-fg"
                >
                  {{ t('games.state.loadFailed') }}
                </p>
                <UiButton
                  variant="outline"
                  size="sm"
                  class="mt-3"
                  @click="games.refetch()"
                >
                  {{ t('games.state.retry') }}
                </UiButton>
              </td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td
                colspan="8"
                data-testid="inventory-empty"
                class="px-5 py-8 text-center text-[13.5px] text-ink-muted"
              >
                {{
                  hasFilters
                    ? t('games.inventory.emptyFiltered')
                    : t('games.inventory.empty')
                }}
              </td>
            </tr>
            <tr
              v-for="(row, index) in rows"
              v-else
              :key="row.id"
              class="text-[13.5px] leading-[1.5] text-ink [&>td:last-child]:pr-5 [&>th:first-child]:pl-5"
              :class="index < lastRowIndex ? 'border-b border-line' : ''"
            >
              <th
                scope="row"
                class="px-[7px] py-3 text-left text-[13.5px] leading-[1.5] font-semibold text-ink"
              >
                <router-link :to="'/games/' + row.id">
                  {{ row.title }}
                </router-link>
              </th>
              <td class="px-[7px] py-3">
                {{ t('games.category.' + row.category) }}
              </td>
              <td class="px-[7px] py-3">{{ playerRange(row) }}</td>
              <td class="px-[7px] py-3">{{ branchOf(row) }}</td>
              <td class="px-[7px] py-3 text-right">{{ row.copies }}</td>
              <td class="px-[7px] py-3 text-right">{{ row.available }}</td>
              <td class="px-[7px] py-3">
                <UiBadge :tone="statusTone(row.status)">
                  {{ t('games.status.' + row.status) }}
                </UiBadge>
              </td>
              <td class="px-[7px] py-3">
                <div class="flex items-center justify-end gap-2">
                  <UiButton
                    variant="ghost"
                    size="sm"
                    :to="'/games/' + row.id + '/edit'"
                  >
                    {{ t('games.inventory.edit') }}
                  </UiButton>
                  <UiButton
                    variant="ghost"
                    size="sm"
                    :disabled="retire.isPending.value"
                    @click="retire.mutate(row.id)"
                  >
                    {{ t('games.inventory.delete') }}
                  </UiButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </OwnerLayout>
</template>
