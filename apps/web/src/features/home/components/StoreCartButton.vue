<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { RouterLink, useRouter, useRoute } from 'vue-router';
import { useCartStore } from '../stores/cart';
import { useStoreText } from '../data/store-text';
import { formatMinor } from '../data/catalog';
import { useGameCovers } from '../api/catalog';

const cart = useCartStore();
const { t, locale } = useStoreText();
const router = useRouter();
const route = useRoute();

const coverOf = useGameCovers();
const failedCovers = ref(new Set<string>());
// The header is on every storefront page, so this is where the cart is first read.
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

const isHovered = ref(false);
let closeTimer: ReturnType<typeof setTimeout> | null = null;

const isCartOrCheckoutPage = computed(
  () => route.name === 'Cart' || route.name === 'Checkout',
);

function handleMouseEnter() {
  if (isCartOrCheckoutPage.value) return;
  if (closeTimer) {
    clearTimeout(closeTimer);
    closeTimer = null;
  }
  isHovered.value = true;
}

function handleMouseLeave() {
  if (isCartOrCheckoutPage.value) {
    isHovered.value = false;
    return;
  }
  closeTimer = setTimeout(() => {
    isHovered.value = false;
  }, 220);
}

// Top 5 recent items in cart (reversed so newest added shows first)
const previewLines = computed(() => {
  return [...cart.lines].reverse().slice(0, 5);
});

function goToCart() {
  isHovered.value = false;
  router.push('/cart');
}
</script>

<template>
  <div
    class="cart-wrapper"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <RouterLink to="/cart" class="cart-trigger" @click="isHovered = false">
      <svg
        viewBox="0 0 24 24"
        width="19"
        height="19"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        aria-hidden="true"
      >
        <path d="M5 7h14l1 14H4L5 7Z" />
        <path d="M8 8V6a4 4 0 0 1 8 0v2" />
      </svg>
      <!-- The visible count is part of the link's name: "Cart 2 items". -->
      <span class="sr-only">{{ t('Cart') }}</span>
      <span class="cart-count">{{ cart.count }}</span>
      <span class="sr-only">{{ t('items') }}</span>
    </RouterLink>

    <!-- Shopee-style Cart Hover Dropdown (only on catalog/home pages) -->
    <Transition name="fade-dropdown">
      <div
        v-if="isHovered && !isCartOrCheckoutPage"
        class="cart-hover-dropdown"
        role="region"
        :aria-label="
          locale === 'th-TH' ? 'ตัวอย่างสินค้าในตะกร้า' : 'Cart items preview'
        "
      >
        <!-- Triangle Pointer -->
        <div class="dropdown-arrow" aria-hidden="true"></div>

        <!-- Popover Body -->
        <div class="dropdown-content">
          <div class="dropdown-header">
            <span>{{
              locale === 'th-TH'
                ? 'สินค้าที่เพิ่งเพิ่มเข้าไป'
                : 'Recently Added Products'
            }}</span>
          </div>

          <!-- Empty Dropdown -->
          <div v-if="cart.lines.length === 0" class="dropdown-empty">
            <svg
              viewBox="0 0 24 24"
              width="44"
              height="44"
              fill="none"
              stroke="#b59f70"
              stroke-width="1.2"
              aria-hidden="true"
            >
              <path d="M5 7h14l1 14H4L5 7Z" />
              <path d="M8 8V6a4 4 0 0 1 8 0v2" />
            </svg>
            <p>
              {{
                locale === 'th-TH'
                  ? 'ยังไม่มีสินค้าในรถเข็น'
                  : 'No products in cart yet'
              }}
            </p>
          </div>

          <!-- Items List (Max 5 lines) -->
          <div v-else class="dropdown-items-list">
            <div
              v-for="line in previewLines"
              :key="line.variantId"
              class="dropdown-item-row"
              @click="goToCart"
            >
              <img
                v-if="coverOf(line.game.id) && !failedCovers.has(line.game.id)"
                :src="coverOf(line.game.id)"
                :alt="line.game.name"
                class="dropdown-item-thumb"
                width="44"
                height="44"
                @error="failedCovers = new Set([...failedCovers, line.game.id])"
              />
              <span
                v-else
                class="dropdown-item-thumb"
                aria-hidden="true"
              ></span>
              <div class="dropdown-item-info">
                <span class="dropdown-item-title" :title="line.game.name">
                  {{ line.game.name }}
                </span>
                <span class="dropdown-item-edition">
                  {{ editionLabel(line.variant.language) }}
                  <span class="dropdown-item-qty">x{{ line.quantity }}</span>
                </span>
              </div>
              <div class="dropdown-item-price">
                {{ formatMinor(line.variant.price) }}
              </div>
            </div>
          </div>

          <!-- Footer Bar -->
          <div class="dropdown-footer">
            <span class="footer-count">
              {{
                locale === 'th-TH'
                  ? `${cart.count} สินค้าในรถเข็น`
                  : `${cart.count} Products in Cart`
              }}
            </span>
            <button type="button" class="btn-view-my-cart" @click="goToCart">
              {{
                locale === 'th-TH' ? 'ดูรถเข็นของคุณ' : 'View My Shopping Cart'
              }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.cart-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.cart-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #f3e5c5;
  text-decoration: none;
  font:
    14px Arial,
    sans-serif;
  padding: 10px 8px;
  border: 1px solid transparent;
  border-radius: 3px;
  cursor: pointer;
  transition: background-color 140ms ease;
  white-space: nowrap;
}
.cart-trigger:hover {
  background: #315242;
  color: #ffffff;
}
.cart-trigger:focus-visible {
  outline: 2px solid #ddc18c;
  outline-offset: 2px;
}
.cart-count {
  font-weight: 700;
  color: #ffd98a;
}

