<script setup lang="ts">
import BranchSelect from './BranchSelect.vue';
import { useId } from 'vue';
import { branches, retailStockAt } from '../data/branches';
import { useCartStore } from '../stores/cart';
import { CARRIERS } from '../stores/orders';
import { useStoreText } from '../data/store-text';

const cart = useCartStore();
const groupId = useId();
const { t, locale } = useStoreText();

function branchOption(id: string, name: string) {
  const count = cart.lines.filter(
    (line) => line.quantity > retailStockAt(line.variantId, id),
  ).length;
  return `${t(name)}${cart.count ? ` — ${count ? t(count === 1 ? '{count} item needs changes' : '{count} items need changes', { count }) : t('All items available')}` : ''}`;
}
</script>

<template>
  <fieldset class="fulfillment-picker">
    <legend class="sr-only">{{ t('Delivery method') }}</legend>
    <div class="fulfillment-options">
      <label>
        <input
          type="radio"
          :name="`fulfillment-${groupId}`"
          value="shipping"
          :checked="cart.method === 'shipping'"
          @change="cart.setFulfillment('shipping')"
        />
        {{ t('Ship to an address') }}
      </label>
      <label>
        <input
          type="radio"
          :name="`fulfillment-${groupId}`"
          value="pickup"
          :checked="cart.method === 'pickup'"
          @change="cart.setFulfillment('pickup')"
        />
        {{ t('Collect in store') }}
      </label>
    </div>

    <!-- Carrier Selection for Shipping -->
    <div v-if="cart.method === 'shipping'" class="carrier-section">
      <span class="carrier-title">
        {{ t('Select Delivery Service') }}
        
      </span>
      <div class="carrier-list">
        <label
          v-for="c in Object.values(CARRIERS)"
          :key="c.id"
          class="carrier-option"
          :class="{ active: cart.carrier === c.id }"
        >
          <input
            type="radio"
            :name="`carrier-${groupId}`"
            :value="c.id"
            :checked="cart.carrier === c.id"
            @change="cart.setCarrier(c.id)"
          />
          <div class="carrier-info">
            <div class="carrier-line">
              <span class="name">{{ locale === 'th-TH' ? c.nameTh : c.name }}</span>
              <span class="price">
                <strong>฿{{ c.fee }}</strong>
              </span>
            </div>
            <div class="carrier-meta">
              <span class="time">{{ locale === 'th-TH' ? c.deliveryDaysTh : c.deliveryDays }}</span>
              <span v-if="c.badge" class="popular-tag">{{ locale === 'th-TH' ? c.badgeTh : c.badge }}</span>
            </div>
          </div>
        </label>
      </div>
    </div>

    <BranchSelect
      v-if="cart.method === 'pickup'"
      :model-value="cart.pickupBranchId"
      :options="
        branches
          .filter((item) => item.pickup)
          .map((branch) => ({
            id: branch.id,
            name: branchOption(branch.id, branch.name),
          }))
      "
      name="pickup-branch"
      label="Collect from"
      class="pickup-label"
      @update:model-value="cart.setFulfillment('pickup', $event)"
    />
  </fieldset>
</template>

<style scoped>
.fulfillment-picker {
  min-width: 0;
  border: 0;
  padding: 0;
  margin: 0 0 14px;
  font: 13px/1.5 Arial, sans-serif;
  color: #202e27;
}
.fulfillment-options {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.fulfillment-options label {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-weight: 600;
}
input[type="radio"] {
  accent-color: #244c38;
  width: 15px;
  height: 15px;
  cursor: pointer;
}

/* Carrier section styling */
.carrier-section {
  margin-top: 10px;
  padding: 10px 12px;
  background: #f4f6f4;
  border: 1px solid #d3d9d4;
  border-radius: 6px;
}
.carrier-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #536359;
  margin-bottom: 8px;
}
.free-shipping-tag {
  color: #1b5e20;
  background: #e8f5e9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: normal;
  text-transform: none;
}
.carrier-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.carrier-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid #d3d9d4;
  border-radius: 5px;
  cursor: pointer;
  transition: all 120ms ease;
}
.carrier-option:hover {
  border-color: #244c38;
  background: #fafdfa;
}
.carrier-option.active {
  border-color: #244c38;
  background: #f0f7f2;
  box-shadow: 0 0 0 1px #244c38;
}
.carrier-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.carrier-line {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 13px;
  color: #172b21;
}
.carrier-line .name {
  font-weight: 600;
}
.carrier-line .price {
  font-size: 13px;
}
.old-fee {
  color: #8c9b91;
  margin-right: 4px;
  font-size: 11px;
}
.free-label {
  color: #1b5e20;
}
.carrier-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #637369;
}
.popular-tag {
  font-size: 9px;
  font-weight: 700;
  background: #c6ad7a;
  color: #14281f;
  padding: 1px 5px;
  border-radius: 3px;
  text-transform: uppercase;
}

.pickup-label {
  display: grid;
  gap: 5px;
  margin-top: 12px;
}
select {
  min-width: 0;
  width: 100%;
  border: 1px solid #829087;
  border-radius: 3px;
  padding: 9px;
  background: white;
  font-size: 13px;
}
select:focus-visible,
input:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
</style>
