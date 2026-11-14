<script setup lang="ts">
// Catalog details and editions come from the catalog API; price, sale stock and
// play-library membership come from the branch chosen in the store selector.
// Rental availability is never shown here: it depends on a visit date and time.
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { formatMinor, range } from '../data/catalog';
import { useStoreText } from '../data/store-text';
import {
  useProduct,
  useTaxonomy,
  localizedDescription,
  taxonomyLabel,
  imageUrl,
  cover,
} from '../api/catalog';
import StoreContext from '../components/StoreContext.vue';
import StoreAccordion from '../components/StoreAccordion.vue';
import BranchAvailability from '../components/BranchAvailability.vue';
import { useBranchOffering } from '../api/inventory';
import { useStoreContextStore } from '../stores/store-context';
import { useCartStore } from '../stores/cart';
import { AppNumberStepper, AppSelect } from '../../../shared/ui';

const route = useRoute();
const { t, locale } = useStoreText();
const product = useProduct(() => String(route.params['gameId'] || ''));
const taxonomy = useTaxonomy();
const game = product.game.data;
const imageFailed = ref('');
const notFound = computed(
  () =>
    !product.valid.value || product.game.error.value?.response?.status === 404,
);
const categoryLabel = (key: string) =>
  taxonomyLabel(taxonomy.data.value?.categories, key, locale.value);
const mechanicLabel = (key: string) =>
  taxonomyLabel(taxonomy.data.value?.mechanics, key, locale.value);
const description = computed(() =>
  game.value ? localizedDescription(game.value, locale.value) : '',
);
function languageName(code: string) {
  try {
    return (
      new Intl.DisplayNames([locale.value], { type: 'language' }).of(code) ||
      code
    );
  } catch {
    return code;
  }
}
const originalTitle = document.title;
watch(
  [
    () => game.value?.name,
    () => product.game.isPending.value,
    notFound,
    locale,
  ],
  () => {
    document.title = `${game.value?.name || t(notFound.value ? 'Game not found' : 'Loading game…')} | The Parlor`;
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  document.title = originalTitle;
});

