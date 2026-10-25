<script setup lang="ts">
// TODO(branch-inventory-api): This unused comparison component reads fixtures.
// Wire branch library membership and edition stock to implemented branch/inventory
// endpoints before exposing it on live product pages. Membership is not visit availability.
import StoreAccordion from './StoreAccordion.vue';
import { computed, ref } from 'vue';
import { branches, rentalLibraryAt, retailStockAt } from '../data/branches';
import { useStoreText } from '../data/store-text';
const props = defineProps<{
  gameId: string;
  variantId?: string;
}>();
const { t } = useStoreText();
const query = ref('');
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
    <label v-if="branches.length > 8" class="availability-search"
      >{{ t('Find a branch')
      }}<input
        v-model="query"
        type="search"
        :placeholder="t('Search branches…')"
    /></label>
    <div
      class="branch-table"
      role="region"
      :aria-label="t('Branch libraries and retail stock')"
      tabindex="0"
    >
      <table>
        <thead>
          <tr>
            <th scope="col">{{ t('Branch') }}</th>
            <th scope="col">{{ t('Rental library') }}</th>
            <th v-if="variantId" scope="col">{{ t('New copy') }}</th>
          </tr>
        </thead>
        <tbody>
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
        </tbody>
      </table>
    </div>
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
table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
  table-layout: fixed;
  font-size: 12px;
}
th,
td {
  text-align: left;
  vertical-align: top;
  padding: 9px 8px 9px 0;
  border-bottom: 1px solid #d3d9d4;
  overflow-wrap: anywhere;
}
thead th {
  color: #4e5b53;
  font-weight: 400;
}
tbody th {
  font-weight: 600;
}
tbody tr:last-child th,
tbody tr:last-child td {
  border-bottom: 0;
}
</style>
