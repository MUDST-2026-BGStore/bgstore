<script setup lang="ts">
// TODO(fulfillment-api): Replace fixture pickup eligibility and stock labels with
// server capabilities and quotes; shipping services/rates and pickup readiness
// are not implemented. Browser selection does not validate fulfillment.
import BranchSelect from './BranchSelect.vue';
import { useId } from 'vue';
import { branches, retailStockAt } from '../data/branches';
import { useCartStore } from '../stores/cart';
import { useStoreText } from '../data/store-text';
const cart = useCartStore();
const groupId = useId();
const { t } = useStoreText();
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
      <label
        ><input
          type="radio"
          :name="`fulfillment-${groupId}`"
          value="shipping"
          :checked="cart.method === 'shipping'"
          @change="cart.setFulfillment('shipping')"
        />{{ t('Ship to an address') }}</label
      >
      <label
        ><input
          type="radio"
          :name="`fulfillment-${groupId}`"
          value="pickup"
          :checked="cart.method === 'pickup'"
          @change="cart.setFulfillment('pickup')"
        />{{ t('Collect in store') }}</label
      >
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
  margin: 0 0 12px;
  font:
    13px/1.5 Arial,
    sans-serif;
  color: var(--color-parlor-neutral-800);
}
.fulfillment-options {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}
.fulfillment-options label {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
}
input {
  accent-color: var(--color-parlor-green-700);
  width: 15px;
  height: 15px;
}
.pickup-label {
  display: grid;
  gap: 5px;
  margin-top: 12px;
}
select {
  min-width: 0;
  width: 100%;
  border: 1px solid var(--color-parlor-neutral-500);
  border-radius: 3px;
  padding: 9px;
  background: white;
  font-size: 13px;
}
select:focus-visible,
input:focus-visible {
  outline: 2px solid var(--color-parlor-green-700);
  outline-offset: 3px;
}
</style>
