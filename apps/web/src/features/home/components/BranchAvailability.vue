<script setup lang="ts">
// TODO(branch-comparison): This comparison component is not mounted anywhere and
// still reads fixture branches, libraries and stock. To use it on product pages,
// read each branch's offering with useBranchOffering (api/inventory.ts) instead.
// Library membership is not visit availability.
import StoreAccordion from './StoreAccordion.vue';
import { computed, ref } from 'vue';
import { AppDataTable, AppField } from '../../../shared/ui';
import { branches, rentalLibraryAt, retailStockAt } from '../data/branches';
import { useStoreText } from '../data/store-text';
const props = defineProps<{
  gameId: string;
  variantId?: string;
}>();
const { t } = useStoreText();
const query = ref('');
const columns = computed(() => [
  { label: t('Branch') },
  { label: t('Rental library') },
  ...(props.variantId ? [{ label: t('New copy') }] : []),
]);
const matchingBranches = computed(() =>
  branches.filter((branch) =>
    t(branch.name)
      .toLocaleLowerCase()
      .includes(query.value.trim().toLocaleLowerCase()),
  ),
);
function rentalText(branchId: string) {
  return t(
    rentalLibraryAt(props.gameId, branchId).offered
      ? 'Included'
      : 'Not offered',
  );
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
          {{
            retailStockAt(variantId, branch.id)
              ? t('{count} in stock', {
                  count: retailStockAt(variantId, branch.id),
                })
              : t('Sold out')
          }}
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
