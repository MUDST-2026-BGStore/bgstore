<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  AppButton,
  AppField,
  AppCollectionPanel,
  AppNumberStepper,
  AppDescriptionList,
  AppInspector,
  AppMasterDetail,
  AppMessageBar,
  AppTableSelect,
  useTableSelection,
  type FilterValues,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
import type { SaleStockItem } from '../../components/inventory/sale-stock';
import type { StockReceiptEntry } from '../useWorkspaceSaleStock';
import InventoryFilterBar from '../../components/inventory/InventoryFilterBar.vue';
import InventoryStockTable from '../../components/inventory/InventoryStockTable.vue';
import InventoryRecordRow from '../../components/inventory/InventoryRecordRow.vue';
import StockReceiveDialog from './StockReceiveDialog.vue';
import StockAdjustmentDialog from './StockAdjustmentDialog.vue';

const { selectedBranchId, saleStock } = useManagementWorkspace();
const itemTypes = [{ id: 'retail', label: 'Retail games' }] as const;
const itemType = ref<'retail'>('retail');
const search = ref('');
const filters = ref<FilterValues>({});
const columns = [
  { label: 'Item / SKU' },
  { label: 'On hand', numeric: true },
  { label: 'Reserved', numeric: true },
  { label: 'Available', numeric: true },
  { label: 'Status' },
  { label: 'Actions', action: true },
];
const { items, saving, error, setOnHand, receive } = saleStock;
const scopedItems = computed(() =>
  items.value.filter((item) => item.type === 'retail'),
);
const categories = computed(() => [
  ...new Set(scopedItems.value.map((item) => item.category)),
]);
const visibleItems = computed(() =>
  scopedItems.value.filter(
    (item) =>
      `${item.name} ${item.description} ${item.sku}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()) &&
      (!filters.value.status?.length ||
        filters.value.status.includes(item.status) ||
        (filters.value.status.includes('NEED_REORDER') &&
          item.status !== 'In stock')) &&
      (!filters.value.category?.length ||
        filters.value.category.includes(item.category)),
  ),
);
// Counts of SKUs avoid adding unlike units (cans, bottles and game copies).
const metrics = computed(() => [
  { label: 'Items', value: scopedItems.value.length },
  {
    label: 'In stock',
    value: scopedItems.value.filter((item) => item.status === 'In stock')
      .length,
  },
  {
    label: 'Low stock',
    value: scopedItems.value.filter((item) => item.status === 'Low stock')
      .length,
    tone: 'warning' as const,
  },
  {
    label: 'Unavailable',
    value: scopedItems.value.filter(
      (item) =>
        item.status === 'Out of stock' || item.status === 'Fully reserved',
    ).length,
    tone: 'danger' as const,
  },
]);
const filtersActive = computed(
  () =>
    !!search.value.trim() ||
    Object.values(filters.value).some((values) => values.length > 0),
);
function clearFilters() {
  search.value = '';
  filters.value = {};
}
function changeType(next: typeof itemType.value) {
  itemType.value = next;
  clearFilters();
}
const selectedItemId = ref<string | null>(null);
const selectedItem = computed(() =>
  items.value.find((item) => item.id === selectedItemId.value),
);
let detailsTrigger: HTMLElement | null = null;
function showDetails(item: SaleStockItem, event: MouseEvent) {
  const target = event.currentTarget as HTMLElement;
  detailsTrigger = target.matches('button')
    ? target
    : target.querySelector<HTMLElement>('[data-record-trigger]');
  selectedItemId.value = item.id;
}
function closeDetails() {
  selectedItemId.value = null;
  detailsTrigger?.focus({ preventScroll: true });
}
const table = ref<InstanceType<typeof InventoryStockTable>>();
const selection = useTableSelection(
  computed(() => visibleItems.value.map((item) => item.id)),
);
const selectedRows = computed(() =>
  visibleItems.value.filter((item) => selection.selectedIds.value.has(item.id)),
);
const pendingReceiveIds = ref<string[]>([]);
const pendingReceive = computed(() =>
  items.value.filter((item) => pendingReceiveIds.value.includes(item.id)),
);
const pendingAdjustmentId = ref<string | null>(null);
const pendingAdjustment = computed(() =>
  items.value.find((item) => item.id === pendingAdjustmentId.value),
);
let commandTrigger: HTMLElement | null = null;
function openReceive(rows: SaleStockItem[], event?: MouseEvent) {
  commandTrigger = event ? (event.currentTarget as HTMLElement) : null;
  pendingReceiveIds.value = rows.map((item) => item.id);
}
function openAdjustment(item: SaleStockItem, event: MouseEvent) {
  commandTrigger = event.currentTarget as HTMLElement;
  pendingAdjustmentId.value = item.id;
}
async function restoreCommandFocus() {
  await nextTick();
  if (commandTrigger?.isConnected)
    commandTrigger.focus({ preventScroll: true });
  else table.value?.focus();
}
async function saveAdjustment(id: string, quantity: number) {
  if (!(await setOnHand(id, quantity))) return;
  pendingAdjustmentId.value = null;
  await restoreCommandFocus();
}
async function receiveStock(entries: StockReceiptEntry[]) {
  if (!(await receive(entries))) return;
  pendingReceiveIds.value = [];
  selection.clear();
  await restoreCommandFocus();
}
watch([search, filters, itemType], () => {
  selectedItemId.value = null;
});
watch(selectedBranchId, () => {
  clearFilters();
  selectedItemId.value = null;
  selection.clear();
  pendingReceiveIds.value = [];
  pendingAdjustmentId.value = null;
});
</script>

<template>
  <AppCollectionPanel
    title="Retail stock"
    title-id="sale-stock-title"
    :metrics="metrics"
    :result-label="`${visibleItems.length} of ${scopedItems.length} items`"
    :selected-count="selection.count.value"
    @clear-selection="selection.clear"
  >
    <template #navigation>
      <div class="stock-type-tabs" role="group" aria-label="Item type">
        <button
          v-for="type in itemTypes"
          :key="type.id"
          type="button"
          :aria-pressed="itemType === type.id"
          @click="changeType(type.id)"
        >
          {{ type.label }}
        </button>
      </div>
    </template>
    <template #filters>
      <InventoryFilterBar
        v-model:search="search"
        v-model:filters="filters"
        search-label="Search stock"
        search-placeholder="Name or SKU"
        :groups="[
          {
            id: 'status',
            label: 'Status',
            options: [
              ...[
                'In stock',
                'Low stock',
                'Fully reserved',
                'Out of stock',
              ].map((label) => ({ value: label, label })),
              { value: 'NEED_REORDER', label: 'Needs attention' },
            ],
          },
          {
            id: 'category',
            label: 'Category',
            options: categories.map((label) => ({ value: label, label })),
          },
        ]"
      />
    </template>
    <template #selection>
      <AppButton appearance="primary" @click="openReceive(selectedRows)"
        >Receive stock</AppButton
      >
    </template>
    <AppMessageBar v-if="error" tone="error">{{ error }}</AppMessageBar>
    <AppMasterDetail :inspecting="!!selectedItem">
      <InventoryStockTable
        ref="table"
        label="Stock for sale table"
        :columns="columns"
        table-class="sale-stock-table"
        :row-count="visibleItems.length"
        :filters-active="filtersActive"
        @clear-filters="clearFilters"
      >
        <template #selection-header>
          <AppTableSelect
            header
            label="Select all receivable items"
            :disabled="!visibleItems.length"
            :model-value="selection.allSelected.value"
            :indeterminate="selection.indeterminate.value"
            @update:model-value="selection.toggleAll"
          />
        </template>
        <InventoryRecordRow
          v-for="item in visibleItems"
          :key="item.id"
          :name="item.name"
          :subtitle="item.description"
          :identifier="item.sku"
          :status="item.status"
          :tone="item.tone"
          :active="selectedItemId === item.id"
          :selected="selection.selectedIds.value.has(item.id)"
          @inspect="showDetails(item, $event)"
        >
          <template #selection>
            <AppTableSelect
              :label="`Select ${item.name}`"
              :model-value="selection.selectedIds.value.has(item.id)"
              @update:model-value="selection.toggle(item.id, $event)"
            />
          </template>
          <template #actions>
            <AppButton
              appearance="primary"
              :aria-label="`Receive stock for ${item.name}, ${item.sku}`"
              @click.stop="openReceive([item], $event)"
              >Receive stock</AppButton
            >
            <AppButton
              :aria-label="`Adjust quantity for ${item.name}, ${item.sku}`"
              @click.stop="openAdjustment(item, $event)"
              >Adjust</AppButton
            >
          </template>
          <td class="numeric">
            {{ item.onHand
            }}<span class="record-secondary">{{ item.unit }}</span>
          </td>
          <td class="numeric">{{ item.reserved }}</td>
          <td class="numeric">
            <strong>{{ item.available }}</strong>
          </td>
        </InventoryRecordRow>
      </InventoryStockTable>
      <AppInspector
        v-if="selectedItem"
        :title="selectedItem.name"
        title-id="sale-stock-details-title"
        @close="closeDetails"
      >
        <AppDescriptionList
          :items="[
            { label: 'SKU', value: selectedItem.sku },
            { label: 'Category', value: selectedItem.category },
            { label: 'Unit', value: selectedItem.unit },
            {
              label: 'On hand',
              value: `${selectedItem.onHand} ${selectedItem.unit}`,
            },
            {
              label: 'Reserved',
              value: `${selectedItem.reserved} ${selectedItem.unit}`,
            },
            {
              label: 'Available',
              value: `${selectedItem.available} ${selectedItem.unit}`,
            },
            ...(selectedItem.reorderPoint === null
              ? []
              : [
                  {
                    label: 'Reorder point',
                    value: `${selectedItem.reorderPoint} ${selectedItem.unit}`,
                  },
                ]),
            { label: 'Status', value: selectedItem.status },
          ]"
        />
        <!-- Live editions report one held count, already shown as Reserved. -->
        <template v-if="!selectedItem.source.editionId">
          <h3 class="stock-section-title">Reservations</h3>
          <AppDescriptionList
            :items="[
              { label: 'Delivery', value: selectedItem.source.shippingHeld },
              { label: 'Store pickup', value: selectedItem.source.pickupHeld },
            ]"
          />
        </template>
        <AppField
          class="stock-adjustment"
          label="Adjust quantity"
          input-id="sale-stock-adjust"
        >
          <AppNumberStepper
            input-id="sale-stock-adjust"
            :model-value="selectedItem.onHand"
            :min="selectedItem.reserved"
            :disabled="saving"
            :label="`Stock for ${selectedItem.name}`"
            @update:model-value="setOnHand(selectedItem.id, $event)"
          />
        </AppField>
        <div class="stock-detail-actions">
          <AppButton
            appearance="primary"
            @click="openReceive([selectedItem], $event)"
            >Receive stock</AppButton
          >
        </div>
      </AppInspector>
    </AppMasterDetail>
  </AppCollectionPanel>
  <StockReceiveDialog
    v-if="pendingReceiveIds.length"
    :items="pendingReceive"
    @close="pendingReceiveIds = []"
    @receive="receiveStock"
  />
  <StockAdjustmentDialog
    v-if="pendingAdjustment"
    :item="pendingAdjustment"
    @close="pendingAdjustmentId = null"
    @adjust="saveAdjustment"
  />
</template>

<style scoped>
:deep(.sale-stock-table) {
  table-layout: fixed;
  min-width: 852px;
}
:deep(.sale-stock-table th:nth-child(1)) {
  width: 44px;
}
:deep(.sale-stock-table th:nth-child(2)) {
  width: 200px;
}
:deep(.sale-stock-table th:nth-child(3)),
:deep(.sale-stock-table th:nth-child(4)),
:deep(.sale-stock-table th:nth-child(5)) {
  width: 88px;
}
:deep(.sale-stock-table th:nth-child(6)) {
  width: 124px;
}
:deep(.sale-stock-table th:last-child) {
  width: 220px;
}
:deep(.sale-stock-table .inventory-identity) {
  overflow-wrap: anywhere;
}
.stock-type-tabs {
  display: flex;
  gap: 24px;
  overflow-x: auto;
}
.stock-type-tabs button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 10px 0;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--ui-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.stock-type-tabs button[aria-pressed='true'] {
  border-bottom-color: var(--ui-brand);
  color: var(--ui-brand);
  font-weight: 600;
}
.stock-type-tabs button:hover {
  color: var(--ui-text);
}
.stock-type-tabs button:focus-visible {
  outline: 2px solid var(--ui-brand);
  outline-offset: -3px;
}
.stock-adjustment {
  margin-top: 20px;
}
.stock-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
.stock-section-title {
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 8px;
}
.removal-list {
  margin: 12px 0 0;
  padding-left: 20px;
  font-size: 14px;
  line-height: 24px;
}
</style>
