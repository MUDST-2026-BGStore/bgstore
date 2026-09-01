<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import ScreenStatus from '../../components/ScreenStatus.vue';
import PageBreadcrumb from '../../components/PageBreadcrumb.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiCard from '../../components/ui/UiCard.vue';
import { gameQueryOptions } from '../../queries/games';
import { formatDate } from './display';
import { hasStatus } from './form';
import { statusTone } from './status';

const { t, locale } = useI18n();
const route = useRoute();

const gameId = computed(() => String(route.params.gameId));
const query = useQuery(computed(() => gameQueryOptions(gameId.value)));

const game = computed(() => query.data.value);
const missing = computed(() => hasStatus(query.error.value, 404));

// Edit game lands here carrying the saved title, as its success state.
const savedTitle = computed(() =>
  typeof route.query.saved === 'string' ? route.query.saved : undefined,
);

const unknown = computed(() => t('games.state.unknown'));

const players = computed(() =>
  game.value
    ? t('games.inventory.playerRange', {
        min: game.value.minPlayers,
        max: game.value.maxPlayers,
      })
    : '',
);

const playTime = computed(() =>
  game.value?.playTimeMinutes
    ? t('games.details.playTimeValue', { minutes: game.value.playTimeMinutes })
    : unknown.value,
);

const added = computed(
  () => formatDate(game.value?.addedAt, locale.value) ?? unknown.value,
);

const lastPlayed = computed(
  () => formatDate(game.value?.lastPlayedAt, locale.value) ?? unknown.value,
);

const facts = computed(() => {
  const loaded = game.value;
  if (!loaded) {
    return [];
  }

  return [
    [
      {
        label: t('games.details.facts.category'),
        value: t('games.category.' + loaded.category),
      },
      { label: t('games.details.facts.players'), value: players.value },
    ],
    [
      { label: t('games.details.facts.playTime'), value: playTime.value },
      {
        label: t('games.details.facts.difficulty'),
        value: loaded.difficulty || unknown.value,
      },
    ],
    [
      { label: t('games.details.facts.added'), value: added.value },
      { label: t('games.details.facts.lastPlayed'), value: lastPlayed.value },
    ],
  ];
});

const meta = computed(() => {
  const loaded = game.value;
  if (!loaded) {
    return '';
  }

  return [
    t('games.category.' + loaded.category),
    t('games.details.metaPlayers', { players: players.value }),
    playTime.value,
    t('games.details.metaAdded', { date: added.value }),
    t('games.details.metaCopies', {
      copies: loaded.totalCopies,
      branches: loaded.branchCount,
    }),
  ].join('  ·  ');
});

const lastStockIndex = computed(() => (game.value?.stock.length ?? 0) - 1);
</script>