// Price and stock follow the browsing preference only; the cart's own fulfillment
// choice never changes what this page shows.
const context = useStoreContextStore();
const cart = useCartStore();
const storeContext = ref<InstanceType<typeof StoreContext>>();
const offering = useBranchOffering(
  () => context.stockBranch?.id,
  () => game.value?.id,
);
const stocked = computed(() => offering.data.value?.editions ?? []);
const selectedEditionId = ref('');
const selected = computed(
  () =>
    stocked.value.find(
      (edition) => edition.editionId === selectedEditionId.value,
    ) ?? stocked.value[0],
);
// Keep the select on a real option when the store, and so the stocked editions, change.
watch(
  stocked,
  (editions) => {
    if (!editions.some((e) => e.editionId === selectedEditionId.value))
      selectedEditionId.value = editions[0]?.editionId ?? '';
  },
  { immediate: true },
);
const quantity = ref(1);
const maxQuantity = computed(() =>
  Math.min(99, selected.value?.saleAvailable ?? 0),
);
const added = ref<'added' | 'stock' | 'error' | ''>('');
watch([() => selected.value?.editionId, () => context.stockBranch?.id], () => {
  quantity.value = 1;
  added.value = '';
});
const otherBranches = ref<InstanceType<typeof BranchAvailability>>();
const stockState = computed(() => {
  const available = selected.value?.saleAvailable ?? 0;
  if (available === 0) return { tone: 'out', text: t('Sold out') };
  if (available <= 3)
    return { tone: 'low', text: t('Only {count} left', { count: available }) };
  return { tone: 'in', text: t('In stock') };
});
const inLibrary = computed(
  () => !!context.branch && !!offering.data.value?.rentalEnabled,
);
const editionOptions = computed(() =>
  stocked.value.map((edition) => ({
    value: edition.editionId,
    label: editionLabel(edition),
  })),
);
// Book a table to play this game, at the chosen branch when its library has it.
const playLink = computed(() => ({
  path: '/reservations/new',
  query: {
    game: game.value?.id,
    ...(context.branch && offering.data.value?.rentalEnabled
      ? { branch: context.branch.id }
      : {}),
  },
}));
function editionLabel(edition: { languageCode: string; editionKind: string }) {
  const language = languageName(edition.languageCode);
  return edition.editionKind === 'EXPANSION'
    ? `${language} · ${t('Expansion')}`
    : language;
}
async function addToCart() {
  const edition = selected.value;
  const branch = context.stockBranch;
  if (!edition || !branch) return;
  if (
    !Number.isSafeInteger(quantity.value) ||
    quantity.value < 1 ||
    quantity.value > maxQuantity.value
  ) {
    added.value = 'stock';
    return;
  }
  added.value = '';
  const ok = await cart.add(edition.editionId, quantity.value, {
    method: context.choice === 'online' ? 'shipping' : 'pickup',
    branchId: branch.id,
  });
  added.value = ok ? 'added' : cart.lastFailure === 'stock' ? 'stock' : 'error';
  if (ok) quantity.value = 1;
}
</script>
<template>
  <div class="product-page">
    <StoreContext ref="storeContext" />
    <nav class="product-breadcrumb" :aria-label="t('Breadcrumb')">
      <RouterLink :to="{ name: 'HomeIndex' }">{{ t('All games') }}</RouterLink>
      <span aria-hidden="true">/</span
      ><span>{{
        game?.name || t(notFound ? 'Game not found' : 'Loading game…')
      }}</span>
    </nav>
    <section v-if="notFound" class="product-missing">
      <h1>{{ t('Game not found') }}</h1>
      <p>{{ t('This game is not in the catalog.') }}</p>
      <RouterLink :to="{ name: 'HomeIndex' }">{{
        t('Back to games')
      }}</RouterLink>
    </section>
    <section
      v-else-if="product.game.isPending.value"
      class="product-missing"
      role="status"
    >
      {{ t('Loading game…') }}
    </section>
    <section
      v-else-if="product.game.isError.value"
      class="product-missing"
      role="alert"
    >
      <h1>{{ t('This game could not be loaded.') }}</h1>
      <button type="button" @click="product.game.refetch()">
        {{ t('Try again') }}
      </button>
    </section>
    <section
      v-else-if="game"
      class="product-main"
      aria-labelledby="product-title"
    >
      <div class="product-media">
        <img
          v-if="imageUrl(game) && imageFailed !== imageUrl(game)"
          :key="imageUrl(game)"
          :src="imageUrl(game)"
          :alt="cover(game)?.altText || game.name"
          width="720"
          height="720"
          fetchpriority="high"
          @error="imageFailed = imageUrl(game)!"
        />
        <span v-else class="image-fallback">{{ game.name }}</span>
      </div>
      <div class="product-details">
        <p class="product-category">
          {{ game.categories.map(categoryLabel).join(' · ') }}
        </p>
        <h1 id="product-title">{{ game.name }}</h1>
        <p v-if="game.designer" class="product-designer">
          {{ t('By {name}', { name: game.designer }) }}
        </p>
        <p v-if="description" class="product-intro">{{ description }}</p>
        <ul class="product-facts" :aria-label="t('Game overview')">
          <li>
            {{
              t('{range} players', {
                range: range([game.minPlayers, game.maxPlayers]),
              })
            }}
          </li>
          <li>
            {{
              t('{range} min', {
                range: range([game.playMinutesMin, game.playMinutesMax]),
              })
            }}
          </li>
          <li v-if="game.difficulty">
            {{
              t(
                { EASY: 'Easy', MEDIUM: 'Medium', ADVANCED: 'Advanced' }[
                  game.difficulty
                ],
              )
            }}
          </li>
        </ul>
        <section class="product-purchase" :aria-label="t('Price and stock')">
          <p v-if="!context.stockBranch" class="muted">
            {{
              context.choice === 'online'
                ? t('Online orders are not available right now.')
                : t('Choose a store to see prices and stock.')
            }}
            <button
              v-if="context.choice !== 'online'"
              type="button"
              class="link-button"
              @click="storeContext?.open()"
            >
              {{ t('Choose a store') }}
            </button>
          </p>
          <p v-else-if="offering.isPending.value" class="muted" role="status">
            {{ t('Loading price and stock…') }}
          </p>
          <div v-else-if="offering.isError.value" role="alert">
            <p>{{ t('Price and stock could not be loaded.') }}</p>
            <button
              type="button"
              class="link-button"
              @click="offering.refetch()"
            >
              {{ t('Try again') }}
            </button>
          </div>
          <p
            v-else-if="!selected || !offering.data.value?.saleEnabled"
            class="muted"
          >
            {{
              context.choice === 'online'
                ? t('Not sold online.')
                : t('Not sold at {branch}.', {
                    branch: t(context.stockBranch.name),
                  })
            }}
          </p>
          <form v-else class="purchase-form" @submit.prevent="addToCart">
            <div class="price-row">
              <p class="product-price">
                {{ formatMinor(selected.salePrice.amountMinor) }}
              </p>
              <span class="stock-badge" :class="`stock-${stockState.tone}`">{{
                stockState.text
              }}</span>
            </div>
            <p class="stock-line">
              <span v-if="stocked.length === 1" class="purchase-edition"
                >{{ t('Edition') }}: {{ editionLabel(selected) }} ·
              </span>
              <span class="stock-place">{{
                context.choice === 'online'
                  ? t('For delivery')
                  : t('Pickup at {branch}', {
                      branch: t(context.stockBranch.name),
                    })
              }}</span>
            </p>
            <div v-if="stocked.length > 1" class="edition-field">
              <span>{{ t('Edition') }}</span>
              <AppSelect
                v-model="selectedEditionId"
                block
                name="edition"
                :label="t('Edition')"
                :options="editionOptions"
              />
            </div>
            <div v-if="maxQuantity > 0" class="buy-row">
              <div class="quantity-field">
                <AppNumberStepper
                  v-model="quantity"
                  :label="t('Quantity')"
                  :min="1"
                  :max="maxQuantity"
                />
              </div>
              <button type="submit" class="add-to-cart" :disabled="cart.busy">
                {{ t('Add to cart') }}
              </button>
            </div>
            <div v-else class="sold-out">
              <p>
                {{
                  context.choice === 'online'
                    ? t('This edition is sold out online.')
                    : t('This edition is sold out at {branch}.', {
                        branch: t(context.stockBranch.name),
                      })
                }}
              </p>
              <button
                type="button"
                class="secondary-action"
                @click="otherBranches?.open()"
              >
                {{ t('Check other branches') }}
              </button>
            </div>
            <p v-if="added === 'added'" class="purchase-note" role="status">
              {{ t('Added to your cart.') }}
              <RouterLink to="/cart">{{ t('View cart') }}</RouterLink>
            </p>
            <p v-else-if="added === 'stock'" class="purchase-note" role="alert">
              {{ t('That quantity is not available for your cart.') }}
            </p>
            <p v-else-if="added === 'error'" class="purchase-note" role="alert">
              {{ t('The cart could not be updated. Try again.') }}
            </p>
          </form>
          <aside class="play-card" :aria-label="t('Play in store')">
            <svg
              class="play-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              aria-hidden="true"
            >
              <rect x="3.5" y="3.5" width="17" height="17" rx="3" />
              <circle cx="8.5" cy="8.5" r="1.2" fill="currentColor" />
              <circle cx="15.5" cy="15.5" r="1.2" fill="currentColor" />
              <circle cx="12" cy="12" r="1.2" fill="currentColor" />
            </svg>
            <div class="play-copy">
              <h2>
                {{
                  inLibrary
                    ? t('Play it at {branch}', {
                        branch: t(context.branch!.name),
                      })
                    : t('Play it in store')
                }}
              </h2>
              <p
                v-if="context.branch && offering.data.value"
                class="library-line"
              >
                {{
                  offering.data.value.rentalEnabled
                    ? t('In the rental library')
                    : t('Not in this library')
                }}<span
                  v-if="
                    offering.data.value.rentalEnabled &&
                    offering.data.value.staffCanTeach
                  "
                >
                  · {{ t('Staff can teach this game') }}</span
                >
              </p>
              <p class="play-hint">
                {{
                  inLibrary
                    ? t('Book a table and choose this game for your visit.')
                    : t('Book a table at a branch whose library has it.')
                }}
              </p>
            </div>
            <RouterLink class="play-reserve" :to="playLink">{{
              t('Reserve a table to play')
            }}</RouterLink>
          </aside>
        </section>
        <div class="product-accordions">
          <BranchAvailability
            v-if="context.stockBranch"
            ref="otherBranches"
            :game-id="game.id"
            :variant-id="
              selected?.editionId || product.editions.data.value?.[0]?.id
            "
          />
          <StoreAccordion :key="game.id" :title="t('Game details')">
            <dl>
              <div v-if="game.minAge != null">
                <dt>{{ t('Minimum age') }}</dt>
                <dd>{{ game.minAge }}</dd>
              </div>
              <div>
                <dt>{{ t('Teaching / setup') }}</dt>
                <dd>
                  {{ game.teachMinutes ?? '—' }} /
                  {{ game.setupMinutes ?? '—' }} {{ t('min') }}
                </dd>
              </div>
              <div>
                <dt>{{ t('Mechanics') }}</dt>
                <dd>
                  {{ game.mechanics.map(mechanicLabel).join(', ') || '—' }}
                </dd>
              </div>
            </dl>
          </StoreAccordion>
          <StoreAccordion :title="t('Editions')">
            <p v-if="product.editions.isPending.value" role="status">
              {{ t('Loading editions…') }}
            </p>
            <div v-else-if="product.editions.isError.value" role="alert">
              <p>{{ t('Editions could not be loaded.') }}</p>
              <button type="button" @click="product.editions.refetch()">
                {{ t('Try again') }}
              </button>
            </div>
            <ul v-else-if="product.editions.data.value?.length">
              <li
                v-for="edition in product.editions.data.value"
                :key="edition.id"
              >
                {{ languageName(edition.languageCode) }} ·
                {{
                  t(
                    edition.editionKind === 'BASE_GAME'
                      ? 'Base game'
                      : 'Expansion',
                  )
                }}
              </li>
            </ul>
            <p v-else>{{ t('No editions are listed.') }}</p>
          </StoreAccordion>
        </div>
      </div>
    </section>
  </div>
