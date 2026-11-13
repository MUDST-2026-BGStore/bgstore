<script setup lang="ts">
// Discovery lists the catalog; when a store is chosen, its offerings can filter
// games sold there or included in its rental library. Cards deliberately show no
// price or availability.
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { storeToRefs } from 'pinia';
import { RouterLink } from 'vue-router';
import { useCatalogBrowseStore } from '../stores/catalog-browse';
import { useStoreContextStore } from '../stores/store-context';
import { useStoreText } from '../data/store-text';
import StoreContext from '../components/StoreContext.vue';
import { range } from '../data/catalog';
import {
  useCatalog,
  useTaxonomy,
  imageUrl,
  cover,
  localizedDescription,
  taxonomyLabel,
} from '../api/catalog';
import { useBranchGames } from '../api/inventory';
import { AppCheckbox, AppSelect } from '../../../shared/ui';

const compact = ref(false);
const filterDialog = ref<HTMLDialogElement>();
const searchInput = ref<HTMLInputElement>();
const catalogHeading = ref<HTMLElement>();
const catalogLayout = ref<HTMLElement>();
let sidebarFrame: number | undefined;
let headingObserver: ResizeObserver | undefined;
let mobileLayout: MediaQueryList | undefined;
function measureSidebar() {
  const layout = catalogLayout.value;
  if (!layout || compact.value) return;
  // Measure the grid rather than the sticky sidebar, whose height changes here.
  const top = Math.round(layout.getBoundingClientRect().top);
  layout.style.setProperty('--catalog-layout-top', `${top}px`);
}
function scheduleSidebarMeasure() {
  if (sidebarFrame !== undefined || compact.value) return;
  sidebarFrame = window.requestAnimationFrame(() => {
    sidebarFrame = undefined;
    measureSidebar();
  });
}
function syncLayout() {
  compact.value = mobileLayout?.matches ?? false;
  if (!compact.value && filterDialog.value?.open) {
    filterDialog.value.close();
    searchInput.value?.focus({ preventScroll: true });
  }
  measureSidebar();
}
onMounted(() => {
  if (typeof window.matchMedia !== 'function') return;
  mobileLayout = window.matchMedia('(max-width: 700px)');
  syncLayout();
  mobileLayout.addEventListener('change', syncLayout);
  window.addEventListener('scroll', scheduleSidebarMeasure, { passive: true });
  window.addEventListener('resize', scheduleSidebarMeasure);
  if (catalogHeading.value && typeof ResizeObserver !== 'undefined') {
    headingObserver = new ResizeObserver(scheduleSidebarMeasure);
    headingObserver.observe(catalogHeading.value);
  }
});
onBeforeUnmount(() => {
  mobileLayout?.removeEventListener('change', syncLayout);
  window.removeEventListener('scroll', scheduleSidebarMeasure);
  window.removeEventListener('resize', scheduleSidebarMeasure);
  headingObserver?.disconnect();
  if (sidebarFrame !== undefined) window.cancelAnimationFrame(sidebarFrame);
});
function openFilters() {
  if (compact.value) filterDialog.value?.showModal();
}

