<script setup lang="ts">
import { computed } from 'vue';
import {
  AppButton,
  AppDialog,
  AppDialogPanel,
  AppField,
  AppFormGrid,
  AppNumberStepper,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const { showRestockModal, restockTargetItem, restockBatchQty, submitRestock } =
  useManagementWorkspace();
const canReceive = computed(
  () =>
    Number.isSafeInteger(restockBatchQty.value) &&
    restockBatchQty.value > 0 &&
    Number.isSafeInteger(
      (restockTargetItem.value?.currentStock ?? 0) + restockBatchQty.value,
    ),
);
function receive() {
  if (canReceive.value) submitRestock();
}
</script>
<template>
  <AppDialog
    v-if="showRestockModal && restockTargetItem"
    title-id="RestockDialog-title"
    size="compact"
    @close="showRestockModal = false"
  >
    <AppDialogPanel
      title-id="RestockDialog-title"
      title="Receive stock"
      :description="restockTargetItem.name"
      as="form"
      @submit.prevent="receive"
      @close="showRestockModal = false"
    >
      <AppFormGrid :columns="1">
        <dl class="stock-preview">
          <dt>Current stock</dt>
          <dd>
            {{ restockTargetItem.currentStock }} {{ restockTargetItem.unit }}
          </dd>
        </dl>
        <AppField
          input-id="restock-quantity"
          :label="`Quantity received (${restockTargetItem.unit})`"
          ><div class="quantity-controls">
            <AppNumberStepper
              v-model="restockBatchQty"
              input-id="restock-quantity"
              label="Quantity received"
              :min="1"
              :max="Number.MAX_SAFE_INTEGER - restockTargetItem.currentStock"
              :step="6"
            /><AppButton
              @click="
                restockBatchQty = Math.min(
                  Number.MAX_SAFE_INTEGER - restockTargetItem.currentStock,
                  restockBatchQty + 12,
                )
              "
              >+12</AppButton
            >
          </div></AppField
        >
        <dl class="stock-preview">
          <dt>New stock</dt>
          <dd>
            {{ restockTargetItem.currentStock + restockBatchQty }}
            {{ restockTargetItem.unit }}
          </dd>
        </dl>
      </AppFormGrid>
      <template #footer
        ><AppButton @click="showRestockModal = false">Cancel</AppButton
        ><AppButton type="submit" appearance="primary" :disabled="!canReceive"
          >Receive stock</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
<style scoped>
.stock-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-surface-subtle);
}
.stock-preview dt {
  color: var(--ui-muted);
  font-size: 13px;
}
.stock-preview dd {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.quantity-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