<template>
  <ScreenStatus
    v-if="query.isPending.value"
    state="loading"
    testid="details-loading"
  />

  <ScreenStatus v-else-if="missing" state="missing" testid="game-not-found" />

  <ScreenStatus
    v-else-if="query.isError.value || !game"
    state="failed"
    testid="details-error"
    @retry="query.refetch()"
  />

  <OwnerLayout v-else>
    <div
      class="flex w-full shrink-0 items-center gap-3 border-b border-line bg-surface px-8 py-3.5"
    >
      <PageBreadcrumb :current="game.title" />
      <div class="h-px min-w-0 flex-1" />
    </div>

    <div class="flex w-full shrink-0 flex-col items-end gap-6 px-8 pt-6 pb-8">
      <p
        v-if="savedTitle"
        data-testid="details-saved"
        role="status"
        class="w-full text-[13px] leading-[20px] text-success-fg"
      >
        {{ t('games.inventory.saved', { title: savedTitle }) }}
      </p>

      <header class="flex w-full flex-col items-start gap-1">
        <div class="flex w-full items-center gap-3">
          <h1 class="text-[28px] leading-[42px] font-semibold text-ink">
            {{ game.title }}
          </h1>
          <UiBadge :tone="statusTone(game.status)">
            {{ t('games.status.' + game.status) }}
          </UiBadge>
        </div>
        <p class="w-full text-[14px] whitespace-pre-wrap text-ink-secondary">
          {{ meta }}
        </p>
      </header>

      <div class="flex w-full flex-col items-start gap-5">
        <UiCard :title="t('games.details.overview')">
          <div class="flex w-full items-start gap-5">
            <div
              class="h-[174px] w-[260px] shrink-0 rounded-lg border border-line bg-surface-sunken"
            />
            <div class="flex min-w-0 flex-1 flex-col items-start gap-3.5">
              <p
                v-if="game.description"
                data-testid="game-description"
                class="w-full text-[13px] leading-[20px] text-ink-secondary"
              >
                {{ game.description }}
              </p>
              <dl class="flex w-full flex-col items-start gap-3">
                <div
                  v-for="(row, rowIndex) in facts"
                  :key="rowIndex"
                  class="flex w-full items-start gap-5"
                >
                  <div
                    v-for="fact in row"
                    :key="fact.label"
                    class="flex min-w-0 flex-1 flex-col items-start gap-0.5"
                  >
                    <dt
                      class="text-[11px] leading-[17px] font-semibold text-ink-muted"
                    >
                      {{ fact.label }}
                    </dt>
                    <dd
                      class="text-[13px] leading-[20px] font-semibold text-ink"
                    >
                      {{ fact.value }}
                    </dd>
                  </div>
                </div>
              </dl>
              <ul class="flex w-full flex-wrap items-start gap-2">
                <li
                  v-for="tag in game.tags"
                  :key="tag"
                  class="rounded-full border border-line bg-surface-sunken px-3 py-[5px] text-[12px] leading-[18px] font-semibold whitespace-nowrap text-ink-secondary"
                >
                  {{ tag }}
                </li>
              </ul>
            </div>
          </div>
        </UiCard>

        <UiCard :title="t('games.details.copiesByBranch')">
          <template #subtitle>
            {{ t('games.details.copiesByBranchHint') }}
          </template>
          <div
            class="w-full overflow-hidden rounded-table border border-line bg-surface"
          >
            <table class="w-full table-fixed border-collapse text-left">
              <colgroup>
                <col />
                <col class="w-[80px]" />
                <col class="w-[90px]" />
                <col class="w-[80px]" />
                <col class="w-[130px]" />
              </colgroup>
              <thead>
                <tr
                  class="border-b border-line bg-surface-sunken text-[12px] leading-[18px] font-semibold text-ink-secondary [&>th:first-child]:pl-4 [&>th:last-child]:pr-4"
                >
                  <th scope="col" class="px-1.5 py-2.5 font-semibold">
                    {{ t('games.details.columns.branch') }}
                  </th>
                  <th scope="col" class="px-1.5 py-2.5 font-semibold">
                    {{ t('games.details.columns.copies') }}
                  </th>
                  <th scope="col" class="px-1.5 py-2.5 font-semibold">
                    {{ t('games.details.columns.available') }}
                  </th>
                  <th scope="col" class="px-1.5 py-2.5 font-semibold">
                    {{ t('games.details.columns.inUse') }}
                  </th>
                  <th scope="col" class="px-1.5 py-2.5 font-semibold">
                    {{ t('games.details.columns.status') }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(stock, index) in game.stock"
                  :key="stock.branchId"
                  class="text-[13px] leading-[20px] [&>td:last-child]:pr-4 [&>th:first-child]:pl-4"
                  :class="index < lastStockIndex ? 'border-b border-line' : ''"
                >
                  <th
                    scope="row"
                    class="px-1.5 py-2.5 text-left font-semibold text-ink"
                  >
                    {{ stock.branchName }}
                  </th>
                  <td class="px-1.5 py-2.5 text-ink-secondary">
                    {{ stock.copies }}
                  </td>
                  <td class="px-1.5 py-2.5 font-semibold text-ink">
                    {{ stock.available }}
                  </td>
                  <td class="px-1.5 py-2.5 text-ink-secondary">
                    {{ stock.inUse }}
                  </td>
                  <td class="px-1.5 py-2.5">
                    <UiBadge :tone="statusTone(stock.status)">
                      {{ t('games.status.' + stock.status) }}
                    </UiBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </UiCard>
      </div>

      <div class="flex shrink-0 items-center gap-[21px]">
        <UiButton variant="outline" to="/games" class="w-[120px]">
          {{ t('games.details.back') }}
        </UiButton>
        <UiButton :to="'/games/' + game.id + '/edit'" class="w-[120px]">
          {{ t('games.details.editGame') }}
        </UiButton>
      </div>
    </div>
  </OwnerLayout>
</template>
