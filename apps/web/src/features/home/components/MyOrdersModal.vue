<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useQueryClient } from '@tanstack/vue-query';
import { cancelOrder, type Order } from '@theparlor/api-client';
import { useOrdersStore } from '../stores/orders';
import { useCartStore } from '../stores/cart';
import { useAuthStore } from '../../../shared/stores/auth';
import { formatMinor } from '../data/catalog';
import { useGameCovers } from '../api/catalog';
import { MY_ORDERS_QUERY_KEY, useMyOrders } from '../api/orders';
import { useStoreText } from '../data/store-text';

const router = useRouter();
const ordersStore = useOrdersStore();
const cart = useCartStore();
const auth = useAuthStore();
const queryClient = useQueryClient();
const coverOf = useGameCovers();
const { locale } = useStoreText();

const carrierNames: Record<string, string> = {
  THAILAND_POST: 'Thailand Post',
  KERRY: 'Kerry Express (KEX)',
  FLASH: 'Flash Express',
  JT: 'J&T Express',
};

const dialog = ref<HTMLDialogElement | null>(null);

// Orders have three server states; there is no separate "shipped" step yet.
export type OrderTab = 'ALL' | 'PLACED' | 'FULFILLED' | 'CANCELLED';
const activeTab = ref<OrderTab>('ALL');
const searchQuery = ref('');
const actionError = ref('');
const cancelling = ref<string | null>(null);

const orders = useMyOrders(
  () => ordersStore.isOrdersModalOpen && auth.authenticated,
);
const allOrders = computed(() => orders.data.value ?? []);
const placedCount = computed(
  () => allOrders.value.filter((order) => order.status === 'PLACED').length,
);

function syncModalState(isOpen: boolean) {
  if (!dialog.value) return;
  if (isOpen) {
    if (ordersStore.activeOrderId) activeTab.value = 'ALL';
    actionError.value = '';
    if (!dialog.value.open) {
      if (typeof dialog.value.showModal === 'function') {
        try {
          dialog.value.showModal();
        } catch {
          dialog.value.setAttribute('open', '');
        }
      } else {
        dialog.value.setAttribute('open', '');
      }
    }
  } else if (dialog.value.open) {
    if (typeof dialog.value.close === 'function') {
      try {
        dialog.value.close();
      } catch {
        dialog.value.removeAttribute('open');
      }
    } else {
      dialog.value.removeAttribute('open');
    }
  }
}

watch(
  () => ordersStore.isOrdersModalOpen,
  async (isOpen) => {
    await nextTick();
    syncModalState(isOpen);
  },
  { immediate: true },
);

const filteredOrders = computed(() => {
  let list = allOrders.value;
  if (activeTab.value !== 'ALL')
    list = list.filter((order) => order.status === activeTab.value);
  const q = searchQuery.value.trim().toLowerCase();
  if (q)
    list = list.filter(
      (order) =>
        order.id.toLowerCase().includes(q) ||
        order.items.some((item) => item.gameName.toLowerCase().includes(q)),
    );
  return list;
});

function handleClose() {
  ordersStore.closeOrdersModal();
}

async function buyAgain(order: Order) {
  actionError.value = '';
  let added = 0;
  for (const item of order.items)
    if (await cart.add(item.editionId, item.quantity)) added++;
  if (!added) {
    actionError.value =
      locale.value === 'th-TH'
        ? 'ไม่สามารถเพิ่มสินค้าจากคำสั่งซื้อนี้ลงตะกร้าได้ในขณะนี้'
        : 'None of these items can be added to your cart right now.';
    return;
  }
  handleClose();
  router.push('/cart');
}

/** Customers can cancel an order until staff hand it over; the card is not charged. */
async function cancel(order: Order) {
  const confirmed = window.confirm(
    locale.value === 'th-TH'
      ? 'ยกเลิกคำสั่งซื้อนี้ใช่หรือไม่ ระบบจะไม่เรียกเก็บเงินจากบัตรของคุณ'
      : 'Cancel this order? Your card will not be charged.',
  );
  if (!confirmed) return;
  actionError.value = '';
  cancelling.value = order.id;
  try {
    await cancelOrder(order.branchId, order.id, {
      reason: 'Cancelled by customer',
    });
    await queryClient.invalidateQueries({ queryKey: MY_ORDERS_QUERY_KEY });
    void queryClient.invalidateQueries({
      queryKey: ['storefront', 'offering'],
    });
  } catch {
    actionError.value =
      locale.value === 'th-TH'
        ? 'ไม่สามารถยกเลิกคำสั่งซื้อนี้ได้'
        : 'This order could not be cancelled.';
  } finally {
    cancelling.value = null;
  }
}

