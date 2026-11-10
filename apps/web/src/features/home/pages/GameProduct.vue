<script setup lang="ts">
// TODO(product-commerce-api): Catalog details/editions below are live API reads.
// Add purchasing only after branch edition prices/stock, server cart and checkout
// work; add rentals only with a visit interval and server availability/allocation.
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { range } from '../data/catalog';
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
import { useCartStore } from '../stores/cart';
import { variants } from '../data/retail';
import { retailStockAt } from '../data/branches';
import { formatPrice } from '../data/catalog';

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
// Catalog/edition identity is authoritative. Price, stock, branch membership and
// checkout remain unavailable until their own APIs are implemented; no fixture join.

const cart = useCartStore();
const buyQty = ref(1);
const addedSuccess = ref(false);

function getSlug(name: string) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

const matchedVariant = computed(() => {
  if (!game.value) return null;
  const gName = (game.value.name || '').toLowerCase();
  const gId = (game.value.id || '').toLowerCase();
  const slug = getSlug(game.value.name || '');
  return (
    variants.find(
      (v) =>
        v.gameId === slug ||
        v.gameId === gId ||
        v.gameId === gName ||
        gName.includes(v.gameId) ||
        slug.includes(v.gameId),
    ) ?? null
  );
});

const variantStock = computed(() => {
  if (!matchedVariant.value) return 0;
  return retailStockAt(matchedVariant.value.id, cart.fulfillmentBranchId);
});

const remainingStock = computed(() => {
  if (!matchedVariant.value) return 0;
  const inCart = cart.items.find((i) => i.variantId === matchedVariant.value?.id)?.quantity ?? 0;
  return Math.max(0, variantStock.value - inCart);
});

function handleAddToCart() {
  if (!matchedVariant.value) return;
  if (cart.method === 'pickup' && !cart.pickupBranchId) {
    cart.setFulfillment('shipping');
  }
  const ok = cart.add(matchedVariant.value.id, buyQty.value);
  if (ok) {
    addedSuccess.value = true;
    buyQty.value = 1;
    setTimeout(() => {
      addedSuccess.value = false;
    }, 2500);
  }
}

</script>
<template>
  <div class="product-page">
    <StoreContext />
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
        <p class="muted" role="status">
          {{ t('Price and stock information is unavailable.') }}
        </p>

        <!-- Order Game for Home Delivery -->
        <div v-if="matchedVariant" class="game-delivery-buy-box">
          <div class="delivery-buy-header">
            <div class="delivery-tag">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <rect x="1" y="3" width="15" height="13" />
                <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
                <circle cx="5.5" cy="18.5" r="2.5" />
                <circle cx="18.5" cy="18.5" r="2.5" />
              </svg>
              <span>{{ t('Available for Home Delivery') }}</span>
            </div>
            <strong class="delivery-price">{{ formatPrice(matchedVariant.price) }}</strong>
          </div>

          <div class="delivery-buy-actions">
            <div class="stepper">
              <button
                type="button"
                class="stepper-btn"
                :disabled="buyQty <= 1"
                :aria-label="t('Decrease quantity')"
                @click="buyQty > 1 && buyQty--"
              >
                −
              </button>
              <span class="stepper-count">{{ buyQty }}</span>
              <button
                type="button"
                class="stepper-btn"
                :disabled="buyQty >= 99"
                :aria-label="t('Increase quantity')"
                @click="buyQty++"
              >
                +
              </button>
            </div>

            <button
              type="button"
              class="btn-add-to-bag"
              :class="{ 'is-added': addedSuccess }"
              @click="handleAddToCart"
            >
              <svg v-if="!addedSuccess" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M5 7h14l1 14H4L5 7Z" />
                <path d="M8 8V6a4 4 0 0 1 8 0v2" />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              <span>{{ addedSuccess ? (locale === 'th-TH' ? 'เพิ่มลงตะกร้าแล้ว!' : 'Added!') : (locale === 'th-TH' ? 'ใส่ตะกร้า' : t('Add to Cart')) }}</span>
              <span v-if="!addedSuccess" class="bag-price">· {{ formatPrice(matchedVariant.price * buyQty) }}</span>
            </button>
            <RouterLink to="/cart" class="btn-goto-cart" v-if="addedSuccess || cart.count > 0">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M5 7h14l1 14H4L5 7Z" />
                <path d="M8 8V6a4 4 0 0 1 8 0v2" />
              </svg>
              <span>{{ locale === 'th-TH' ? 'ดูตะกร้า' : 'View Cart' }}</span>
              <span class="cart-pill-count">{{ cart.count }}</span>
            </RouterLink>
          </div>
        </div>
        <div class="product-accordions">
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

/* Delivery Buy Box */
.game-delivery-buy-box {
  margin: 18px 0 24px;
  padding: 18px 20px;
  background: #fffaf1;
  border: 1px solid #c6ad7a;
  border-radius: 6px;
  box-shadow: 0 4px 14px rgba(20, 36, 28, 0.08);
}

.delivery-buy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.delivery-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #1f5e3d;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  padding: 4px 10px;
  border-radius: 4px;
}

.delivery-price {
  font-size: 24px;
  font-weight: 800;
  color: #142a1f;
  letter-spacing: -0.01em;
}

.delivery-buy-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid #c6ad7a;
  border-radius: 4px;
  background: #ffffff;
  overflow: hidden;
}

.stepper-btn {
  width: 38px;
  height: 42px;
  background: transparent;
  border: none;
  font-size: 18px;
  font-weight: bold;
  color: #142a1f;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 140ms ease;
}

.stepper-btn:hover:not(:disabled) {
  background: #ebe3d3;
}

.stepper-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.stepper-count {
  width: 40px;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: #142a1f;
}

.btn-add-to-bag {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 42px;
  padding: 0 20px;
  background: #142a1f;
  border: 1px solid #c6ad7a;
  border-radius: 4px;
  color: #efe5d0;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
  box-shadow: 0 2px 6px rgba(20, 42, 31, 0.15);
}

.btn-add-to-bag:hover {
  background: #244c38;
  color: #ffffff;
  border-color: #dfc999;
}

.btn-add-to-bag svg {
  color: #c6ad7a;
}

.bag-price {
  color: #ffd98a;
}

.btn-add-to-bag.is-added {
  background: #1f5e3d;
  border-color: #34d399;
  color: #ffffff;
}

.btn-goto-cart {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 16px;
  background: transparent;
  border: 1px solid #c6ad7a;
  border-radius: 4px;
  color: #142a1f;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  transition: all 140ms ease;
  white-space: nowrap;
}

.btn-goto-cart:hover {
  background: #f7f1e5;
  border-color: #9f8448;
}

.cart-pill-count {
  background: #142a1f;
  color: #c6ad7a;
  font-size: 11px;
  font-weight: 800;
  padding: 2px 7px;
  border-radius: 10px;
}
</style>