const loginFailed = new URLSearchParams(window.location.search).has(
  'login_error',
);
const { t, locale } = useStoreText();
const sortOptions = computed(() => [
  { value: 'shelf', label: t('Shelf order') },
  { value: 'name', label: t('Name: A–Z') },
  { value: 'time', label: t('Shortest playtime') },
]);
const playerOptions = computed(() => [
  { value: '', label: t('Any group size') },
  ...Array.from({ length: 20 }, (_, index) => ({
    value: String(index + 1),
    label: t(index === 0 ? '{count} player' : '{count} players', {
      count: index + 1,
    }),
  })),
]);
const timeOptions = computed(() => [
  { value: '', label: t('Any duration') },
  { value: '30', label: t('Up to 30 minutes') },
  { value: '60', label: t('Up to 1 hour') },
  { value: '120', label: t('Up to 2 hours') },
  { value: '180', label: t('Up to 3 hours') },
]);
const difficultyOptions = computed(() => [
  { value: '', label: t('All experience levels') },
  { value: 'Easy', label: t('First-timer friendly') },
  { value: 'Medium', label: t('Some experience') },
  { value: 'Advanced', label: t('Ready for a challenge') },
]);
const context = useStoreContextStore();
const catalog = useCatalog();
const taxonomy = useTaxonomy();
const branchGames = useBranchGames(() => context.stockBranch?.id);
const {
  query,
  category,
  players,
  time,
  difficulty,
  soldHere,
  inLibrary,
  sort,
  view,
} = storeToRefs(useCatalogBrowseStore());
watch(
  () => [context.stockBranch?.id, Boolean(context.branch)] as const,
  ([stockBranchId, hasBranch]) => {
    if (!stockBranchId) {
      soldHere.value = false;
      inLibrary.value = false;
    } else if (!hasBranch) {
      inLibrary.value = false;
    }
  },
);
const soldGameIds = computed(
  () =>
    new Set(
      (branchGames.data.value ?? [])
        .filter((item) => item.saleEnabled && item.editions.length > 0)
        .map((item) => item.game.id),
    ),
);
const libraryGameIds = computed(
  () =>
    new Set(
      (branchGames.data.value ?? [])
        .filter((item) => item.rentalEnabled)
        .map((item) => item.game.id),
    ),
);
const games = computed(() => catalog.data.value ?? []);
const categories = computed(() => [
  ...new Set(games.value.flatMap((game) => game.categories)),
]);
const categoryLabel = (key: string) =>
  taxonomyLabel(taxonomy.data.value?.categories, key, locale.value);
const failedImages = ref(new Set<string>());
const activeCount = computed(
  () =>
    [
      category.value,
      players.value,
      time.value,
      difficulty.value,
      context.stockBranch && soldHere.value,
      context.branch && inLibrary.value,
    ].filter(Boolean).length,
);
const filtered = computed(() => {
  const needle = query.value.trim().toLocaleLowerCase();
  const result = games.value.filter(
    (game) =>
      (!needle ||
        [
          game.name,
          game.designer ?? '',
          ...game.categories,
          ...game.categories.map(categoryLabel),
          ...game.mechanics,
        ]
          .join(' ')
          .toLocaleLowerCase()
          .includes(needle)) &&
      (!category.value || game.categories.includes(category.value)) &&
      (!players.value ||
        (game.minPlayers <= Number(players.value) &&
          game.maxPlayers >= Number(players.value))) &&
      (!time.value ||
        (game.teachMinutes != null &&
          game.setupMinutes != null &&
          game.playMinutesMax + game.teachMinutes + game.setupMinutes <=
            Number(time.value))) &&
      (!difficulty.value ||
        game.difficulty === difficulty.value.toUpperCase()) &&
      (!context.stockBranch ||
        !soldHere.value ||
        soldGameIds.value.has(game.id)) &&
      (!context.branch ||
        !inLibrary.value ||
        libraryGameIds.value.has(game.id)),
  );
  if (sort.value === 'name')
    result.sort((a, b) => a.name.localeCompare(b.name, locale.value));
  if (sort.value === 'time')
    result.sort((a, b) => a.playMinutesMax - b.playMinutesMax);
  return result;
});
function reset() {
  query.value = '';
  category.value = '';
  players.value = '';
  time.value = '';
  difficulty.value = '';
  soldHere.value = false;
  inLibrary.value = false;
}
function markImageFailed(url: string) {
  failedImages.value = new Set([...failedImages.value, url]);
}
</script>

