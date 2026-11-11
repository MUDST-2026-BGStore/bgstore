<script setup lang="ts">
import { computed, ref } from 'vue';
import { FOOD_MENU_ITEMS, FOOD_CATEGORIES } from '../data/food-menu';
import type { FoodMenuItem, FoodOrderItem } from '../types';
import { calculatePreOrderDiscount } from '../data/payment';
import { useStoreText } from '../../home/data/store-text';

const props = defineProps<{
  modelValue: FoodOrderItem[];
}>();

const emit = defineEmits<{
  'update:modelValue': [items: FoodOrderItem[]];
}>();

const { t, locale } = useStoreText();
const activeCategory = ref<string>('all');

function localizedItemName(item: FoodMenuItem): string {
  return locale.value === 'th-TH' && item.nameTh ? item.nameTh : item.name;
}

function localizedItemDesc(item: FoodMenuItem): string {
  return locale.value === 'th-TH' && item.descriptionTh
    ? item.descriptionTh
    : item.description;
}

function localizedDietary(dietary?: string): string {
  if (!dietary) return '';
  if (dietary.toLowerCase() === 'vegetarian') return t('Vegetarian');
  if (dietary.toLowerCase() === 'vegan') return t('Vegan');
  return t(dietary);
}
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
  props.modelValue.reduce(
    (sum, line) => sum + line.item.price * line.quantity,
    0,
  ),
);

const discountSummary = computed(() =>
  calculatePreOrderDiscount(originalSubtotal.value),
);

const totalItemsCount = computed(() =>
  props.modelValue.reduce((sum, line) => sum + line.quantity, 0),
);
</script>

<template>
  <div class="food-menu-picker">
    <!-- Header banner highlighting 10% discount -->
    <div class="discount-banner">
      <div class="discount-badge">
        <span class="icon"
          ><svg class="gold-icon" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z"
            ></path></svg
        ></span>
        <span class="highlight">{{
          t('Pre-Order Food & Drinks (10% Discount)')
        }}</span>
      </div>
      <p class="discount-copy">
        {{
          t(
            'Enjoy an exclusive 10% discount on all food & beverages when pre-ordering with your table reservation.',
          )
        }}
        {{
          t(
            'You can also order extra food and drinks while playing at your table in the future!',
          )
        }}
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
          {{ t(cat.label) }}
        </button>
      </div>

      <div class="search-box">
        <input
          v-model="searchQuery"
          type="search"
          :placeholder="t('Search food, beverages, snacks…')"
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
          <h4 class="item-name">{{ localizedItemName(item) }}</h4>
          <div v-if="item.popular || item.dietary" class="item-tags-row">
            <span v-if="item.popular" class="popular-pill">{{
              t('popular')
            }}</span>
            <span v-if="item.dietary" class="dietary-pill">{{
              localizedDietary(item.dietary)
            }}</span>
          </div>
          <p class="item-desc">{{ localizedItemDesc(item) }}</p>
        </div>

        <div class="card-footer">
          <div class="price-stack">
            <div class="price-row">
              <span class="discounted-price"
                >฿{{ Math.round(item.price * 0.9) }}</span
              >
              <span class="original-price">฿{{ item.price }}</span>
            </div>
            <span class="discount-tag">{{ t('10% OFF') }}</span>
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
              <span v-if="getQuantity(item.id) === 0"
                >+ {{ t('Add to pre-order') }}</span
              >
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
          <span class="count-badge"
            >{{ totalItemsCount }} {{ t('Pre-order items') }}</span
          >
          <div class="price-breakdown">
            <span class="orig-strike">฿{{ originalSubtotal }}</span>
            <span class="discounted-sum"
              >฿{{ discountSummary.discountedTotal }}</span
            >
            <span class="saved-pill"
              >Saved ฿{{ discountSummary.discountAmount }}</span
            >
          </div>
        </div>
        <p class="tray-footnote">
          <span class="gold-bullet">◆</span>
          {{
            t(
              'Pre-ordered items will be prepared fresh upon your table check-in.',
            )
          }}
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
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  gap: 16px;
}

@media (max-width: 640px) {
  .menu-grid {
    grid-template-columns: 1fr;
  }
}

.menu-card {
  background: #ffffff;
  border: 1px solid #e0d5c1;
  border-radius: 10px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.menu-card:hover {
  border-color: #c6ad7a;
  box-shadow:
    0 6px 18px rgba(20, 43, 32, 0.08),
    0 0 12px rgba(198, 173, 122, 0.15);
  transform: translateY(-2px);
}

.menu-card.has-qty {
  border-color: #203c30;
  background: #faf8f3;
  box-shadow: 0 0 0 1px #203c30;
}

.card-header {
  margin-bottom: 12px;
}

.item-name {
  margin: 0 0 6px 0;
  font-size: 15px;
  font-weight: 700;
  color: #1b2f25;
  line-height: 1.35;
}

.item-tags-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.popular-pill {
  font-size: 10.5px;
  font-weight: 600;
  text-transform: lowercase; /* explicitly lowercase as requested */
  background: #fde8cf;
  color: #8c4e0b;
  padding: 2px 7px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.dietary-pill {
  font-size: 10.5px;
  font-weight: 600;
  background: #e2f0d9;
  color: #2e6022;
  padding: 2px 7px;
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
  margin-top: 8px;
  gap: 8px;
}

.price-stack {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex-shrink: 0;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.discounted-price {
  font-size: 16px;
  font-weight: 800;
  color: #1a4231;
}

.original-price {
  font-size: 12px;
  text-decoration: line-through;
  color: #8e887d;
}

.discount-tag {
  display: inline-block;
  align-self: flex-start;
  font-size: 10px;
  font-weight: 700;
  color: #b54708;
  background: #fef0c7;
  padding: 1px 5px;
  border-radius: 3px;
  white-space: nowrap;
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

.gold-icon {
  width: 18px;
  height: 18px;
  color: #ddc18c;
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
  filter: drop-shadow(0 0 4px rgba(198, 173, 122, 0.35));
}
</style>
