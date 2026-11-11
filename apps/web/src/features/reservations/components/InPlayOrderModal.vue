<script setup lang="ts">
import { ref, computed } from 'vue';
import { FOOD_MENU_ITEMS, FOOD_CATEGORIES } from '../data/food-menu';
import { useCustomerReservationsStore, type CustomerReservation } from '../data/reservations-store';
import { useStoreText } from '../../home/data/store-text';

const props = defineProps<{
  reservation: CustomerReservation;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'order-placed', orderId: string): void;
}>();

const { t, locale } = useStoreText();
const reservationsStore = useCustomerReservationsStore();

const activeCategory = ref('all');
const searchQuery = ref('');
const orderQuantities = ref<Record<string, number>>({});
const orderNotes = ref('');
const orderSuccess = ref(false);

const filteredItems = computed(() => {
  return FOOD_MENU_ITEMS.filter((item) => {
    const matchCat =
      activeCategory.value === 'all' || item.category === activeCategory.value;
    const q = searchQuery.value.toLowerCase().trim();
    const nameMatch =
      !q ||
      item.name.toLowerCase().includes(q) ||
      (item.nameTh && item.nameTh.includes(q)) ||
      item.description.toLowerCase().includes(q);
    return matchCat && nameMatch;
  });
});

function getQuantity(itemId: string): number {
  return orderQuantities.value[itemId] || 0;
}

function updateQuantity(itemId: string, delta: number) {
  const current = getQuantity(itemId);
  const next = Math.max(0, current + delta);
  if (next === 0) {
    delete orderQuantities.value[itemId];
  } else {
    orderQuantities.value[itemId] = next;
  }
}

const selectedOrderList = computed(() => {
  return Object.entries(orderQuantities.value)
    .filter(([_, qty]) => qty > 0)
    .map(([id, qty]) => {
      const item = FOOD_MENU_ITEMS.find((m) => m.id === id)!;
      return {
        item: {
          id: item.id,
          name: item.name,
          nameTh: item.nameTh,
          price: item.price,
        },
        quantity: qty,
      };
    });
});

const orderTotal = computed(() => {
  return selectedOrderList.value.reduce(
    (sum, entry) => sum + entry.item.price * entry.quantity,
    0
  );
});

function submitOrder() {
  if (selectedOrderList.value.length === 0) return;

  const newOrder = reservationsStore.addInPlayOrder(props.reservation.id, {
    items: selectedOrderList.value,
    subtotal: orderTotal.value,
    paid: false,
    paymentMethod: 'on_tab',
    notes: orderNotes.value.trim() || undefined,
  });

  orderSuccess.value = true;
  setTimeout(() => {
    if (newOrder) emit('order-placed', newOrder.id);
    emit('close');
  }, 1400);
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-card inplay-order-dialog">
      <!-- Header -->
      <div class="dialog-header">
        <div class="header-info">
          <span class="table-tag">
            <span class="bullet">◆</span>
            {{ t(reservation.tableLabel) }} • {{ t(reservation.branchName) }}
          </span>
          <h2 class="dialog-title">{{ t('Order Food & Drinks to Table') }}</h2>
          <p class="dialog-sub">
            {{ t('Items will be prepared immediately and served to your playing table. Pay at counter with staff when finished.') }}
          </p>
        </div>
        <button type="button" class="btn-close" :aria-label="t('Cancel')" @click="emit('close')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- Success Screen -->
      <div v-if="orderSuccess" class="success-screen">
        <div class="success-icon-ring">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </div>
        <h3 class="success-title">{{ t('Order Sent to Kitchen!') }}</h3>
        <p class="success-sub">
          {{ t('Your refreshments are being prepared and will be brought directly to your table.') }}
        </p>
        <span class="success-method">
          {{ t('Added to your Table Tab (Pay at Counter)') }}
        </span>
      </div>

      <!-- Main Ordering Form (No pictures, clean simple text list) -->
      <div v-else class="dialog-content">
        <!-- Search & Category Filters -->
        <div class="menu-controls">
          <input
            v-model="searchQuery"
            type="search"
            class="search-input"
            :placeholder="t('Search food, beverages, snacks…')"
          />
          <div class="cat-pills">
            <button
              v-for="cat in FOOD_CATEGORIES"
              :key="cat.id"
              type="button"
              class="cat-btn"
              :class="{ active: activeCategory === cat.id }"
              @click="activeCategory = cat.id"
            >
              {{ t(cat.label) }}
            </button>
          </div>
        </div>

        <!-- Clean Text-Only Menu Items List (No pictures) -->
        <div class="menu-items-scroll">
          <div v-for="item in filteredItems" :key="item.id" class="menu-item-row">
            <div class="item-info">
              <div class="item-title-row">
                <span class="item-name">
                  {{ locale === 'th-TH' && item.nameTh ? item.nameTh : item.name }}
                </span>
                <span v-if="item.popular" class="popular-tag">{{ t('popular') }}</span>
                <span v-if="item.dietary" class="dietary-tag">{{ t(item.dietary) }}</span>
              </div>
              <p class="item-desc">
                {{ locale === 'th-TH' && item.descriptionTh ? item.descriptionTh : item.description }}
              </p>
              <span class="item-price">฿{{ item.price }}</span>
            </div>

            <!-- Stepper -->
            <div class="stepper-wrap">
              <button
                v-if="getQuantity(item.id) > 0"
                type="button"
                class="step-btn minus"
                @click="updateQuantity(item.id, -1)"
              >
                –
              </button>
              <span v-if="getQuantity(item.id) > 0" class="step-val">
                {{ getQuantity(item.id) }}
              </span>
              <button
                type="button"
                class="step-btn plus"
                @click="updateQuantity(item.id, 1)"
              >
                +
              </button>
            </div>
          </div>
        </div>

        <!-- Order Summary & Notes -->
        <div class="order-summary-box">
          <div class="summary-top">
            <span class="summary-lbl">{{ t('Current In-Play Order:') }}</span>
            <span class="summary-total">฿{{ orderTotal }}</span>
          </div>

          <!-- Notes -->
          <input
            v-model="orderNotes"
            type="text"
            class="notes-input"
            :placeholder="t('Special instructions (e.g. Extra ice, serve in 15 mins…)')"
          />

          <div class="counter-pay-notice">
            <svg class="gold-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="16" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
            <span>{{ t('All in-play orders are added to your table tab and paid at the counter with staff.') }}</span>
          </div>

          <!-- Action Button -->
          <button
            type="button"
            class="btn btn-primary submit-order-btn"
            :disabled="selectedOrderList.length === 0"
            @click="submitOrder"
          >
            {{ t('Send Order to Table (฿{total})', { total: orderTotal }) }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--color-parlor-green-950) 72%, transparent);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 250;
  padding: 16px;
}