<template>
  <div id="top" class="storefront">
    <p v-if="loginFailed" class="login-alert" role="alert">
      {{ t('Sign-in failed. Open Account to try again.') }}
    </p>
    <section id="collection" aria-labelledby="collection-title">
      <div ref="catalogHeading" class="catalog-heading">
        <h1 id="collection-title" class="sr-only">
          {{ t('Shop board games') }}
        </h1>
        <StoreContext />
      </div>
      <div class="search-bar">
        <label class="search-field"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            aria-hidden="true"
          >
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 4.5 4.5" /></svg
          ><input
            ref="searchInput"
            v-model="query"
            type="search"
            :aria-label="t('Search games')"
            :placeholder="t('Search games, designers, mechanics…')"
        /></label>
      </div>
      <div ref="catalogLayout" class="catalog-layout">
        <aside
          id="desktop-catalog-filters"
          class="filters"
          :aria-label="t('Filter games')"
        ></aside>
        <div class="catalog-results">
          <div class="results-toolbar">
            <div class="results-count" role="status">
              <strong>{{
                category ? categoryLabel(category) : t('All games')
              }}</strong
              ><span v-if="catalog.data.value">{{
                t(filtered.length === 1 ? '{count} game' : '{count} games', {
                  count: filtered.length,
                })
              }}</span>
            </div>
            <div class="results-controls">
              <button
                class="mobile-filter-trigger"
                type="button"
                aria-haspopup="dialog"
                aria-controls="catalog-filter-dialog"
                @click="openFilters"
              >
                {{ t('Filters')
                }}<span v-if="activeCount"> ({{ activeCount }})</span>
              </button>
              <div class="sort-control">
                <span aria-hidden="true">{{ t('Sort by') }}</span>
                <AppSelect
                  v-model="sort"
                  :label="t('Sort games')"
                  :options="sortOptions"
                />
              </div>
              <div class="view-switch" :aria-label="t('Display games')">
                <button
                  type="button"
                  :aria-label="t('Grid view')"
                  :aria-pressed="view === 'grid'"
                  @click="view = 'grid'"
                >
                  <svg
                    viewBox="0 0 20 20"
                    fill="currentColor"
                    aria-hidden="true"
                  >
                    <path
                      d="M2 2h6v6H2zm10 0h6v6h-6zM2 12h6v6H2zm10 0h6v6h-6z"
                    />
                  </svg></button
                ><button
                  type="button"
                  :aria-label="t('List view')"
                  :aria-pressed="view === 'list'"
                  @click="view = 'list'"
                >
                  <svg
                    viewBox="0 0 20 20"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <path d="M2 4h3m3 0h10M2 10h3m3 0h10M2 16h3m3 0h10" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <TransitionGroup
            v-if="
              !catalog.isPending.value &&
              !catalog.isError.value &&
              filtered.length
            "
            :key="view"
            name="catalog"
            tag="div"
            class="game-grid"
            :class="{ 'list-view': view === 'list' }"
          >
            <article
              v-for="(game, index) in filtered"
              :key="game.id"
              class="game-card"
              :data-game="game.id"
            >
              <RouterLink
                class="cover-button"
                :to="{ name: 'GameProduct', params: { gameId: game.id } }"
                :aria-label="t('View {name}', { name: game.name })"
              >
                <img
                  v-if="imageUrl(game) && !failedImages.has(imageUrl(game)!)"
                  :src="imageUrl(game)"
                  :alt="cover(game)?.altText || game.name"
                  width="360"
                  height="360"
                  :loading="index < 4 ? 'eager' : 'lazy'"
                  @error="markImageFailed(imageUrl(game)!)"
                />
                <span v-else class="cover-fallback">{{ game.name }}</span>
              </RouterLink>
              <div class="game-info">
                <p class="game-category">
                  {{ categoryLabel(game.categories[0] || '') }}
                </p>
                <h3>
                  <RouterLink
                    :to="{ name: 'GameProduct', params: { gameId: game.id } }"
                    >{{ game.name }}</RouterLink
                  >
                </h3>
                <p class="game-meta">
                  <span>{{
                    t('{range} players', {
                      range: range([game.minPlayers, game.maxPlayers]),
                    })
                  }}</span
                  ><span>{{
                    t('{range} min', {
                      range: range([game.playMinutesMin, game.playMinutesMax]),
                    })
                  }}</span
                  ><span>{{
                    game.difficulty
                      ? t(
                          {
                            EASY: 'Easy',
                            MEDIUM: 'Medium',
                            ADVANCED: 'Advanced',
                          }[game.difficulty],
                        )
                      : t('Experience not specified')
                  }}</span>
                </p>
                <p class="list-description">
                  {{ localizedDescription(game, locale) }}
                </p>
              </div>
            </article>
          </TransitionGroup>
          <div
            v-else-if="catalog.isPending.value"
            class="empty-state"
            role="status"
          >
            {{ t('Loading games…') }}
          </div>
          <div
            v-else-if="catalog.isError.value"
            class="empty-state"
            role="alert"
          >
            <h2>{{ t('Games could not be loaded.') }}</h2>
            <button type="button" @click="catalog.refetch()">
              {{ t('Try again') }}
            </button>
          </div>
          <div v-else class="empty-state">
            <svg
              class="empty-art"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <circle cx="10.5" cy="10.5" r="6.5" />
              <path d="m16 16 4.5 4.5" />
            </svg>
            <h2>{{ t('No matching games') }}</h2>
            <p>{{ t('Change your search or filters to see more games.') }}</p>
            <button type="button" @click="reset">
              {{ t('Clear search & filters') }}
            </button>
          </div>
          <div v-if="catalog.data.value" class="shelf-end">
            <p>
              {{
                t('Showing {count} of {total} games', {
                  count: filtered.length,
                  total: games.length,
                })
              }}
            </p>
            <button v-if="activeCount || query" type="button" @click="reset">
              {{ t('Clear filters') }}
            </button>
          </div>
        </div>
      </div>
    </section>
    <dialog
      id="catalog-filter-dialog"
      ref="filterDialog"
      class="catalog-filter-dialog"
      :aria-label="t('Filter games')"
      @click="$event.target === filterDialog && filterDialog?.close()"
    >
      <div class="filter-dialog-heading">
        <h2>{{ t('Filter games') }}</h2>
        <button
          type="button"
          :aria-label="t('Close filters')"
          @click="filterDialog?.close()"
        >
          ✕
        </button>
      </div>
      <div id="mobile-catalog-filters" class="filter-dialog-content"></div>
      <div class="filter-dialog-footer">
        <button type="button" @click="filterDialog?.close()">
          {{ t('Show {count} games', { count: filtered.length }) }}
        </button>
      </div>
    </dialog>
    <!-- Move one set of controls between the desktop sidebar and native mobile dialog. -->
    <Teleport
      defer
      :to="compact ? '#mobile-catalog-filters' : '#desktop-catalog-filters'"
    >
      <div class="filter-fields">
        <div class="filter-heading">
          <h2>{{ t('Browse games') }}</h2>
          <button
            v-if="activeCount || query"
            type="button"
            class="reset-link"
            @click="reset"
          >
            {{ t('Reset all') }}
          </button>
        </div>
        <nav class="category-list" :aria-label="t('Game categories')">
          <button
            type="button"
            :aria-pressed="category === ''"
            @click="category = ''"
          >
            <span>{{ t('All categories') }}</span
            ><span>{{ games.length }}</span>
          </button>
          <button
            v-for="item in categories"
            :key="item"
            type="button"
            :aria-pressed="category === item"
            @click="category = item"
          >
            <span>{{ categoryLabel(item) }}</span
            ><span>{{
              games.filter((game) => game.categories.includes(item)).length
            }}</span>
          </button>
        </nav>
        <div class="table-filters">
          <div
            v-if="context.stockBranch"
            class="branch-filters"
            role="group"
            :aria-label="t('At this store')"
          >
            <h2>{{ t('At this store') }}</h2>
            <label class="branch-filter-check">
              <AppCheckbox v-model="soldHere" :label="t('Sold here')" />
              <span>{{ t('Sold here') }}</span>
            </label>
            <label v-if="context.branch" class="branch-filter-check">
              <AppCheckbox v-model="inLibrary" :label="t('In this library')" />
              <span>{{ t('In this library') }}</span>
            </label>
          </div>
          <h2>{{ t('For your table') }}</h2>
          <div class="filter-field">
            <span>{{ t('Players') }}</span>
            <AppSelect
              v-model="players"
              block
              name="players"
              :label="t('Players')"
              :options="playerOptions"
            />
          </div>
          <div class="filter-field">
            <span>{{ t('Time at the table') }}</span>
            <AppSelect
              v-model="time"
              block
              name="time"
              :label="t('Time at the table')"
              :options="timeOptions"
            />
          </div>
          <p class="filter-hint">{{ t('Includes teaching & setup.') }}</p>
          <div class="filter-field">
            <span>{{ t('Experience level') }}</span>
            <AppSelect
              v-model="difficulty"
              block
              name="difficulty"
              :label="t('Experience level')"
              :options="difficultyOptions"
            />
          </div>
        </div>
      </div>
    </Teleport>
    <footer>
      <a href="#top">The Parlor</a
      ><a href="#collection">{{ t('Back to games ↑') }}</a>
    </footer>
  </div>