</template>
<style scoped>
.product-page {
  --ink: #202e27;
  --muted: #4e5b53;
  --line: #d3d9d4;
  --accent: #244c38;
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.5;
}
.product-breadcrumb {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
  margin: 22px 0 26px;
  color: var(--muted);
  font-size: 12px;
}
.product-breadcrumb a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.product-main {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 1fr);
  gap: clamp(32px, 6vw, 88px);
  align-items: start;
}
.product-media {
  position: sticky;
  top: calc(var(--parlor-header-height, 64px) + 28px);
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  background: #f0f2ef;
  min-width: 0;
}
.product-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  mix-blend-mode: multiply;
  padding: clamp(24px, 5vw, 64px);
}
.image-fallback {
  font-size: 32px;
  padding: 32px;
}
.product-details {
  min-width: 0;
}
.product-category,
.product-designer {
  font-size: 13px;
  color: var(--muted);
}
h1 {
  font-size: clamp(26px, 2.5vw, 36px);
  line-height: 1.2;
  font-weight: 600;
  margin-top: 8px;
  letter-spacing: -0.025em;
}
.product-designer {
  margin-top: 9px;
}
.product-intro {
  margin-top: 18px;
  line-height: 1.8;
}
.product-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  list-style: none;
  padding: 0;
  margin: 16px 0 24px;
  color: var(--muted);
  font-size: 13px;
}
label {
  display: grid;
  gap: 7px;
  min-width: 0;
  font-size: 13px;
}
select,
input {
  min-width: 0;
  width: 100%;
  background: #fff;
  border: 1px solid #829087;
  padding: 11px;
  border-radius: 3px;
  color: var(--ink);
  font-size: 14px;
}
select {
  cursor: pointer;
}
button {
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}
button:disabled {
  background: #e2e6e2;
  color: var(--muted);
  border-color: #b5bfb6;
  cursor: not-allowed;
}
h2 {
  font-size: 14px;
  font-weight: 600;
}
.product-purchase {
  display: grid;
  gap: 18px;
  margin: 4px 0 8px;
  padding: 22px 0 24px;
  /* The accordions below draw the closing rule. */
  border-top: 1px solid var(--line);
}
.purchase-form {
  display: grid;
  gap: 14px;
}
.price-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
}
.product-price {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.stock-badge {
  padding: 3px 10px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}
.stock-in {
  color: #2f6b45;
  background: #edf4ec;
}
.stock-low {
  color: #82551b;
  background: #fff4df;
}
.stock-out {
  color: #8a3b30;
  background: #fbf1ee;
}
.stock-line,
.library-line,
.play-hint {
  font-size: 13px;
  color: var(--muted);
}
.stock-line {
  margin-top: -6px;
}
.edition-field {
  display: grid;
  gap: 7px;
  min-width: 0;
  font-size: 13px;
}
.buy-row {
  display: flex;
  align-items: stretch;
  gap: 10px;
  --ui-control-height: 48px;
}
.buy-row .add-to-cart {
  flex: 1 1 auto;
  width: auto;
}
.sold-out {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #e6d4cf;
  border-radius: 4px;
  background: #fbf6f4;
  font-size: 13px;
}
.secondary-action {
  justify-self: start;
  padding: 9px 14px;
  border: 1px solid var(--accent);
  border-radius: 3px;
  background: #fff;
  color: var(--accent);
  font: inherit;
  font-weight: 600;
}
.secondary-action:hover {
  background: #e9f0ea;
}
.play-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 14px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #f3f6f3;
}
.play-icon {
  grid-row: span 2;
  width: 28px;
  height: 28px;
  color: var(--accent);
}
.play-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.play-copy h2 {
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
}
.play-reserve {
  grid-column: 2;
  justify-self: start;
  margin-top: 10px;
  padding: 10px 16px;
  border-radius: 3px;
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  text-decoration: none;
  transition: background-color 150ms ease;
}
.play-reserve:hover {
  background: #1b3a2b;
}
.add-to-cart {
  width: 100%;
  padding: 13px 16px;
  border: 1px solid var(--accent);
  border-radius: 3px;
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.add-to-cart:hover:not(:disabled) {
  background: #1b3a2b;
}
.purchase-note {
  font-size: 13px;
}
.purchase-note a,
.link-button {
  border: 0;
  padding: 0;
  background: none;
  color: var(--accent);
  font: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.muted {
  color: var(--muted);
  font-size: 12px;
  margin-top: 7px;
}
dl {
  padding-top: 14px;
  display: grid;
  gap: 12px;
  font-size: 13px;
}
dl > div {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 12px;
}
dt {
  color: var(--muted);
}
.product-missing {
  padding: 80px 0;
}
.product-missing p {
  margin: 18px 0;
}
.product-missing a {
  text-decoration: underline;
  text-underline-offset: 3px;
}
a:focus-visible,
button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}
@media (max-width: 700px) {
  .product-main {
    grid-template-columns: 1fr;
    gap: 28px;
  }
  .product-media {
    position: static;
    width: 100%;
    aspect-ratio: 1.15;
  }
  .product-media img {
    padding: 30px;
  }
  .product-breadcrumb {
    margin: 18px 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  button {
    transition: none;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .product-media img {
    animation: media-reveal 450ms cubic-bezier(0.215, 0.61, 0.355, 1);
  }
  .product-details {
    animation: details-reveal 350ms ease-out;
  }
  @keyframes media-reveal {
    from {
      opacity: 0;
      transform: scale(0.97);
    }
    to {
      opacity: 1;
      transform: scale(1);
    }
  }
  @keyframes details-reveal {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

.reserve-action-card {
  margin: 22px 0;
  padding: 18px 20px;
  background: #fffaf1;
  border: 1px solid #c6ad7a;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.action-card-text strong {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: #1a3325;
}
.action-card-text p {
  margin-top: 4px;
  font-size: 13px;
  color: #556358;
}
.reserve-game-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #203c30;
  color: #fff3dc;
  font-weight: 700;
  font-size: 13.5px;
  letter-spacing: 0.04em;
  padding: 12px 22px;
  border-radius: 4px;
  border: 1px solid #c6ad7a;
  text-decoration: none;
  transition: all 140ms ease;
  box-shadow: 0 2px 8px rgba(18, 38, 28, 0.12);
}
.reserve-game-btn:hover {
  background: #14281f;
  border-color: #dfc999;
  transform: translateY(-1px);
}
.btn-icon {
  font-size: 16px;
  color: #c6ad7a;
}
</style>