/* Hover Dropdown (Shopee Style) */
.cart-hover-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: -8px;
  width: 400px;
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.22);
  z-index: 1000;
  font-family: Arial, Helvetica, sans-serif;
}

/* Upward pointing arrow triangle */
.dropdown-arrow {
  position: absolute;
  top: -6px;
  right: 22px;
  width: 12px;
  height: 6px;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-bottom: 6px solid #ffffff;
}

.dropdown-content {
  display: flex;
  flex-direction: column;
}

.dropdown-header {
  padding: 12px 14px;
  font-size: 13px;
  color: #888888;
  border-bottom: 1px solid #f0f0f0;
  background: #fafaf9;
  border-top-left-radius: 4px;
  border-top-right-radius: 4px;
}

.dropdown-empty {
  padding: 32px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #777777;
  font-size: 13px;
}

.dropdown-items-list {
  max-height: 310px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.dropdown-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background-color 120ms ease;
}
.dropdown-item-row:hover {
  background: #faf7f2;
}

.dropdown-item-thumb {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #ebebeb;
  flex-shrink: 0;
  background: #f7f7f7;
}

.dropdown-item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.dropdown-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #222222;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dropdown-item-edition {
  font-size: 11px;
  color: #777777;
  display: flex;
  align-items: center;
  gap: 8px;
}

.dropdown-item-qty {
  font-weight: 700;
  color: #142a1f;
}

.dropdown-item-price {
  font-size: 14px;
  font-weight: 700;
  color: #a47c28; /* luxury gold tone matching Shopee's highlighted price */
  white-space: nowrap;
  flex-shrink: 0;
}

.dropdown-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #fbfbfb;
  border-top: 1px solid #f0f0f0;
  border-bottom-left-radius: 4px;
  border-bottom-right-radius: 4px;
}

.footer-count {
  font-size: 12px;
  color: #555555;
  font-weight: 500;
}

.btn-view-my-cart {
  background: #142a1f; /* Parlor deep emerald or brand highlight */
  color: #f6ecd5;
  border: 1px solid #365e49;
  padding: 7px 16px;
  border-radius: 3px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-view-my-cart:hover {
  background: #1d3d2e;
  color: #ffffff;
  border-color: #c6ad7a;
}

/* Transition Animations */
.fade-dropdown-enter-active,
.fade-dropdown-leave-active {
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}
.fade-dropdown-enter-from,
.fade-dropdown-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (prefers-reduced-motion: reduce) {
  .cart-trigger,
  .btn-view-my-cart,
  .dropdown-item-row {
    transition: none;
  }
}
</style>
