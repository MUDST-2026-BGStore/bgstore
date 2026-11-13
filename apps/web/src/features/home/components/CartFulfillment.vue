<script setup lang="ts">
import BranchSelect from './BranchSelect.vue';
import { computed, useId } from 'vue';
import { useBranchDirectoryStore } from '../stores/branch-directory';
import { useCartStore } from '../stores/cart';
import { useStoreText } from '../data/store-text';

const cart = useCartStore();
const directory = useBranchDirectoryStore();
const groupId = useId();
const { t } = useStoreText();
// Which lines a branch can fulfill is only known for the cart's current choice;
// the server flags shortages after the shopper picks a branch.
const pickupBranches = computed(() =>
  directory.branches.filter((branch) => branch.pickup),
);
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

    <p
      v-if="cart.method === 'shipping' && !cart.fulfillmentBranch"
      class="fulfillment-note"
    >
      {{ t('Online orders are not available right now.') }}
    </p>

    <BranchSelect
      v-if="cart.method === 'pickup'"
      :model-value="cart.pickupBranchId"
      :options="pickupBranches"
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
  font:
    13px/1.5 Arial,
    sans-serif;
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
input[type='radio'] {
  accent-color: #244c38;
  width: 15px;
  height: 15px;
  cursor: pointer;
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
.fulfillment-note {
  margin-top: 8px;
  font-size: 13px;
  color: #6b4a1d;
}
</style>
