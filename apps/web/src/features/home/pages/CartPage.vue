<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { useCartStore } from '../stores/cart';
import { useOrdersStore } from '../stores/orders';
import { formatMinor } from '../data/catalog';
import { useGameCovers } from '../api/catalog';
import { useAuthStore } from '../../../shared/stores/auth';
import { useStoreText } from '../data/store-text';
import { AppCheckbox, AppNumberStepper } from '../../../shared/ui';

const router = useRouter();
const cart = useCartStore();
const ordersStore = useOrdersStore();
const { t, locale } = useStoreText();
const auth = useAuthStore();
const coverOf = useGameCovers();
const failedCovers = ref(new Set<string>());
onMounted(() => void cart.load());

function editionLabel(language: string) {
  try {
    return (
      new Intl.DisplayNames([locale.value], { type: 'language' }).of(
        language,
      ) || language
    );
  } catch {
    return language;
  }
}

/** Why a line blocks checkout at the cart's current fulfillment location. */
function issueText(line: (typeof cart.lines)[number]) {
  switch (line.issue) {
    case 'INSUFFICIENT_STOCK':
      return t('Only {count} available. Reduce the quantity.', {
        count: line.available,
      });
    case 'NOT_SOLD_AT_LOCATION':
      return cart.method === 'shipping'
        ? t('Not available for delivery')
        : t('Not available at {branch}', {
            branch: t(cart.fulfillmentBranch?.name || ''),
          });
    case 'EDITION_INACTIVE':
    case 'GAME_INACTIVE':
      return t('No longer sold. Remove this item.');
    default:
      return '';
  }
}

// Shopee-style Item Selection
const selectedVariantIds = ref<string[]>([]);
const initializedSelection = ref(false);

watch(
  () => cart.lines.map((l) => l.variantId),
  (currentIds) => {
    if (!initializedSelection.value) {
      selectedVariantIds.value = [...currentIds];
      initializedSelection.value = true;
    } else {
      const existing = new Set(selectedVariantIds.value);
      const prevIds = new Set(selectedVariantIds.value);
      for (const id of currentIds) {
        if (!prevIds.has(id)) {
          existing.add(id);
        }
      }
      selectedVariantIds.value = currentIds.filter((id) => existing.has(id));
    }
  },
  { immediate: true },
);

const isAllSelected = computed(
  () =>
    cart.lines.length > 0 &&
    cart.lines.every((l) => selectedVariantIds.value.includes(l.variantId)),
);

const isPartiallySelected = computed(
  () => selectedVariantIds.value.length > 0 && !isAllSelected.value,
);

function toggleSelectAll(checked: boolean) {
  if (checked) {
    selectedVariantIds.value = cart.lines.map((l) => l.variantId);
  } else {
    selectedVariantIds.value = [];
  }
}

function toggleSelectItem(variantId: string) {
  const idx = selectedVariantIds.value.indexOf(variantId);
  if (idx >= 0) {
    selectedVariantIds.value.splice(idx, 1);
  } else {
    selectedVariantIds.value.push(variantId);
  }
}

async function removeSelectedItems() {
  for (const id of [...selectedVariantIds.value]) await cart.update(id, 0);
  selectedVariantIds.value = [];
}

const selectedLines = computed(() =>
  cart.lines.filter((l) => selectedVariantIds.value.includes(l.variantId)),
);

const selectedCount = computed(() =>
  selectedLines.value.reduce((sum, l) => sum + l.quantity, 0),
);

const selectedSubtotal = computed(() =>
  selectedLines.value.reduce((sum, l) => sum + l.total, 0),
);

async function updateQty(id: string, qty: number) {
  formError.value = '';
  if (qty >= 1 && !(await cart.update(id, qty)))
    formError.value = changeFailed();
}

async function removeItem(id: string) {
  formError.value = '';
  if (!(await cart.update(id, 0))) formError.value = changeFailed();
}

const formError = ref('');
const changeFailed = () => t('The cart could not be updated. Try again.');

