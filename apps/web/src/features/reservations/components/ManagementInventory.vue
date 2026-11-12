<script setup lang="ts">
import { ref } from 'vue';
import { AppPage } from '../../../shared/ui';
import RentalAssetsPanel from './inventory/RentalAssetsPanel.vue';
import FulfillmentQueue from './inventory/FulfillmentQueue.vue';

defineProps<{ branchName: string }>();
const views = [
  { id: 'stock', label: 'Stock for sale' },
  { id: 'rentals', label: 'Rental assets' },
  { id: 'fulfillment', label: 'Fulfillment' },
] as const;
const view = ref<(typeof views)[number]['id']>('stock');
</script>

<template>
  <AppPage
    class="inventory-workspace"
    title="Inventory & fulfillment"
    :subtitle="branchName"
    title-id="inventory-title"
  >
    <nav class="inventory-tabs" aria-label="Inventory views">
      <button
        v-for="item in views"
        :key="item.id"
        type="button"
        :aria-current="view === item.id ? 'page' : undefined"
        @click="view = item.id"
      >
        {{ item.label }}
      </button>
    </nav>
    <Transition
      name="workspace-panel"
      mode="out-in"
      @before-leave="(element) => element.setAttribute('inert', '')"
    >
      <div :key="view">
        <slot v-if="view === 'stock'" name="stock" />
        <RentalAssetsPanel v-else-if="view === 'rentals'" />
        <FulfillmentQueue v-else :branch-name="branchName" />
      </div>
    </Transition>
  </AppPage>
</template>

<style scoped>
.inventory-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--ui-divider);
  overflow-x: auto;
}
.inventory-tabs button {
  background: transparent;
  padding: 12px 0;
  border: 0;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  color: var(--ui-muted);
  font: inherit;
  cursor: pointer;
}
.inventory-tabs button[aria-current] {
  border-bottom-color: var(--ui-brand);
  color: var(--ui-text);
  font-weight: 600;
}
@media (max-width: 700px) {
  .inventory-tabs {
    gap: 18px;
  }
}
</style>
