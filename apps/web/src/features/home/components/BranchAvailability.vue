<script setup lang="ts">
// Read-only branch comparison for product pages: reads active branches from the
// branch directory and each branch's offering from the inventory API. Library
// membership is not visit availability.
import StoreAccordion from './StoreAccordion.vue';
import { computed, onMounted, ref } from 'vue';
import { AppDataTable, AppField } from '../../../shared/ui';
import { useBranchOfferings } from '../api/inventory';
import { useBranchDirectoryStore } from '../stores/branch-directory';
import { useStoreText } from '../data/store-text';
const props = defineProps<{
  gameId: string;
  variantId?: string;
}>();
const { t } = useStoreText();
const directory = useBranchDirectoryStore();
onMounted(() => {
  void directory.load();
});
const branches = computed(() => directory.branches);
const offerings = useBranchOfferings(
  () => branches.value.map((branch) => branch.id),
  () => props.gameId,
);
const offeringByBranch = computed(
  () =>
    new Map(
      branches.value.map((branch, index) => [
        branch.id,
        offerings.value[index],
      ]),
    ),
);
const query = ref('');
const columns = computed(() => [
  { label: t('Branch') },
  { label: t('Rental library') },
  ...(props.variantId ? [{ label: t('New copy') }] : []),
]);
const matchingBranches = computed(() => {
  const needle = query.value.trim().toLocaleLowerCase();
  if (!needle) return branches.value;
  return branches.value.filter((branch) =>
    [t(branch.name), branch.name, branch.address]
      .join(' ')
      .toLocaleLowerCase()
      .includes(needle),
  );
});
function rentalText(branchId: string) {
  const entry = offeringByBranch.value.get(branchId);
  if (entry?.isPending) return t('Loading price and stock…');
  if (entry?.isError) return '—';
  return t(entry?.data?.rentalEnabled ? 'Included' : 'Not offered');
}
function retailStockAt(variantId: string, branchId: string) {
  const entry = offeringByBranch.value.get(branchId);
  if (!entry?.data?.saleEnabled) return 0;
  const edition = entry.data.editions.find(
    (item) => item.editionId === variantId,
  );
  return edition?.active ? edition.saleAvailable : 0;
}
function retailText(variantId: string, branchId: string) {
  const entry = offeringByBranch.value.get(branchId);
  if (entry?.isPending) return t('Loading price and stock…');
  if (entry?.isError) return '—';
  const count = retailStockAt(variantId, branchId);
  return count ? t('{count} in stock', { count }) : t('Sold out');
}
</script>
<template>
  <StoreAccordion
    class="branch-availability"
    :title="t('Check other branches')"
  >
    <p v-if="variantId" class="branch-hint">
      {{ t('Retail availability is for the selected edition.') }}
    </p>
    <AppField
      v-if="branches.length > 8"
      class="availability-search"
      :label="t('Find a branch')"
      :input-id="`${gameId}-${variantId || 'rental'}-branch-search`"
      ><input
        :id="`${gameId}-${variantId || 'rental'}-branch-search`"
        v-model="query"
        type="search"
        :placeholder="t('Search branches…')"
    /></AppField>
    <AppDataTable
      class="branch-table"
      :label="t('Branch libraries and retail stock')"
      :columns="columns"
    >
      <tr
        v-for="branch in matchingBranches"
        :key="branch.id"
        class="branch-row"
      >
        <th scope="row">{{ t(branch.name) }}</th>
        <td>{{ rentalText(branch.id) }}</td>
        <td v-if="variantId">
          {{ retailText(variantId, branch.id) }}
        </td>
      </tr>
    </AppDataTable>
    <p v-if="!matchingBranches.length" role="status">
      {{ t('No matching branches. Try another name.') }}
    </p>
  </StoreAccordion>
</template>
<style scoped>
.branch-hint {
  font-size: 12px;
  color: #4e5b53;
  margin-top: 10px;
}
.branch-table {
  max-height: 260px;
  overflow: auto;
}
.availability-search {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}
.availability-search input {
  border: 1px solid #829087;
  padding: 9px;
  border-radius: 3px;
  background: white;
  width: 100%;
}
.branch-table:focus-visible,
input:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
</style>
