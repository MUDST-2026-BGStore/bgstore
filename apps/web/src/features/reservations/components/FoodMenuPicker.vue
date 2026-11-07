<script setup lang="ts">
import { computed, ref } from 'vue';
import { FOOD_MENU_ITEMS, FOOD_CATEGORIES } from '../data/food-menu';
import type { FoodMenuItem, FoodOrderItem } from '../types';
import { calculatePreOrderDiscount } from '../data/payment';

const props = defineProps<{
  modelValue: FoodOrderItem[];
}>();

const emit = defineEmits<{
  'update:modelValue': [items: FoodOrderItem[]];
}>();

const activeCategory = ref<string>('all');
const searchQuery = ref<string>('');

const filteredItems = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  return FOOD_MENU_ITEMS.filter((item) => {
    const matchesCategory =
      activeCategory.value === 'all' || item.category === activeCategory.value;
    const matchesSearch =
      !query ||
      item.name.toLowerCase().includes(query) ||
      item.description.toLowerCase().includes(query);
    return matchesCategory && matchesSearch;
  });
});

function getQuantity(itemId: string): number {
  const found = props.modelValue.find((i) => i.item.id === itemId);
  return found ? found.quantity : 0;
}

function updateQuantity(item: FoodMenuItem, delta: number) {
  const current = getQuantity(item.id);
  const next = Math.max(0, current + delta);
  const updated = props.modelValue.filter((i) => i.item.id !== item.id);
  if (next > 0) {
    updated.push({ item, quantity: next });
  }
  emit('update:modelValue', updated);
}

const originalSubtotal = computed(() =>
  props.modelValue.reduce((sum, line) => sum + line.item.price * line.quantity, 0)
);

const discountSummary = computed(() =>
  calculatePreOrderDiscount(originalSubtotal.value)
);

const totalItemsCount = computed(() =>
  props.modelValue.reduce((sum, line) => sum + line.quantity, 0)
);
</script>

<template>
  <div class="food-menu-picker">
    <!-- Header banner highlighting 10% discount -->
    <div class="discount-banner">
      <div class="discount-badge">
        <span class="icon">✨</span>
        <span class="highlight">10% Off Pre-Orders</span>
      </div>
      <p class="discount-copy">
        Add food & drinks to your table booking now and receive an instant <strong>10% discount</strong>!
        Extra items can also be ordered at your table while playing.
      </p>
    </div>

    <!-- Category tabs and search -->
    <div class="menu-controls">
      <div class="category-tabs" role="tablist" aria-label="Food Categories">
        <button
          v-for="cat in FOOD_CATEGORIES"
          :key="cat.id"
          type="button"
          class="cat-tab"
          :class="{ 'is-active': activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >
          {{ cat.label }}
        </button>
      </div>

      <div class="search-box">
        <input
          v-model="searchQuery"
          type="search"
          placeholder="Search snacks, coffee, platters…"
          class="search-input"
          aria-label="Search menu items"
        />
      </div>
    </div>

    <!-- Items Grid -->
    <div class="menu-grid">
      <div
        v-for="item in filteredItems"
        :key="item.id"
        class="menu-card"
        :class="{ 'has-qty': getQuantity(item.id) > 0 }"
      >
        <div class="card-header">
          <div class="title-row">
            <h4 class="item-name">{{ item.name }}</h4>
            <span v-if="item.popular" class="popular-pill">Popular</span>
            <span v-if="item.dietary" class="dietary-pill">{{ item.dietary }}</span>
          </div>
          <p class="item-desc">{{ item.description }}</p>
        </div>

        <div class="card-footer">
          <div class="price-stack">
            <span class="discounted-price">฿{{ Math.round(item.price * 0.9) }}</span>
            <span class="original-price">฿{{ item.price }}</span>
            <span class="discount-tag">10% OFF</span>
          </div>

          <!-- Quantity Stepper -->
          <div class="stepper">
            <button
              v-if="getQuantity(item.id) > 0"
              type="button"
              class="step-btn minus"
              :aria-label="`Decrease ${item.name}`"
              @click="updateQuantity(item, -1)"
            >
              −
            </button>
            <span v-if="getQuantity(item.id) > 0" class="qty-count">{{
              getQuantity(item.id)
            }}</span>
            <button
              type="button"
              class="step-btn plus"
              :aria-label="`Add ${item.name}`"
              @click="updateQuantity(item, 1)"
            >
              <span v-if="getQuantity(item.id) === 0">+ Add</span>
              <span v-else>+</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Pre-Order Tray Summary -->
    <div v-if="totalItemsCount > 0" class="preorder-tray">
      <div class="tray-content">
        <div class="tray-info">
          <span class="count-badge">{{ totalItemsCount }} items selected</span>
          <div class="price-breakdown">
            <span class="orig-strike">฿{{ originalSubtotal }}</span>
            <span class="discounted-sum">฿{{ discountSummary.discountedTotal }}</span>
            <span class="saved-pill">Saved ฿{{ discountSummary.discountAmount }}</span>
          </div>
        </div>
        <p class="tray-footnote">
          ☕ Pre-ordered items will be prepared fresh upon your table check-in.
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.food-menu-picker {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.discount-banner {
  background: #f4ecdc;
  border: 1px solid #c6ad7a;
  border-radius: 6px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.discount-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #203c30;
  color: #fffaf1;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.discount-copy {
  margin: 0;
  font-size: 13px;
  color: #243e32;
  line-height: 1.5;
}

.menu-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-tab {
  padding: 7px 14px;
  border: 1px solid #d5c8b2;
  background: #fffaf1;
  color: #3b362c;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 140ms ease;
}

.cat-tab:hover {
  background: #eee3ce;
  border-color: #baa88c;
}

.cat-tab.is-active {
  background: #203c30;
  border-color: #203c30;
  color: #fff3dc;
}

.search-input {
  padding: 8px 14px;
  border: 1px solid #d5c8b2;
  border-radius: 6px;
  background: #ffffff;
  color: #202e27;
  font-size: 13px;
  min-width: 240px;
  outline: none;
}

.search-input:focus {
  border-color: #203c30;
  box-shadow: 0 0 0 2px rgba(32, 60, 48, 0.15);
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.menu-card {
  background: #ffffff;
  border: 1px solid #e0d5c1;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.menu-card:hover {
  border-color: #c6ad7a;
  box-shadow: 0 4px 12px rgba(20, 43, 32, 0.06);
}

.menu-card.has-qty {
  border-color: #203c30;
  background: #faf8f3;
}

.card-header {
  margin-bottom: 12px;
}

.title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.item-name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #1b2f25;
}

.popular-pill {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  background: #fde8cf;
  color: #8c4e0b;
  padding: 2px 6px;
  border-radius: 4px;
}

.dietary-pill {
  font-size: 10px;
  background: #e2f0d9;
  color: #2e6022;
  padding: 2px 6px;
  border-radius: 4px;
}

.item-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #555044;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f0e9dc;
  padding-top: 12px;
  margin-top: 6px;
}

