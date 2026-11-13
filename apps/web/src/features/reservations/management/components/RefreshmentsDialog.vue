<script setup lang="ts">
import { computed } from 'vue';
import {
  AppBadge,
  AppButton,
  AppDialog,
  AppDialogPanel,
  AppNumberStepper,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  getCustomerName,
  branchFloorTables,
  getTableStatus,
  branchStockItems,
  staffSnackReservation,
  getStaffSnackQty,
  adjustStaffSnackQty,
  staffSnackSelectedItems,
  staffSnackSubtotal,
  submitStaffSnackOrder,
} = useManagementWorkspace();
const tableNumber = computed(
  () =>
    branchFloorTables.find(
      (table) =>
        getTableStatus(table).reservation?.id ===
        staffSnackReservation.value?.id,
    )?.number ?? '—',
);
</script>
<template>
  <AppDialog
    v-if="staffSnackReservation"
    title-id="RefreshmentsDialog-title"
    size="compact"
    @close="staffSnackReservation = null"
  >
    <AppDialogPanel
      title-id="RefreshmentsDialog-title"
      title="Add refreshments"
      :description="`Table ${tableNumber} · ${getCustomerName(staffSnackReservation.customer)}`"
      @close="staffSnackReservation = null"
    >
      <div class="refreshment-list">
        <div
          v-for="item in branchStockItems"
          :key="item.id"
          class="refreshment-row"
        >
          <div class="refreshment-info">
            <strong>{{ item.name }}</strong
            ><span
              >฿{{ item.price }} ·
              <AppBadge :tone="item.currentStock > 0 ? 'neutral' : 'danger'">{{
                item.currentStock > 0
                  ? `${item.currentStock} in stock`
                  : 'Out of stock'
              }}</AppBadge></span
            >
          </div>
          <AppNumberStepper
            :model-value="getStaffSnackQty(item.id)"
            :label="`Quantity of ${item.name}`"
            :max="item.currentStock"
            :disabled="item.currentStock <= 0"
            @update:model-value="
              adjustStaffSnackQty(item, $event - getStaffSnackQty(item.id))
            "
          />
        </div>
      </div>
      <template #footer
        ><strong class="refreshment-total"
          >Total ฿{{ staffSnackSubtotal }}</strong
        ><AppButton @click="staffSnackReservation = null">Cancel</AppButton
        ><AppButton
          appearance="primary"
          :disabled="staffSnackSelectedItems.length === 0"
          @click="submitStaffSnackOrder"
          >Add to table</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
<style scoped>
.refreshment-list {
  display: grid;
  gap: 16px;
}
.refreshment-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.refreshment-info {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.refreshment-info strong {
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
}
.refreshment-info > span {
  font-size: 12px;
  line-height: 18px;
  color: var(--ui-muted);
}
.refreshment-total {
  margin-right: auto;
  font-size: 14px;
  font-weight: 600;
}
</style>
