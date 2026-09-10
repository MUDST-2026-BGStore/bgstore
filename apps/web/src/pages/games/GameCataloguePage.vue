<script setup lang="ts">
import { useInfiniteQuery } from '@tanstack/vue-query';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import ClientLayout from '../../layouts/ClientLayout.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import {
  catalogueQueryOptions,
  type CatalogueQuery,
} from '../../queries/games';
import { cardMeta, catalogueCategories } from './catalogue';
import { catalogueLocaleOf, resolveLocalized } from './localized';
import type { GameCategory, GameSummary } from '../../generated/api/types.gen';

/** The guest's view of `/games`: what the store has, to pick from before a visit. */
const { t, locale } = useI18n();

const search = ref('');
const debouncedSearch = ref('');
const category = ref<GameCategory | ''>('');

// Typing should not fire a request per keystroke, but the query key still has
// to settle on what was typed.
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(search, (value) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    debouncedSearch.value = value;
  }, 300);
});

const query = computed<CatalogueQuery>(() => ({
  // A guest never browses a game the store no longer offers.
  lifecycle: 'active',
  ...(category.value ? { category: category.value } : {}),
  ...(debouncedSearch.value.trim()
    ? { search: debouncedSearch.value.trim() }
    : {}),
  // The list is paged in the database, so the locale decides the order there.
  locale: catalogueLocaleOf(locale.value),
}));

const games = useInfiniteQuery(
  computed(() => catalogueQueryOptions(query.value)),
);

const cards = computed(
  () => games.data.value?.pages.flatMap((page) => page.items) ?? [],
);
const total = computed(
  () => games.data.value?.pages.at(-1)?.page.totalElements ?? 0,
);
const filtered = computed(
  () => Boolean(category.value) || debouncedSearch.value.trim().length > 0,
);

const chips = computed(() => [
  { value: '' as const, label: t('catalogue.all') },
  ...catalogueCategories.map((value) => ({
    value,
    label: t('catalogue.category.' + value),
  })),
]);

function titleOf(game: GameSummary) {
  return resolveLocalized(game.title, locale.value);
}

function metaOf(game: GameSummary) {
  return cardMeta(game, t);
}
</script>

<template>
  <ClientLayout>
    <div class="flex w-full flex-col items-start gap-6 px-8 pt-8 pb-14">
      <header class="flex flex-col items-start gap-1">
        <h1 class="text-[28px] leading-[1.45] font-bold text-ink">
          {{ t('catalogue.title') }}
        </h1>
        <p class="text-[14px] leading-[1.55] text-ink-secondary">
          {{ t('catalogue.subtitle') }}
        </p>
      </header>

      <div class="flex w-full flex-wrap items-center gap-3">
        <UiTextInput
          id="catalogue-search"
          v-model="search"
          search
          class="w-full shrink-0 sm:w-[320px]"
          :placeholder="t('catalogue.searchPlaceholder')"
        />
        <div
          role="group"
          :aria-label="t('catalogue.categoryFilter')"
          class="flex flex-wrap items-center gap-2"
        >
          <button
            v-for="chip in chips"
            :key="chip.value"
            type="button"
            :aria-pressed="category === chip.value"
            class="rounded-full border px-[14px] py-[7px] text-[13px] leading-[1.4] whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            :class="
              category === chip.value
                ? 'border-primary bg-primary font-semibold text-primary-fg'
                : 'border-line bg-surface text-ink-secondary hover:border-line-strong'
            "
            @click="category = chip.value"
          >
            {{ chip.label }}
          </button>
        </div>
        <div class="h-px min-w-0 flex-1" />
        <p
          data-testid="catalogue-count"
          aria-live="polite"
          class="shrink-0 text-[13px] leading-[1.5] text-ink-muted"
        >
          {{ t('catalogue.count', total) }}
        </p>
      </div>

      <p
        v-if="games.isPending.value"
        data-testid="catalogue-loading"
        class="text-[14px] text-ink-muted"
      >
        {{ t('catalogue.loading') }}
      </p>

      <div
        v-else-if="games.isError.value"
        class="flex flex-col items-start gap-3"
      >
        <p
          data-testid="catalogue-error"
          role="alert"
          class="text-[14px] text-danger-fg"
        >
          {{ t('catalogue.loadFailed') }}
        </p>
        <UiButton variant="outline" size="sm" @click="games.refetch()">
          {{ t('catalogue.retry') }}
        </UiButton>
      </div>

      <p
        v-else-if="cards.length === 0"
        data-testid="catalogue-empty"
        class="text-[14px] text-ink-muted"
      >
        {{ filtered ? t('catalogue.emptyFiltered') : t('catalogue.empty') }}
      </p>

      <template v-else>
        <ul class="grid w-full grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <li v-for="game in cards" :key="game.id" class="flex">
            <router-link
              :to="'/games/' + game.id"
              data-testid="catalogue-card"
              class="flex w-full flex-col items-start gap-3 rounded-lg border border-line bg-surface px-3 pt-3 pb-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <div
                class="h-[104px] w-full shrink-0 overflow-hidden rounded-md bg-surface-sunken"
              >
                <img
                  v-if="game.coverImageUrl"
                  :src="game.coverImageUrl"
                  alt=""
                  loading="lazy"
                  class="block size-full object-cover"
                />
              </div>
              <div class="flex w-full min-w-0 flex-col items-start gap-0.5">
                <h2
                  class="w-full truncate text-[16px] leading-[24px] font-semibold text-ink"
                >
                  {{ titleOf(game) }}
                </h2>
                <p class="w-full text-[12px] leading-[18px] text-ink-muted">
                  {{ metaOf(game) }}
                </p>
              </div>
            </router-link>
          </li>
        </ul>

        <UiButton
          v-if="games.hasNextPage.value"
          variant="outline"
          class="self-center"
          :disabled="games.isFetchingNextPage.value"
          @click="games.fetchNextPage()"
        >
          {{ t('catalogue.showMore') }}
        </UiButton>
      </template>
    </div>
  </ClientLayout>
</template>