</template>

<style scoped>
.storefront {
  --catalog-search-height: 64px;
  --ink: #202e27;
  --muted: #4e5b53;
  --line: #d3d9d4;
  --control-border: #829087;
  --surface: #fff;
  --accent: #244c38;
  --ease: cubic-bezier(0.2, 0.7, 0.2, 1);
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.5;
}
button,
a,
select,
input {
  -webkit-tap-highlight-color: transparent;
  outline-offset: 3px;
}
button,
select {
  cursor: pointer;
}
button {
  text-align: left;
}
button:focus-visible,
a:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--accent);
}
button,
a,
select {
  transition:
    color 140ms ease,
    background-color 140ms ease,
    border-color 140ms ease,
    box-shadow 140ms ease;
}
button:active {
  translate: 0 1px;
}
.catalog-heading {
  display: block;
}
h1 {
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.25;
}
.search-bar {
  position: sticky;
  top: var(--parlor-header-height, 64px);
  z-index: 30;
  height: var(--catalog-search-height);
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  background: #fafbf9;
  display: flex;
  gap: 20px;
  justify-content: space-between;
}
.search-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  border: 1px solid var(--control-border);
  border-radius: 4px;
  background: var(--surface);
  padding: 0 14px;
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease;
}
.search-field svg {
  width: 19px;
  height: 19px;
  flex-shrink: 0;
  color: var(--muted);
}
.search-field input {
  width: 100%;
  min-width: 0;
  padding: 12px 0;
  font-size: 14px;
}
.search-field input::placeholder {
  color: #56635b;
  opacity: 1;
}
.search-field:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px #244c3826;
}
.search-field input:focus {
  outline: none;
}
.catalog-layout {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 30px;
}
.filters {
  position: sticky;
  --filter-sticky-top: calc(
    var(--parlor-header-height, 64px) + var(--catalog-search-height)
  );
  top: var(--filter-sticky-top);
  align-self: start;
  max-height: calc(
    100dvh - max(var(--filter-sticky-top), var(--catalog-layout-top, 0px)) -
      16px
  );
  overflow-y: auto;
  overscroll-behavior-y: contain;
  scrollbar-width: thin;
  min-width: 0;
  /* Keep the category highlight's bleed and focus rings inside the scrollport. */
  margin-left: -10px;
  padding: 24px 24px 24px 10px;
  border-right: 1px solid var(--line);
}
.filter-heading {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.filters h2 {
  font-size: 14px;
  font-weight: 600;
}
.reset-link {
  font-size: 12px;
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.category-list {
  display: grid;
  gap: 3px;
  margin-left: -10px;
  margin-right: -6px;
}
.category-list button {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  border-radius: 3px;
  padding: 9px 10px;
  color: #38493e;
  font-size: 13px;
}
.category-list button > span:last-child {
  color: var(--muted);
  font-size: 12px;
}
.category-list button:hover {
  background: #e9ede9;
}
.category-list button[aria-pressed='true'] {
  background: #dce7df;
  color: #1c422c;
  font-weight: 600;
}
.table-filters {
  display: grid;
  gap: 17px;
  padding-top: 23px;
  border-top: 1px solid var(--line);
  margin-top: 22px;
}
.branch-filters {
  display: grid;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}
.table-filters label.branch-filter-check {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
}
.table-filters .filter-field {
  display: grid;
  gap: 7px;
  font-size: 13px;
  color: #34473a;
}
.filter-hint {
  margin-top: -10px;
  font-size: 12px;
  color: var(--muted);
}
.catalog-results {
  min-width: 0;
  padding-top: 22px;
}
.results-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: 34px;
}
.results-count {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.results-count strong {
  font-size: 18px;
  font-weight: 600;
}
.results-count > span {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.results-controls {
  display: flex;
  gap: 16px;
  align-items: center;
}
.sort-control {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
  /* A toolbar control, smaller than the filter fields. */
  --ui-control-height: 32px;
  --ui-control-font-size: 13px;
}
.view-switch {
  display: flex;
  gap: 4px;
  border-left: 1px solid var(--line);
  padding-left: 12px;
}
.view-switch button {
  border: 1px solid transparent;
  border-radius: 3px;
  padding: 8px;
  color: #465a4c;
}
.view-switch button:hover {
  background: #e9ede9;
}
.view-switch button[aria-pressed='true'] {
  background: #dce7df;
  border-color: #829087;
  color: #244c38;
}
.view-switch svg {
  width: 15px;
  height: 15px;
}
.game-grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 28px 20px;
  margin-top: 20px;
  animation: view-in 160ms var(--ease);
}
.game-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.cover-button {
  position: relative;
  flex-shrink: 0;
  display: flex;
  width: 100%;
  aspect-ratio: 1.2;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 3px;
  background: #f0f2ef;
}
.cover-button img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  mix-blend-mode: multiply;
  padding: 16px 18px 36px;
  transition: transform 220ms var(--ease);
}
@media (hover: hover) {
  .cover-button:hover img {
    transform: translateY(-3px) scale(1.025);
  }
}
.cover-button:focus-visible {
  outline-offset: -3px;
}
.cover-fallback {
  padding: 20px;
  font-size: 24px;
  font-weight: 600;
}
.game-info {
  padding-top: 11px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.game-category {
  color: var(--muted);
  font-size: 12px;
}
h3 {
  font-size: 15px;
  font-weight: 600;
}
.game-info h3 {
  margin-top: 3px;
  line-height: 1.4;
}
.game-info h3 a:hover {
  color: #1c5f3d;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.game-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 0;
  margin-top: 7px;
  margin-bottom: 12px;
  color: var(--muted);
  font-size: 12px;
}
.game-meta > span + span::before {
  content: '·';
  margin-inline: 6px;
}
.list-description {
  display: none;
}
.list-view {
  grid-template-columns: 1fr;
  gap: 0;
}
.list-view .game-card {
  display: grid;
  grid-template-columns: 116px 1fr;
  gap: 20px;
  padding-block: 16px;
  border-bottom: 1px solid var(--line);
}
.list-view .game-card:first-child {
  padding-top: 0;
}
.list-view .game-info {
  padding-top: 0;
  display: block;
}
.list-view .game-meta {
  margin-bottom: 0;
}
.list-view .cover-button {
  aspect-ratio: 1;
}
.list-view .cover-button img {
  padding: 10px;
}
.list-view .list-description {
  display: block;
  margin-top: 8px;
  max-width: 700px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted);
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 72px 24px 88px;
}
.empty-art {
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
  color: var(--muted);
  opacity: 0.55;
}
.empty-state h2 {
  font-size: 20px;
  font-weight: 600;
  max-width: 420px;
}
.empty-state p {
  margin-top: 10px;
  color: var(--muted);
}
.empty-state button {
  margin-top: 20px;
  background: var(--accent);
  color: #fff;
  border-radius: 3px;
  padding: 11px 16px;
}
.empty-state button:hover {
  background: #173c29;
}
.shelf-end {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 0;
  margin-top: 24px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}
.shelf-end button {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}
footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border-top: 1px solid var(--line);
  padding-top: 18px;
  font-size: 12px;
  color: var(--muted);
}
footer > a:first-child {
  color: var(--ink);
  font-weight: 600;
}
footer a:hover {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.login-alert {
  padding: 15px;
  margin-bottom: 20px;
  border: 1px solid #9c6a56;
  color: #823020;
  background: #fff6ef;
}
.catalog-enter-active {
  transition:
    opacity 160ms ease,
    transform 160ms var(--ease);
}
.catalog-move {
  transition: transform 220ms var(--ease);
}
.catalog-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.catalog-leave-active {
  display: none;
}
@keyframes view-in {
  from {
    opacity: 0.6;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.mobile-filter-trigger {
  display: none;
  align-items: center;
  min-height: 38px;
  border: 1px solid var(--control-border);
  border-radius: 3px;
  padding: 6px 10px;
  white-space: nowrap;
}
.catalog-filter-dialog {
  position: fixed;
  inset: 0 0 0 auto;
  width: min(380px, 100%);
  max-width: 100%;
  height: 100dvh;
  max-height: 100dvh;
  margin: 0 0 0 auto;
  padding: 0;
  border: 0;
  border-left: 1px solid var(--control-border);
  background: #fafbf9;
  color: var(--ink);
  transform: translateX(100%);
  transition:
    transform 250ms ease,
    display 250ms allow-discrete,
    overlay 250ms allow-discrete;
}
.catalog-filter-dialog[open] {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  transform: translateX(0);
}
.catalog-filter-dialog::backdrop {
  background: #14221d66;
}
@starting-style {
  .catalog-filter-dialog[open] {
    transform: translateX(100%);
  }
}
.filter-dialog-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}
.filter-dialog-heading h2 {
  font-size: 18px;
  font-weight: 600;
}
.filter-dialog-heading button {
  width: 44px;
  height: 44px;
}
.filter-dialog-content {
  overflow-y: auto;
  overscroll-behavior-y: contain;
  padding: 20px;
}
.filter-dialog-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--line);
}
.filter-dialog-footer button {
  width: 100%;
  padding: 12px;
  background: var(--accent);
  color: white;
  border-radius: 3px;
}
:global(html:has(.catalog-filter-dialog[open])) {
  overflow: hidden;
}
.storefront :where(a, button, input, select) {
  scroll-margin-top: calc(
    var(--parlor-header-height, 64px) + var(--catalog-search-height) + 12px
  );
}
@media (max-width: 1200px) {
  .search-bar {
    gap: 12px;
  }
  .search-field input {
    font-size: 13px;
  }
  .game-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .catalog-layout {
    grid-template-columns: 208px minmax(0, 1fr);
    gap: 24px;
  }
  .filters {
    padding-right: 20px;
  }
}