function contactStore() {
  alert(
    locale.value === 'th-TH'
      ? 'ติดต่อร้านค้า The Parlor: โทร 02-123-4567 หรือ Line @theparlor'
      : 'Contact The Parlor: Tel 02-123-4567 or Line @theparlor',
  );
}

function editionLabel(language: string): string {
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

function paymentLabel(order: Order): string {
  const isTh = locale.value === 'th-TH';
  const payment = order.payment;
  if (payment.method === 'COUNTER')
    return isTh ? 'ชำระที่เคาน์เตอร์' : 'Paid at the counter';
  const card =
    `${payment.cardBrand ?? ''} •••• ${payment.cardLast4 ?? ''}`.trim();
  switch (payment.status) {
    case 'CAPTURED':
      return isTh ? `ชำระแล้วด้วย ${card}` : `Paid with ${card}`;
    case 'VOIDED':
      return isTh ? `ไม่มีการเรียกเก็บเงิน (${card})` : `Not charged (${card})`;
    default:
      return isTh
        ? `กันวงเงินไว้ที่ ${card} จะเรียกเก็บเมื่อส่งมอบสินค้า`
        : `Reserved on ${card}; charged at handover`;
  }
}

async function copyTracking(trackingNumber: string) {
  await navigator.clipboard.writeText(trackingNumber);
}

function getStatusLabel(order: Order): { text: string; colorClass: string } {
  const isTh = locale.value === 'th-TH';
  if (order.status === 'CANCELLED')
    return {
      text: isTh ? 'ยกเลิกแล้ว' : 'Cancelled',
      colorClass: 'status-unpaid',
    };
  if (order.status === 'FULFILLED')
    return {
      text: isTh ? 'สำเร็จแล้ว' : 'Completed',
      colorClass: 'status-completed',
    };
  return order.fulfillmentMethod === 'SHIPPING'
    ? {
        text: isTh ? 'กำลังเตรียมจัดส่ง' : 'Preparing Shipment',
        colorClass: 'status-toship',
      }
    : {
        text: isTh ? 'กำลังเตรียมสินค้าให้รับที่สาขา' : 'Preparing for Pickup',
        colorClass: 'status-toship',
      };
}
</script>

<template>
  <dialog
    ref="dialog"
    class="shopee-orders-modal"
    aria-labelledby="orders-dialog-title"
    @click="$event.target === dialog && handleClose()"
    @close="handleClose"
  >
    <div class="modal-window">
      <!-- 1. TOP HEADER & CLOSE BUTTON -->
      <div class="modal-top-bar">
        <div class="top-title-group">
          <h2 id="orders-dialog-title" class="title-text">
            {{ locale === 'th-TH' ? 'คำสั่งซื้อของฉัน' : 'My Purchase' }}
          </h2>
        </div>
        <button
          type="button"
          class="btn-close-modal"
          aria-label="Close"
          @click="handleClose"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- 2. STATUS TABS -->
      <nav class="shopee-tabs-nav" aria-label="Order status tabs">
        <button
          type="button"
          class="shopee-tab"
          :class="{ active: activeTab === 'ALL' }"
          @click="activeTab = 'ALL'"
        >
          <span>{{ locale === 'th-TH' ? 'ทั้งหมด' : 'All' }}</span>
        </button>
        <button
          type="button"
          class="shopee-tab"
          :class="{ active: activeTab === 'PLACED' }"
          @click="activeTab = 'PLACED'"
        >
          <span>{{ locale === 'th-TH' ? 'กำลังเตรียม' : 'In Progress' }}</span>
          <span v-if="placedCount > 0" class="tab-badge">{{
            placedCount
          }}</span>
        </button>
        <button
          type="button"
          class="shopee-tab"
          :class="{ active: activeTab === 'FULFILLED' }"
          @click="activeTab = 'FULFILLED'"
        >
          <span>{{ locale === 'th-TH' ? 'สำเร็จแล้ว' : 'Completed' }}</span>
        </button>
        <button
          type="button"
          class="shopee-tab"
          :class="{ active: activeTab === 'CANCELLED' }"
          @click="activeTab = 'CANCELLED'"
        >
          <span>{{ locale === 'th-TH' ? 'ยกเลิกแล้ว' : 'Cancelled' }}</span>
        </button>
      </nav>

      <!-- 3. SEARCH BAR (Shopee Style) -->
      <div class="shopee-search-bar-wrap">
        <div class="search-input-box">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#888888"
            stroke-width="2"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="
              locale === 'th-TH'
                ? 'ค้นหาโดยใช้ชื่อบอร์ดเกม หรือหมายเลขคำสั่งซื้อ'
                : 'Search by board game name or Order ID'
            "
          />
          <button
            v-if="searchQuery"
            type="button"
            class="btn-clear-search"
            @click="searchQuery = ''"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- 4. ORDERS LIST AREA -->
      <div class="orders-scroll-area">
        <p v-if="actionError" class="orders-alert" role="alert">
          {{ actionError }}
        </p>
        <!-- Guests have no order history; orders belong to an account. -->
        <div v-if="!auth.authenticated" class="empty-orders-view">
          <p>
            {{
              locale === 'th-TH'
                ? 'เข้าสู่ระบบเพื่อดูคำสั่งซื้อของคุณ'
                : 'Sign in to see your orders.'
            }}
          </p>
        </div>
        <div
          v-else-if="orders.isPending.value"
          class="empty-orders-view"
          role="status"
        >
          <p>
            {{
              locale === 'th-TH' ? 'กำลังโหลดคำสั่งซื้อ…' : 'Loading orders…'
            }}
          </p>
        </div>
        <div
          v-else-if="orders.isError.value"
          class="empty-orders-view"
          role="alert"
        >
          <p>
            {{
              locale === 'th-TH'
                ? 'ไม่สามารถโหลดคำสั่งซื้อได้'
                : 'Your orders could not be loaded.'
            }}
          </p>
          <button
            type="button"
            class="btn-shopee-secondary"
            @click="orders.refetch()"
          >
            {{ locale === 'th-TH' ? 'ลองอีกครั้ง' : 'Try again' }}
          </button>
        </div>
        <div v-else-if="filteredOrders.length === 0" class="empty-orders-view">
          <p>
            {{
              locale === 'th-TH'
                ? 'ยังไม่มีรายการคำสั่งซื้อในหมวดหมู่นี้'
                : 'No orders found in this section'
            }}
          </p>
        </div>

        <!-- Orders Cards (Shopee Card Structure) -->
        <div v-else class="shopee-order-cards-list">
          <article
            v-for="order in filteredOrders"
            :key="order.id"
            class="shopee-order-card"
          >
            <!-- Card Head: Shop Name + Actions + Status -->
            <div class="card-shop-header">
              <div class="shop-left-group">
                <span class="badge-recommended">{{
                  locale === 'th-TH' ? 'ร้านแนะนำ' : 'Official'
                }}</span>
                <strong class="shop-name">The Parlor Board Game Store</strong>
                <button
                  type="button"
                  class="btn-visit-shop"
                  @click="handleClose"
                >
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                  </svg>
                  <span>{{ locale === 'th-TH' ? 'ดูร้านค้า' : 'Store' }}</span>
                </button>
              </div>

              <!-- Status Right -->
              <div class="shop-right-status">
                <span
                  class="order-status-text"
                  :class="getStatusLabel(order).colorClass"
                >
                  {{ getStatusLabel(order).text }}
                </span>
              </div>
            </div>

            <!-- Items List -->
            <div class="card-items-block">
              <div
                v-for="item in order.items"
                :key="item.editionId"
                class="order-item-line"
              >
                <img
                  v-if="coverOf(item.gameId)"
                  :src="coverOf(item.gameId)"
                  :alt="item.gameName"
                  class="order-item-thumb"
                />
                <span v-else class="order-item-thumb" aria-hidden="true"></span>
                <div class="order-item-desc">
                  <h4 class="order-item-title">{{ item.gameName }}</h4>
                  <div class="order-item-options">
                    <span
                      >{{
                        locale === 'th-TH' ? 'ตัวเลือกสินค้า:' : 'Edition:'
                      }}
                      {{ editionLabel(item.editionLanguage) }}</span
                    >
                  </div>
                  <div class="order-item-qty">x{{ item.quantity }}</div>
                </div>
                <div class="order-item-pricing">
                  <span class="curr-price">{{
                    formatMinor(item.unitPrice.amountMinor)
                  }}</span>
                </div>
              </div>
            </div>

            <!-- Card Bottom: Total Price + Action Buttons -->
            <div class="card-footer-action-row">
              <div class="footer-total-line">
                <span class="order-payment-line">
                  {{ locale === 'th-TH' ? 'ยอดรวมสินค้า' : 'Subtotal' }}:
                  {{ formatMinor(order.subtotal.amountMinor) }}
                </span>
                <span
                  v-if="order.fulfillmentMethod === 'SHIPPING'"
                  class="order-payment-line"
                >
                  {{ locale === 'th-TH' ? 'ค่าจัดส่ง' : 'Shipping' }}:
                  {{
                    order.shippingFee.amountMinor === 0
                      ? locale === 'th-TH'
                        ? 'ฟรี'
                        : 'Free'
                      : formatMinor(order.shippingFee.amountMinor)
                  }}
                </span>
                <span class="lbl-total-purchase">{{
                  locale === 'th-TH' ? 'รวมการสั่งซื้อ:' : 'Order Total:'
                }}</span>
                <strong class="val-total-purchase">{{
                  formatMinor(order.total.amountMinor)
                }}</strong>
                <span class="order-payment-line">{{
                  paymentLabel(order)
                }}</span>
                <span
                  v-if="order.carrier && order.trackingNumber"
                  class="tracking-snippet-bar"
                >
                  {{ carrierNames[order.carrier] }} ·
                  <strong>{{ order.trackingNumber }}</strong>
                  <button
                    type="button"
                    class="btn-copy-code"
                    @click="copyTracking(order.trackingNumber)"
                  >
                    {{ locale === 'th-TH' ? 'คัดลอก' : 'Copy' }}
                  </button>
                </span>
              </div>

              <div class="footer-buttons-group">
                <button
                  type="button"
                  class="btn-shopee-primary"
                  :disabled="cart.busy"
                  @click="buyAgain(order)"
                >
                  {{ locale === 'th-TH' ? 'ซื้ออีกครั้ง' : 'Buy Again' }}
                </button>
                <button
                  v-if="order.status === 'PLACED'"
                  type="button"
                  class="btn-shopee-secondary"
                  :disabled="cancelling === order.id"
                  @click="cancel(order)"
                >
                  {{ locale === 'th-TH' ? 'ยกเลิกคำสั่งซื้อ' : 'Cancel Order' }}
                </button>
                <button
                  type="button"
                  class="btn-shopee-secondary"
                  @click="contactStore"
                >
                  {{ locale === 'th-TH' ? 'ติดต่อผู้ขาย' : 'Contact Seller' }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.orders-alert {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #e2b4ad;
  border-radius: 4px;
  background: #fdf1ef;
  color: #a12a1c;
  font-size: 13px;
}
.order-payment-line {
  display: block;
  margin-top: 2px;
  color: #5c6a61;
  font-size: 12px;
}
.shopee-orders-modal {
  position: fixed;
  inset: 0;
  width: min(880px, 95vw);
  max-height: 88vh;
  margin: auto;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: #f5f5f5;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.28);
  font-family: Arial, Helvetica, sans-serif;
  box-sizing: border-box;
  z-index: 99999;
}

.shopee-orders-modal::backdrop {
  background: rgba(14, 25, 19, 0.65);
  backdrop-filter: blur(2px);
}

.modal-window {
  display: flex;
  flex-direction: column;
  height: 88vh;
  max-height: 88vh;
  background: #f5f5f5;
  overflow: hidden;
}

/* 1. Modal Top Bar */
.modal-top-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #ffffff;
  border-bottom: 1px solid #ebebeb;
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}
.title-text {
  font-size: 18px;
  font-weight: 800;
  color: #142a1f;
  margin: 0;
}
.btn-close-modal {
  background: transparent;
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  cursor: pointer;
  transition: background-color 120ms ease;
}
.btn-close-modal:hover {
  background: #f0f0f0;
  color: #111111;
}

/* 2. Shopee Status Tabs */
.shopee-tabs-nav {
  flex-shrink: 0;
  display: flex;
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
  overflow-x: auto;
  scrollbar-width: none; /* Hide scrollbar for clean tab look */
}
.shopee-tabs-nav::-webkit-scrollbar {
  display: none;
}
.shopee-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 10px;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  font-size: 14px;
  font-weight: 600;
  color: #555555;
  cursor: pointer;
  white-space: nowrap;
  transition: all 120ms ease;
}
.shopee-tab:hover {
  color: #142a1f;
}
.shopee-tab.active {
  color: #142a1f;
  border-bottom-color: #142a1f; /* Parlor signature active line */
  font-weight: 800;
}
.tab-badge {
  background: #142a1f;
  color: #ffd98a;
  font-size: 10px;
  font-weight: 800;
  padding: 1px 6px;
  border-radius: 10px;
}