async function proceedToCheckout() {
  formError.value = '';
  if (selectedCount.value === 0) {
    formError.value =
      locale.value === 'th-TH'
        ? 'กรุณาเลือกบอร์ดเกมอย่างน้อย 1 รายการเพื่อสั่งซื้อ'
        : 'Please select at least 1 game to checkout.';
    return;
  }

  // The server checks out a whole cart, so lines left unticked are removed first.
  const unselected = cart.lines.filter(
    (l) => !selectedVariantIds.value.includes(l.variantId),
  );
  for (const item of unselected) {
    if (!(await cart.update(item.variantId, 0))) {
      formError.value = changeFailed();
      return;
    }
  }

  router.push('/checkout');
}
</script>

<template>
  <div class="shopee-cart-page">
    <div class="cart-container">
      <!-- Breadcrumb Navigation -->
      <div class="cart-top-bar">
        <nav class="cart-breadcrumb" aria-label="Breadcrumb">
          <RouterLink to="/" class="bc-link">
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            <span>{{ locale === 'th-TH' ? 'หน้าหลัก' : 'Home' }}</span>
          </RouterLink>
          <span class="bc-sep">/</span>
          <span class="bc-curr">{{
            locale === 'th-TH' ? 'ตะกร้าสินค้า' : 'Shopping Cart'
          }}</span>
        </nav>
      </div>

      <div class="page-title-row">
        <h1 class="page-title">
          <span>{{
            locale === 'th-TH' ? 'ตะกร้าสินค้า' : 'Shopping Cart'
          }}</span>
          <span v-if="cart.lines.length > 0" class="items-count-tag">
            ({{ cart.lines.length }}
            {{ locale === 'th-TH' ? 'รายการ' : 'items' }})
          </span>
        </h1>

        <button
          v-if="auth.authenticated"
          type="button"
          class="btn-view-history"
          @click="ordersStore.openOrdersModal()"
        >
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path
              d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
            />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
            <line x1="12" y1="22.08" x2="12" y2="12" />
          </svg>
          <span>{{
            locale === 'th-TH' ? 'คำสั่งซื้อของฉัน' : 'My Orders'
          }}</span>
        </button>
      </div>

      <!-- EMPTY STATE -->
      <div v-if="cart.lines.length === 0" class="empty-cart-card">
        <div class="empty-icon-wrap">
          <svg
            width="60"
            height="60"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#c6ad7a"
            stroke-width="1.3"
            aria-hidden="true"
          >
            <path d="M5 7h14l1 14H4L5 7Z" />
            <path d="M8 8V6a4 4 0 0 1 8 0v2" />
          </svg>
        </div>
        <h2>
          {{
            locale === 'th-TH'
              ? 'ไม่มีสินค้าในตะกร้าของคุณ'
              : 'Your cart is currently empty'
          }}
        </h2>
        <p class="empty-sub">
          {{
            locale === 'th-TH'
              ? 'เลือกดูบอร์ดเกมเพื่อเพิ่มลงในตะกร้าของคุณ'
              : 'Discover games from our curated collection and add them to your cart.'
          }}
        </p>
        <RouterLink to="/" class="btn-return-shop">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.2"
          >
            <line x1="19" y1="12" x2="5" y2="12" />
            <polyline points="12 19 5 12 12 5" />
          </svg>
          <span>{{
            locale === 'th-TH'
              ? 'กลับไปหน้าหลักเพื่อเลือกซื้อบอร์ดเกม'
              : 'Explore Board Games'
          }}</span>
        </RouterLink>
      </div>

      <!-- ACTIVE CART TABLE (SHOPEE STYLE) -->
      <div v-else class="shopee-cart-layout">
        <!-- Error Banner if any -->
        <div v-if="formError" class="cart-form-alert" role="alert">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <span>{{ formError }}</span>
        </div>

        <!-- 1. TABLE HEADER (SHOPEE COLUMNS) -->
        <div class="shopee-table-header">
          <div class="col-chk">
            <label class="custom-chk-label">
              <AppCheckbox
                :model-value="isAllSelected"
                :indeterminate="isPartiallySelected"
                :label="locale === 'th-TH' ? 'เลือกทั้งหมด' : 'Select all'"
                @update:model-value="toggleSelectAll"
              />
            </label>
          </div>
          <div class="col-product">
            {{ locale === 'th-TH' ? 'สินค้า' : 'Product' }}
          </div>
          <div class="col-unit-price">
            {{ locale === 'th-TH' ? 'ราคาต่อชิ้น' : 'Unit Price' }}
          </div>
          <div class="col-qty">
            {{ locale === 'th-TH' ? 'จำนวน' : 'Quantity' }}
          </div>
          <div class="col-total">
            {{ locale === 'th-TH' ? 'ราคารวม' : 'Total Price' }}
          </div>
          <div class="col-action">
            {{ locale === 'th-TH' ? 'จัดการ' : 'Action' }}
          </div>
        </div>

        <!-- 2. STORE GROUP CARD -->
        <div class="shopee-store-card">
          <div class="store-banner-row">
            <span class="store-badge">{{
              locale === 'th-TH' ? 'ร้านค้าทางการ' : 'Official Store'
            }}</span>
            <strong class="store-name">The Parlor Board Game Store</strong>
          </div>

          <!-- Product Rows (No fading on unchecked, full brightness) -->
          <div class="store-items-list">
            <div
              v-for="line in cart.lines"
              :key="line.variantId"
              class="shopee-item-row"
            >
              <!-- Row Checkbox -->
              <div class="col-chk">
                <label class="custom-chk-label">
                  <AppCheckbox
                    :model-value="selectedVariantIds.includes(line.variantId)"
                    :label="line.game.name"
                    @update:model-value="toggleSelectItem(line.variantId)"
                  />
                </label>
              </div>

              <!-- Product Details -->
              <div class="col-product item-product-cell">
                <img
                  v-if="
                    coverOf(line.game.id) && !failedCovers.has(line.game.id)
                  "
                  :src="coverOf(line.game.id)"
                  :alt="line.game.name"
                  class="item-img"
                  @error="
                    failedCovers = new Set([...failedCovers, line.game.id])
                  "
                />
                <span
                  v-else
                  class="item-img item-img-fallback"
                  aria-hidden="true"
                ></span>
                <div class="item-text">
                  <RouterLink :to="`/games/${line.game.id}`" class="item-title">
                    {{ line.game.name }}
                  </RouterLink>
                  <span class="item-edition-tag">
                    {{ editionLabel(line.variant.language) }}
                  </span>
                  <span v-if="line.problem" class="item-issue" role="alert">
                    {{ issueText(line) }}
                  </span>
                </div>
              </div>

              <!-- Unit Price -->
              <div class="col-unit-price item-unit-price">
                {{ formatMinor(line.variant.price) }}
              </div>

              <!-- Quantity Stepper -->
              <div class="col-qty item-qty-cell">
                <AppNumberStepper
                  :model-value="line.quantity"
                  :label="t('Quantity for {game}', { game: line.game.name })"
                  :min="1"
                  :max="Math.max(1, line.available)"
                  :disabled="cart.busy"
                  @update:model-value="updateQty(line.variantId, $event)"
                />
                <div class="stock-info-wrap">
                  <span v-if="line.available" class="stock-hint">
                    {{
                      locale === 'th-TH'
                        ? `คลังเหลือ ${line.available} ชิ้น`
                        : `${line.available} in stock`
                    }}
                  </span>
                  <span
                    v-if="line.quantity >= line.available"
                    class="stock-max-tag"
                  >
                    {{
                      locale === 'th-TH' ? '(สต็อกสูงสุดแล้ว)' : '(Max reached)'
                    }}
                  </span>
                </div>
              </div>

              <!-- Total Price for Line -->
              <div class="col-total item-total-price">
                {{ formatMinor(line.total) }}
              </div>

              <!-- Delete Action -->
              <div class="col-action item-action-cell">
                <button
                  type="button"
                  class="btn-row-delete"
                  :aria-label="t('Remove {name}', { name: line.game.name })"
                  @click="removeItem(line.variantId)"
                >
                  {{ locale === 'th-TH' ? 'ลบ' : 'Delete' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. BOTTOM STICKY BAR (SHOPEE STYLE - CLEAN & FLUSH) -->
        <div class="shopee-sticky-bar">
          <div class="sticky-left">
            <label class="custom-chk-label">
              <AppCheckbox
                :model-value="isAllSelected"
                :indeterminate="isPartiallySelected"
                :label="locale === 'th-TH' ? 'เลือกทั้งหมด' : 'Select all'"
                @update:model-value="toggleSelectAll"
              />
              <span class="chk-text">
                {{
                  locale === 'th-TH'
                    ? `เลือกทั้งหมด (${cart.lines.length})`
                    : `Select All (${cart.lines.length})`
                }}
              </span>
            </label>

            <button
              v-if="selectedVariantIds.length > 0"
              type="button"
              class="btn-bar-delete"
              @click="removeSelectedItems"
            >
              {{
                locale === 'th-TH'
                  ? `ลบ (${selectedVariantIds.length})`
                  : `Delete (${selectedVariantIds.length})`
              }}
            </button>
          </div>

          <div class="sticky-right">
            <div class="price-breakdown">
              <div class="breakdown-line">
                <span class="lbl-total">
                  {{
                    locale === 'th-TH'
                      ? `รวม (${selectedCount} สินค้า):`
                      : `Total (${selectedCount} items):`
                  }}
                </span>
                <strong class="total-amt">{{
                  formatMinor(selectedSubtotal)
                }}</strong>
              </div>
            </div>

            <button
              type="button"
              class="btn-shopee-checkout"
              :disabled="selectedCount === 0"
              @click="proceedToCheckout"
            >
              {{ locale === 'th-TH' ? 'สั่งซื้อสินค้า' : 'Checkout' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.item-issue {
  display: block;
  margin-top: 4px;
  color: #a12a1c;
  font-size: 12px;
  font-weight: 600;
}
.item-img-fallback {
  display: block;
  background: #f0f2ef;
}
.shopee-cart-page {
  background: #f4f5f7;
  min-height: calc(100vh - 70px);
  padding: 24px 0 80px; /* leaves room for sticky bar */
  font-family: Arial, Helvetica, sans-serif;
  color: #243e32;
}

.cart-container {
  max-width: 1140px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Breadcrumb */
.cart-top-bar {
  display: flex;
  align-items: center;
}
.cart-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.bc-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #555555;
  text-decoration: none;
}
.bc-link:hover {
  color: #142a1f;
  text-decoration: underline;
}
.bc-sep {
  color: #aaaaaa;
}
.bc-curr {
  color: #142a1f;
  font-weight: 700;
}

/* Page Title Row */
.page-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 800;
  color: #142a1f;
  margin: 0;
}
.items-count-tag {
  font-size: 14px;
  font-weight: 500;
  color: #666666;
}
.btn-view-history {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #eaf0ec;
  color: #1d3d2e;
  border: 1px solid #bed6ca;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-view-history:hover {
  background: #dce7e0;
  border-color: #142a1f;
}
.history-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  background: #142a1f;
  color: #ffd98a;
  border-radius: 9px;
  font-size: 10px;
  font-weight: 800;
}

/* Empty Cart State */
.empty-cart-card {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 60px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.empty-icon-wrap {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: #faf7f2;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.empty-cart-card h2 {
  font-size: 18px;
  font-weight: 700;
  color: #222222;
  margin: 0;
}
.empty-sub {
  font-size: 14px;
  color: #4e5b53;
  max-width: 400px;
  margin: 0;
}
.btn-return-shop {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 12px 24px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  margin-top: 10px;
  transition: all 140ms ease;
}
.btn-return-shop:hover {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
}

/* Layout */
.shopee-cart-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Form Alert */
.cart-form-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fdf2f2;
  border: 1px solid #f0a8a8;
  border-radius: 4px;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 600;
}

/* Checkbox Style */
.custom-chk-label {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
/* Shopee Table Header */
.shopee-table-header {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  padding: 14px 20px;
  font-size: 13px;
  color: #777777;
  border: 1px solid #eaeaea;
}
.col-chk {
  width: 40px;
  flex-shrink: 0;
}
.col-product {
  flex: 3;
  min-width: 200px;
}
.col-unit-price {
  flex: 1;
  text-align: center;
  min-width: 100px;
}
.col-qty {
  flex: 1.2;
  text-align: center;
  min-width: 130px;
}
.col-total {
  flex: 1;
  text-align: center;
  min-width: 100px;
}
.col-action {
  flex: 0.8;
  text-align: center;
  min-width: 80px;
}

/* Store Group Card */
.shopee-store-card {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #eaeaea;
  overflow: hidden;
}
.store-banner-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafbf9;
}
.store-badge {
  background: #142a1f;
  color: #ffd98a;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 3px;
}
.store-name {
  font-size: 14px;
  color: #142a1f;
}

/* Items List (No fading on unchecked, full brightness) */
.store-items-list {
  display: flex;
  flex-direction: column;
}
.shopee-item-row {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f5f5f5;
  transition: background-color 120ms ease;
  background: #ffffff;
  opacity: 1; /* Unchecked items are NOT faded */
}
.shopee-item-row:last-child {
  border-bottom: none;
}
.shopee-item-row:hover {
  background: #fafafa;
}

/* Product Cell */
.item-product-cell {
  display: flex;
  align-items: center;
  gap: 14px;
}
.item-img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #ebebeb;
  flex-shrink: 0;
  background: #f7f7f7;
  opacity: 1;
}
.item-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #222222;
  text-decoration: none;
  line-height: 1.35;
}
.item-title:hover {
  color: #142a1f;
  text-decoration: underline;
}
.item-edition-tag {
  font-size: 12px;
  color: #777777;
}

