<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  AppCollectionPanel,
  AppDescriptionList,
  AppInspector,
  AppMasterDetail,
  type FilterValues,
} from '../../../../shared/ui';
import {
  rentalCopySamples,
  type RentalCopySample,
} from '../management-inventory.samples';
import InventoryFilterBar from './InventoryFilterBar.vue';
import InventoryStockTable from './InventoryStockTable.vue';
import InventoryRecordRow from './InventoryRecordRow.vue';

const search = ref('');
const filters = ref<FilterValues>({});
const selectedCode = ref<string | null>(null);
const selectedItem = computed(() =>
  rentalCopySamples.find((item) => item.assetCode === selectedCode.value),
);
const assetStatus = (item: RentalCopySample) =>
  item.state === 'On shelf'
    ? 'Available'
    : item.state === 'At table'
      ? 'In use'
      : 'Maintenance';
const rows = computed(() =>
  rentalCopySamples.filter(
    (item) =>
      `${item.name} ${item.sku} ${item.assetCode} ${item.location}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()) &&
      (!filters.value.status?.length ||
        filters.value.status.includes(assetStatus(item))),
  ),
);
const columns = [
  { label: 'Item / asset code' },
  { label: 'Location / condition' },
  { label: 'Status' },
  { label: 'Actions', action: true },
];
const metrics = computed(() => [
  { label: 'Assets', value: rentalCopySamples.length },
  {
    label: 'Available',
    value: rentalCopySamples.filter((item) => assetStatus(item) === 'Available')
      .length,
  },
  {
    label: 'In use',
    value: rentalCopySamples.filter((item) => assetStatus(item) === 'In use')
      .length,
  },
  {
    label: 'Maintenance',
    value: rentalCopySamples.filter(
      (item) => assetStatus(item) === 'Maintenance',
    ).length,
    tone: 'danger' as const,
  },
]);
let detailsTrigger: HTMLElement | null = null;
function showDetails(item: RentalCopySample, event: MouseEvent) {
  const target = event.currentTarget as HTMLElement;
  detailsTrigger = target.matches('button')
    ? target
    : target.querySelector<HTMLElement>('[data-record-trigger]');
  selectedCode.value = item.assetCode;
}
function closeDetails() {
  selectedCode.value = null;
  detailsTrigger?.focus({ preventScroll: true });
}
function clearFilters() {
  search.value = '';
  filters.value = {};
}
watch([search, filters], () => {
  selectedCode.value = null;
});
</script>

<template>
  <AppCollectionPanel
    title="Rental assets"
    title-id="rental-assets-title"
    :metrics="metrics"
    :result-label="`${rows.length} of ${rentalCopySamples.length} assets`"
  >
    <template #filters>
      <InventoryFilterBar
        v-model:search="search"
        v-model:filters="filters"
        search-label="Search rental assets"
        search-placeholder="Name, SKU or asset code"
        :groups="[
          {
            id: 'status',
            label: 'Status',
            options: ['Available', 'In use', 'Maintenance'].map((label) => ({
              value: label,
              label,
            })),
          },
        ]"
      />
    </template>
    <AppMasterDetail :inspecting="!!selectedItem">
      <InventoryStockTable
        label="Rental assets table"
        :columns="columns"
        :row-count="rows.length"
        :filters-active="!!search.trim() || !!filters.status?.length"
        @clear-filters="clearFilters"
      >
        <InventoryRecordRow
          v-for="item in rows"
          :key="item.assetCode"
          :name="item.name"
          :subtitle="item.sku"
          :identifier="item.assetCode"
          :status="assetStatus(item)"
          :tone="
            item.state === 'On shelf'
              ? 'success'
              : item.state === 'Maintenance'
                ? 'danger'
                : 'warning'
          "
          :active="selectedCode === item.assetCode"
          @inspect="showDetails(item, $event)"
        >
          <td>{{ item.location }}</td>
        </InventoryRecordRow>
      </InventoryStockTable>
      <AppInspector
        v-if="selectedItem"
        :title="`${selectedItem.name} · ${selectedItem.assetCode}`"
        title-id="rental-asset-details-title"
        @close="closeDetails"
      >
        <AppDescriptionList
          :items="[
            { label: 'SKU', value: selectedItem.sku },
            { label: 'Asset code', value: selectedItem.assetCode },
            { label: 'Status', value: assetStatus(selectedItem) },
            { label: 'Location / condition', value: selectedItem.location },
          ]"
        />
      </AppInspector>
    </AppMasterDetail>
  </AppCollectionPanel>
</template>
