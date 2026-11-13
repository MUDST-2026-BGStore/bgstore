<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  AppBadge,
  AppButton,
  AppCollectionPanel,
  AppDescriptionList,
  AppField,
  AppSelect,
  type FilterValues,
  AppInspector,
  AppMasterDetail,
  AppRecordLink,
  AppTableActions,
} from '../../../../shared/ui';
import {
  channelLabels,
  fulfillmentSamples,
  retailStockSamples,
} from '../management-inventory.samples';
import InventoryFilterBar from './InventoryFilterBar.vue';
import InventoryStockTable from './InventoryStockTable.vue';

defineProps<{ branchName: string }>();
const search = ref('');
const filters = ref<FilterValues>({});
const queue = ref('open');
const selectedReference = ref<string | null>(null);
const selectedOrder = computed(() =>
  fulfillmentSamples.find((item) => item.reference === selectedReference.value),
);
// Completed counter sales remain in history; they aren't outbound work.
const completed = (item: (typeof fulfillmentSamples)[number]) =>
  item.state === 'Sale completed';
const orderStatus = (item: (typeof fulfillmentSamples)[number]) =>
  completed(item) ? 'Completed' : item.state;
const scopedOrders = computed(() =>
  fulfillmentSamples.filter(
    (item) =>
      queue.value === 'all' ||
      (queue.value === 'completed' ? completed(item) : !completed(item)),
  ),
);
const statusOptions = computed(() => [
  ...new Set(scopedOrders.value.map(orderStatus)),
]);
const rows = computed(() =>
  scopedOrders.value.filter(
    (item) =>
      `${item.reference} ${item.sku}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()) &&
      (!filters.value.status?.length ||
        filters.value.status.includes(orderStatus(item))) &&
      (!filters.value.method?.length ||
        filters.value.method.includes(item.channel)),
  ),
);
const metrics = [
  {
    label: 'Open orders',
    value: fulfillmentSamples.filter((item) => !completed(item)).length,
  },
  {
    label: 'Awaiting dispatch',
    value: fulfillmentSamples.filter(
      (item) => item.state === 'Awaiting dispatch',
    ).length,
  },
  {
    label: 'Awaiting collection',
    value: fulfillmentSamples.filter(
      (item) => item.state === 'Awaiting collection',
    ).length,
  },
  {
    label: 'Completed orders',
    value: fulfillmentSamples.filter(completed).length,
  },
];
const columns = [
  { label: 'Order' },
  { label: 'Method' },
  { label: 'Item / SKU' },
  { label: 'Quantity', numeric: true },
  { label: 'Status' },
  { label: 'Actions', action: true },
];
let detailsTrigger: HTMLElement | null = null;
function showDetails(reference: string, event: MouseEvent) {
  const target = event.currentTarget as HTMLElement;
  detailsTrigger = target.matches('button')
    ? target
    : target.querySelector<HTMLElement>('[data-record-trigger]');
  selectedReference.value = reference;
}
function closeDetails() {
  selectedReference.value = null;
  detailsTrigger?.focus({ preventScroll: true });
}
function clearFilters() {
  search.value = '';
  filters.value = {};
}
watch(queue, clearFilters);
watch([search, filters, queue], () => {
  selectedReference.value = null;
});
const productName = (sku: string) =>
  retailStockSamples.find((item) => item.sku === sku)?.name || sku;
</script>

<template>
  <AppCollectionPanel
    title="Fulfillment"
    title-id="fulfillment-title"
    :metrics="metrics"
    :result-label="`${rows.length} of ${scopedOrders.length} orders`"
  >
    <template #filters>
      <AppField label="Queue" input-id="fulfillment-queue" layout="inline">
        <AppSelect
          input-id="fulfillment-queue"
          v-model="queue"
          label="Queue"
          :options="[
            { value: 'open', label: 'Open orders' },
            { value: 'completed', label: 'Completed orders' },
            { value: 'all', label: 'All orders' },
          ]"
        />
      </AppField>
      <InventoryFilterBar
        v-model:search="search"
        v-model:filters="filters"
        search-label="Search orders"
        search-placeholder="Order or SKU"
        :groups="[
          {
            id: 'status',
            label: 'Status',
            options: statusOptions.map((label) => ({ value: label, label })),
          },
          {
            id: 'method',
            label: 'Method',
            options: Object.entries(channelLabels).map(([value, label]) => ({
              value,
              label,
            })),
          },
        ]"
      />
    </template>
    <AppMasterDetail :inspecting="!!selectedOrder">
      <InventoryStockTable
        label="Fulfillment orders table"
        :columns="columns"
        :row-count="rows.length"
        :filters-active="
          !!search.trim() ||
          Object.values(filters).some((values) => values.length > 0)
        "
        @clear-filters="clearFilters"
      >
        <tr
          v-for="item in rows"
          :key="item.reference"
          data-record-row
          :data-active="selectedReference === item.reference"
          @click="showDetails(item.reference, $event)"
        >
          <td>
            <AppRecordLink
              data-record-trigger
              :aria-expanded="selectedReference === item.reference"
              @click.stop="showDetails(item.reference, $event)"
              >{{ item.reference }}</AppRecordLink
            >
          </td>
          <td>{{ channelLabels[item.channel] }}</td>
          <td>
            <strong>{{ productName(item.sku) }}</strong
            ><span class="order-sku">{{ item.sku }}</span>
          </td>
          <td class="numeric">{{ item.quantity }}</td>
          <td>
            <AppBadge :tone="completed(item) ? 'success' : 'warning'">{{
              orderStatus(item)
            }}</AppBadge>
          </td>
          <AppTableActions
            ><AppButton
              :aria-label="`View details for ${item.reference}`"
              :aria-expanded="selectedReference === item.reference"
              @click.stop="showDetails(item.reference, $event)"
              >Details</AppButton
            ></AppTableActions
          >
        </tr>
      </InventoryStockTable>
      <AppInspector
        v-if="selectedOrder"
        :title="selectedOrder.reference"
        title-id="fulfillment-details-title"
        @close="closeDetails"
      >
        <AppDescriptionList
          :items="[
            { label: 'Order', value: selectedOrder.reference },
            { label: 'Method', value: channelLabels[selectedOrder.channel] },
            { label: 'Branch', value: branchName },
            { label: 'Status', value: orderStatus(selectedOrder) },
          ]"
        />
        <h3 class="order-section-title">Order items</h3>
        <AppDescriptionList
          :items="[
            { label: 'Item', value: productName(selectedOrder.sku) },
            { label: 'SKU', value: selectedOrder.sku },
            { label: 'Quantity', value: selectedOrder.quantity },
          ]"
        />
      </AppInspector>
    </AppMasterDetail>
  </AppCollectionPanel>
</template>

<style scoped>
.order-sku {
  display: block;
  margin-top: 4px;
  color: var(--ui-muted);
  font: 12px monospace;
}
.order-section-title {
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 8px;
}
</style>
