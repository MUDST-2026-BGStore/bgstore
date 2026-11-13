<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  AppButton,
  AppDescriptionList,
  AppDialog,
  AppDialogPanel,
  AppField,
  AppNumberStepper,
} from '../../../../shared/ui';
import type { SaleStockItem } from '../../components/inventory/sale-stock';
const props = defineProps<{ item: SaleStockItem }>();
const emit = defineEmits<{
  close: [];
  adjust: [id: string, quantity: number];
}>();
const quantity = ref(props.item.onHand);
const canAdjust = computed(
  () =>
    Number.isSafeInteger(quantity.value) &&
    quantity.value >= props.item.reserved &&
    quantity.value >= 0,
);
function save() {
  if (canAdjust.value) emit('adjust', props.item.id, quantity.value);
}
</script>
<template>
  <AppDialog title-id="stock-adjust-title" @close="emit('close')">
    <AppDialogPanel
      title-id="stock-adjust-title"
      title="Adjust quantity"
      :description="item.name"
      as="form"
      @submit.prevent="save"
      @close="emit('close')"
    >
      <AppDescriptionList
        :items="[
          { label: 'SKU', value: item.sku },
          { label: 'On hand', value: `${item.onHand} ${item.unit}` },
          { label: 'Reserved', value: `${item.reserved} ${item.unit}` },
        ]"
      />
      <AppField label="Counted quantity" input-id="stock-counted-quantity">
        <AppNumberStepper
          v-model="quantity"
          input-id="stock-counted-quantity"
          label="Counted quantity"
          :min="item.reserved"
        />
      </AppField>
      <p class="adjustment-help">
        On hand must cover the {{ item.reserved }} {{ item.unit }} reserved for
        orders.
      </p>
      <template #footer
        ><AppButton @click="emit('close')">Cancel</AppButton
        ><AppButton type="submit" appearance="primary" :disabled="!canAdjust"
          >Save adjustment</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
<style scoped>
.adjustment-help {
  color: var(--ui-muted);
  font-size: 12px;
  margin: 8px 0 0;
}
</style>