/* Unit Price */
.item-unit-price {
  font-size: 14px;
  color: #444444;
}

/* Stepper */
.item-qty-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.stock-info-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stock-hint {
  font-size: 11px;
  color: #777777;
}
.stock-max-tag {
  font-size: 10px;
  color: #b91c1c;
  font-weight: 700;
}

/* Total Price */
.item-total-price {
  font-size: 15px;
  font-weight: 700;
  color: #142a1f;
}

/* Delete Action */
.item-action-cell {
  display: flex;
  justify-content: center;
}
.btn-row-delete {
  background: none;
  border: none;
  color: #888888;
  font-size: 13px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 3px;
  transition: all 120ms ease;
}
.btn-row-delete:hover {
  color: #b91c1c;
  background: #fef2f2;
}

/* Sticky Bottom Bar */
.shopee-sticky-bar {
  position: sticky;
  bottom: 0;
  background: #ffffff;
  border-top: 1px solid #e5e5e5;
  box-shadow: 0 -3px 12px rgba(0, 0, 0, 0.08);
  padding: 14px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 4px;
  z-index: 50;
}
.sticky-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.chk-text {
  font-size: 14px;
  color: #222222;
}
.btn-bar-delete {
  background: none;
  border: none;
  color: #b91c1c;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
}
.btn-bar-delete:hover {
  text-decoration: underline;
}

.sticky-right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.price-breakdown {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.breakdown-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.lbl-total {
  font-size: 14px;
  color: #555555;
}
.total-amt {
  font-size: 22px;
  font-weight: 800;
  color: #142a1f;
}

.btn-shopee-checkout {
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 12px 36px;
  border-radius: 4px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-shopee-checkout:hover:not(:disabled) {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
}
.btn-shopee-checkout:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .shopee-table-header {
    display: none;
  }
  .shopee-item-row {
    flex-wrap: wrap;
    gap: 12px;
  }
  .col-unit-price,
  .col-total {
    text-align: left;
  }
  .shopee-sticky-bar {
    flex-direction: column;
    gap: 14px;
    align-items: stretch;
  }
  .sticky-right {
    justify-content: space-between;
  }
}
</style>