.price-stack {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.discounted-price {
  font-size: 16px;
  font-weight: 700;
  color: #1a4231;
}

.original-price {
  font-size: 12px;
  text-decoration: line-through;
  color: #8e887d;
}

.discount-tag {
  font-size: 10px;
  font-weight: 700;
  color: #b54708;
  background: #fef0c7;
  padding: 2px 4px;
  border-radius: 3px;
}

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fffaf1;
  border: 1px solid #d5c8b2;
  border-radius: 6px;
  padding: 3px;
}

.step-btn {
  background: transparent;
  border: none;
  color: #203c30;
  font-weight: 700;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 120ms ease;
}

.step-btn:hover {
  background: #e9dec9;
}

.step-btn.plus {
  background: #203c30;
  color: #fffaf1;
}

.step-btn.plus:hover {
  background: #172c23;
}

.qty-count {
  font-size: 13px;
  font-weight: 700;
  min-width: 18px;
  text-align: center;
  color: #172c23;
}

.preorder-tray {
  background: #203c30;
  color: #fffaf1;
  border-radius: 8px;
  padding: 14px 20px;
  margin-top: 8px;
  box-shadow: 0 4px 16px rgba(18, 39, 29, 0.2);
}

.tray-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 6px;
}

.count-badge {
  font-size: 14px;
  font-weight: 700;
  color: #f7ebd4;
}

.price-breakdown {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.orig-strike {
  font-size: 13px;
  text-decoration: line-through;
  color: #a8b8b0;
}

.discounted-sum {
  font-size: 18px;
  font-weight: 800;
  color: #fce8bd;
}

.saved-pill {
  background: #2f614b;
  color: #e5f6ed;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
}

.tray-footnote {
  margin: 0;
  font-size: 12px;
  color: #b7cbbf;
}

@media (max-width: 640px) {
  .discount-banner {
    flex-direction: column;
    align-items: flex-start;
  }
  .menu-controls {
    flex-direction: column;
    align-items: stretch;
  }
  .search-input {
    width: 100%;
  }
}
</style>