/* 3. Search Bar */
.shopee-search-bar-wrap {
  flex-shrink: 0;
  padding: 12px 20px;
  background: #ffffff;
  border-bottom: 1px solid #ebebeb;
}
.search-input-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f4f5f7;
  border-radius: 4px;
  padding: 8px 14px;
  border: 1px solid #e1e7e4;
}
.search-input-box input {
  flex: 1;
  background: transparent;
  border: none;
  font-size: 13px;
  color: #222222;
  outline: none;
}
.btn-clear-search {
  background: none;
  border: none;
  font-size: 12px;
  color: #888888;
  cursor: pointer;
}

/* 4. Orders Scroll Area */
.orders-scroll-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 24px;
  display: flex;
  flex-direction: column;
}

.empty-orders-view {
  text-align: center;
  padding: 60px 20px;
  color: #777777;
  font-size: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

/* Shopee Order Card */
.shopee-order-cards-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.shopee-order-card {
  background: #ffffff;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

/* Shop Header inside card */
.card-shop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid #f2f2f2;
}
.shop-left-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.badge-recommended {
  background: #142a1f;
  color: #ffd98a;
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 2px;
}
.shop-name {
  font-size: 13px;
  font-weight: 700;
  color: #222222;
}
.btn-shop-chat,
.btn-visit-shop {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #ffffff;
  border: 1px solid #dcdcdc;
  padding: 3px 8px;
  border-radius: 2px;
  font-size: 11px;
  color: #555555;
  cursor: pointer;
}
.btn-shop-chat:hover,
.btn-visit-shop:hover {
  border-color: #142a1f;
  color: #142a1f;
}

/* Status text on the right */
.shop-right-status {
  display: flex;
  align-items: center;
}
.order-status-text {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
}
.status-unpaid {
  color: #ea580c;
}
.status-toship {
  color: #d97706; /* warm amber/orange tone */
}
.status-toreceive {
  color: #0284c7;
}
.status-completed {
  color: #16a34a;
}

/* Items Block */
.card-items-block {
  display: flex;
  flex-direction: column;
}
.order-item-line {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid #f7f7f7;
}
.order-item-thumb {
  width: 68px;
  height: 68px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid #eaeaea;
  background: #fafafa;
  flex-shrink: 0;
}
.order-item-desc {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.order-item-title {
  font-size: 14px;
  font-weight: 600;
  color: #222222;
  margin: 0;
  line-height: 1.3;
}
.order-item-options {
  font-size: 12px;
  color: #777777;
}
.order-item-qty {
  font-size: 12px;
  color: #444444;
}
.order-item-pricing {
  font-size: 14px;
  font-weight: 700;
  color: #222222;
  flex-shrink: 0;
}

/* Tracking Bar */
.tracking-snippet-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 18px;
  background: #fafbf9;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
  color: #3b5e4c;
}
.ts-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ts-eta {
  color: #777777;
}
.btn-copy-code {
  background: #ffffff;
  border: 1px solid #c8d9d0;
  color: #142a1f;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}
.btn-copy-code:hover {
  background: #eaf1ed;
}

/* Card Footer: Order Total & Buttons (Shopee Style) */
.card-footer-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  background: #ffffff;
  border-top: 1px solid #f4f4f4;
}
.footer-total-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.lbl-total-purchase {
  font-size: 13px;
  color: #555555;
}
.val-total-purchase {
  font-size: 20px;
  font-weight: 800;
  color: #142a1f; /* Luxury emerald or highlight price */
}

.footer-buttons-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-shopee-primary {
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 8px 20px;
  border-radius: 3px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 120ms ease;
}
.btn-shopee-primary:hover {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
}
.btn-shopee-secondary {
  background: #ffffff;
  color: #444444;
  border: 1px solid #d5d5d5;
  padding: 8px 16px;
  border-radius: 3px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 120ms ease;
}
.btn-shopee-secondary:hover {
  background: #f7f7f7;
  color: #111111;
  border-color: #aaaaaa;
}

@media (max-width: 640px) {
  .card-footer-action-row {
    flex-direction: column;
    align-items: flex-end;
    gap: 12px;
  }
}
</style>