@media (max-width: 900px) {
  .game-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 18px;
  }
  .results-controls {
    gap: 8px;
  }
  .sort-control {
    font-size: 0;
  }
  .view-switch {
    padding-left: 7px;
  }
}
@media (max-width: 700px) {
  h1 {
    font-size: 25px;
  }
  .search-field {
    width: 100%;
    flex: auto;
  }
  .catalog-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
  .filters {
    display: none;
  }
  .mobile-filter-trigger {
    display: inline-flex;
  }
  .catalog-results {
    padding-top: 22px;
  }
  .results-toolbar {
    flex-wrap: wrap;
    gap: 10px;
  }
  .results-count {
    gap: 8px;
  }
  .results-count strong {
    font-size: 17px;
  }
  .results-controls {
    margin-left: auto;
  }
  .view-switch {
    gap: 2px;
  }
  .view-switch button {
    padding: 7px;
  }
  .game-grid {
    gap: 24px 14px;
    margin-top: 18px;
  }
  .cover-button img {
    padding: 12px 12px 30px;
  }
  .game-info h3 {
    font-size: 14px;
  }
  .game-category,
  .game-meta {
    font-size: 12px;
  }
  .game-meta > span + span::before {
    margin-inline: 4px;
  }
  .list-view {
    grid-template-columns: 1fr;
  }
  .list-view .game-card {
    grid-template-columns: 90px 1fr;
    gap: 14px;
  }
  .list-view .list-description {
    font-size: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition: none !important;
    animation: none !important;
    scroll-behavior: auto !important;
  }
  .cover-button:hover img {
    transform: none;
  }
  button:active {
    translate: none;
  }
}
</style>
