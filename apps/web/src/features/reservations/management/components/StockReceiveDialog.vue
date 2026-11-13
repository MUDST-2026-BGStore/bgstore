<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  AppButton,
  AppDataTable,
  AppDialog,
  AppDialogPanel,
  AppNumberStepper,
} from '../../../../shared/ui';
import type { SaleStockItem } from '../../components/inventory/sale-stock';
import type { StockReceiptEntry } from '../useWorkspaceSaleStock';
const props = defineProps<{ items: SaleStockItem[] }>();
const emit = defineEmits<{
  close: [];
  receive: [entries: StockReceiptEntry[]];
}>();
const entries = ref(props.items.map((item) => ({ id: item.id, quantity: 12 })));
const rows = computed(() =>
  entries.value.flatMap((entry) => {
    const item = props.items.find((item) => item.id === entry.id);
    return item ? [{ entry, item }] : [];
  }),
);
const columns = [
  { label: 'Item / SKU' },
  { label: 'On hand', numeric: true },
  { label: 'Quantity received' },
  { label: 'New on hand', numeric: true },
];
const canReceive = computed(
  () =>
    entries.value.length > 0 &&
    rows.value.length === entries.value.length &&
    rows.value.every(
      ({ item, entry }) =>
        Number.isSafeInteger(entry.quantity) &&
        entry.quantity > 0 &&
        Number.isSafeInteger(item.onHand + entry.quantity),
    ),
);
function receive() {
  if (canReceive.value)
    emit(
      'receive',
      entries.value.map((entry) => ({ ...entry })),
    );
}
</script>
<template>
  <AppDialog title-id="stock-receive-title" size="wide" @close="emit('close')">
    <AppDialogPanel
      title-id="stock-receive-title"
      title="Receive stock"
      :description="`${items.length} items`"
      as="form"
      @submit.prevent="receive"
      @close="emit('close')"
    >
      <AppDataTable label="Items to receive" :columns="columns">
        <tr v-for="{ entry, item } in rows" :key="item.id">
          <th scope="row">
            {{ item.name
            }}<span class="receipt-unit">{{ item.sku }} · {{ item.unit }}</span>
          </th>
          <td class="numeric">{{ item.onHand }}</td>
          <td>
            <AppNumberStepper
              v-model="entry.quantity"
              :label="`Quantity for ${item.name}`"
              :min="1"
              :max="Number.MAX_SAFE_INTEGER - item.onHand"
            />
          </td>
          <td class="numeric">{{ item.onHand + entry.quantity }}</td>
        </tr>
      </AppDataTable>
      <template #footer
        ><AppButton @click="emit('close')">Cancel</AppButton
        ><AppButton type="submit" appearance="primary" :disabled="!canReceive"
          >Receive stock</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
<style scoped>
.receipt-unit {
  display: block;
  font-size: 12px;
  line-height: 16px;
  color: var(--ui-muted);
}
</style>
