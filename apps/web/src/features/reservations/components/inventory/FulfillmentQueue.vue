<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { fulfillOrder, listBranchOrders } from '@theparlor/api-client';
import { collectPages } from '../../../../shared/lib/pagination';
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
  AppMessageBar,
  AppRecordLink,
  AppTableActions,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../../management/context';
import {
  channelLabels,
  fulfillmentSamples,
  retailStockSamples,
  toFulfillmentRow,
  type FulfillmentSample,
} from '../management-inventory.samples';
import { branchGamesKey } from '../../management/branch-games';
import InventoryFilterBar from './InventoryFilterBar.vue';
import InventoryStockTable from './InventoryStockTable.vue';

defineProps<{ branchName: string }>();
const queryClient = useQueryClient();
const { selectedBranchId } = useManagementWorkspace();
const ordersQuery = useQuery({
  queryKey: computed(() => [
    'workspace',
    'branchOrders',
    selectedBranchId.value,
  ]),
  enabled: computed(() => Boolean(selectedBranchId.value)),
  queryFn: ({ signal }) =>
    collectPages((cursor) =>
      listBranchOrders(
        selectedBranchId.value,
        { limit: 100, cursor },
        undefined,
        signal,
      ),
    ),
});
// Server orders; sample orders remain only while the branch has none.
const orders = computed<readonly FulfillmentSample[]>(() => {
  const live = ordersQuery.data.value;
  return live?.length ? live.map(toFulfillmentRow) : fulfillmentSamples;
});
watch(selectedBranchId, () => {
  selectedReference.value = null;
  fulfillError.value = '';
});

const fulfillError = ref('');
const fulfillMutation = useMutation({
  mutationFn: ({ branchId, orderId }: { branchId: string; orderId: string }) =>
    fulfillOrder(branchId, orderId),
  onSuccess: (_order, { branchId }) =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: ['workspace', 'branchOrders', branchId],
      }),
      queryClient.invalidateQueries({ queryKey: branchGamesKey(branchId) }),
    ]),
});

async function handleFulfillOrder(order: FulfillmentSample) {
  if (!order.orderId || !isOpenOrder(order)) return;
  fulfillError.value = '';
  try {
    await fulfillMutation.mutateAsync({
      branchId: selectedBranchId.value,
      orderId: order.orderId,
    });
  } catch {
    fulfillError.value =
      'The order could not be fulfilled. Refresh and try again.';
  }
}

const search = ref('');
const filters = ref<FilterValues>({});
const queue = ref('open');
const selectedReference = ref<string | null>(null);
const selectedOrder = computed(() =>
  orders.value.find((item) => item.reference === selectedReference.value),
);
// Completed counter sales and cancelled orders aren't outbound work.
const completed = (item: FulfillmentSample) => item.state === 'Sale completed';
const cancelled = (item: FulfillmentSample) => item.state === 'Cancelled';
const isOpenOrder = (item: FulfillmentSample) =>
  !completed(item) && !cancelled(item);
const orderStatus = (item: FulfillmentSample) =>
  completed(item) ? 'Completed' : item.state;
const scopedOrders = computed(() =>
  orders.value.filter(
    (item) =>
      queue.value === 'all' ||
      (queue.value === 'completed' ? completed(item) : isOpenOrder(item)),
  ),
);
const statusOptions = computed(() => [
  ...new Set(scopedOrders.value.map(orderStatus)),
]);
const rows = computed(() =>
  scopedOrders.value.filter(
    (item) =>
      `${item.reference} ${item.sku} ${item.itemName ?? ''}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()) &&
      (!filters.value.status?.length ||
        filters.value.status.includes(orderStatus(item))) &&
      (!filters.value.method?.length ||
        filters.value.method.includes(item.channel)),
  ),
);
const metrics = computed(() => [
  {
    label: 'Open orders',
    value: orders.value.filter(isOpenOrder).length,
  },
  {
    label: 'Awaiting dispatch',
    value: orders.value.filter((item) => item.state === 'Awaiting dispatch')
      .length,
  },
  {
    label: 'Awaiting collection',
    value: orders.value.filter((item) => item.state === 'Awaiting collection')
      .length,
  },
  {
    label: 'Completed orders',
    value: orders.value.filter(completed).length,
  },
]);
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
const productName = (sku: string, itemName?: string) =>
  itemName || retailStockSamples.find((item) => item.sku === sku)?.name || sku;
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
            <strong>{{ productName(item.sku, item.itemName) }}</strong
            ><span class="order-sku">{{ item.sku }}</span>
          </td>
          <td class="numeric">{{ item.quantity }}</td>
          <td>
            <AppBadge
              :tone="
                completed(item)
                  ? 'success'
                  : cancelled(item)
                    ? 'danger'
                    : 'warning'
              "
              >{{ orderStatus(item) }}</AppBadge
            >
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
            {
              label: 'Item',
              value: productName(selectedOrder.sku, selectedOrder.itemName),
            },
            { label: 'SKU', value: selectedOrder.sku },
            { label: 'Quantity', value: selectedOrder.quantity },
          ]"
        />
        <div
          v-if="selectedOrder.orderId && isOpenOrder(selectedOrder)"
          class="order-detail-actions"
        >
          <AppButton
            appearance="primary"
            :disabled="fulfillMutation.isPending.value"
            @click="handleFulfillOrder(selectedOrder)"
            >Fulfill order</AppButton
          >
        </div>
        <AppMessageBar v-if="fulfillError" tone="error">{{
          fulfillError
        }}</AppMessageBar>
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
.order-detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
</style>