.modal-card {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 14px;
  max-width: 620px;
  width: 100%;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 40px color-mix(in srgb, var(--color-parlor-green-950) 45%, transparent);
  overflow: hidden;
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  background: var(--color-parlor-page);
}

.table-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--color-parlor-green-900);
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
}

.bullet {
  color: var(--color-parlor-gold-500);
}

.dialog-title {
  margin: 0 0 2px;
  font-size: 20px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.dialog-sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-parlor-neutral-600);
}

.btn-close {
  background: transparent;
  border: none;
  color: var(--color-parlor-neutral-600);
  cursor: pointer;
  padding: 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close:hover {
  background: var(--color-parlor-neutral-150);
  color: var(--color-parlor-green-900);
}

.dialog-content {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
}

.menu-controls {
  padding: 12px 20px;
  background: var(--color-parlor-white);
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  font-size: 13px;
  box-sizing: border-box;
}

.cat-pills {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.cat-btn {
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-neutral-800);
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cat-btn:hover {
  background: var(--color-parlor-neutral-150);
}

.cat-btn.active {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
}

.menu-items-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.menu-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--color-parlor-neutral-150);
  border-radius: 8px;
  background: var(--color-parlor-white);
  transition: all 0.2s ease;
}

.menu-item-row:hover {
  border-color: var(--color-parlor-gold-500);
  background: var(--color-parlor-page);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 3px;
}

.item-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.popular-tag {
  font-size: 10px;
  font-weight: 800;
  color: var(--color-parlor-gold-500);
  background: var(--color-parlor-green-900);
  padding: 2px 7px;
  border-radius: 10px;
  border: 1px solid var(--color-parlor-gold-500);
  text-transform: lowercase;
}

.dietary-tag {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-parlor-green-600);
  background: var(--color-parlor-success-100);
  padding: 2px 7px;
  border-radius: 10px;
}

.item-desc {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
  margin: 0 0 4px;
  line-height: 1.35;
}

.item-price {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.step-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid var(--color-parlor-gold-500);
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  font-weight: 800;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.step-btn.minus {
  background: var(--color-parlor-white);
  color: var(--color-parlor-green-900);
}

.step-val {
  font-size: 14px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
  min-width: 18px;
  text-align: center;
}

/* Order Summary Box */
.order-summary-box {
  background: var(--color-parlor-page);
  border-top: 1px solid var(--color-parlor-neutral-300);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-lbl {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.summary-total {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.notes-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  font-size: 12.5px;
  box-sizing: border-box;
}

.counter-pay-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--color-parlor-neutral-600);
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  padding: 6px 10px;
  border-radius: 6px;
}

.gold-icon {
  color: var(--color-parlor-gold-500);
  flex-shrink: 0;
}

.submit-order-btn {
  width: 100%;
  padding: 12px;
  font-size: 14px;
  font-weight: 800;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.submit-order-btn:hover:not(:disabled) {
  background: var(--color-parlor-green-800);
  transform: translateY(-1px);
}

.submit-order-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Success Screen */
.success-screen {
  padding: 50px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.success-icon-ring {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 2px solid var(--color-parlor-gold-500);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}

.success-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.success-sub {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-parlor-neutral-600);
  max-width: 400px;
}

.success-method {
  font-size: 12px;
  font-weight: 700;
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  color: var(--color-parlor-green-900);
  padding: 4px 12px;
  border-radius: 12px;
  margin-top: 6px;
}
</style>
