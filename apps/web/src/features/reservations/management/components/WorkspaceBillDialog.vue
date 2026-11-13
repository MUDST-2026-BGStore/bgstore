<script setup lang="ts">
import TableBillModal from '../../components/TableBillModal.vue';
import { AppDialog } from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const { queryClient, staffBillReservation } = useManagementWorkspace();
</script>

<template>
  <AppDialog
    v-if="staffBillReservation"
    class="management-billing"
    :title-id="`table-bill-title-${staffBillReservation.id}`"
    @close="staffBillReservation = null"
  >
    <TableBillModal
      :reservation="staffBillReservation"
      :is-staff="true"
      @close="staffBillReservation = null"
      @settled="
        staffBillReservation = null;
        queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
      "
    />
  </AppDialog>
</template>

<style scoped>
.management-billing :deep(.modal-overlay) {
  position: static;
  inset: auto;
  display: block;
  padding: 0;
  background: transparent;
  backdrop-filter: none;
}
.management-billing :deep(.bill-dialog) {
  max-width: none;
  max-height: calc(100dvh - 32px);
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
  box-shadow: 0 16px 64px rgb(16 26 20 / 24%);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  overflow-y: auto;
}
.management-billing :deep(.dialog-header) {
  padding: 20px 24px;
  background: var(--ui-surface);
  border-bottom: 1px solid var(--ui-divider);
}
.management-billing :deep(.dialog-title) {
  font-size: 18px;
  font-weight: 600;
  color: var(--ui-text);
}
.management-billing :deep(.dialog-sub),
.management-billing :deep(.table-ref) {
  color: var(--ui-muted);
  font-size: 12px;
  font-weight: 400;
}
.management-billing :deep(.pulse-dot),
.management-billing :deep(.gold-bullet) {
  display: none;
}
.management-billing :deep(.live-pill),
.management-billing :deep(.client-badge) {
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-neutral-subtle);
  color: var(--ui-text);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
}
.management-billing :deep(.bill-scroll) {
  padding: 20px 24px;
  gap: 20px;
}
.management-billing :deep(.bill-section) {
  padding: 0 0 20px;
  border: 0;
  border-bottom: 1px solid var(--ui-divider);
  border-radius: 0;
  background: transparent;
}
.management-billing :deep(.bill-section:last-child) {
  padding-bottom: 0;
  border-bottom: 0;
}
.management-billing :deep(.section-title) {
  font-size: 14px;
  font-weight: 600;
}
.management-billing :deep(.section-cost) {
  font-size: 14px;
  font-weight: 600;
}
.management-billing :deep(.meta-tag),
.management-billing :deep(.tab-pill),
.management-billing :deep(.inplay-order-card) {
  border-radius: var(--ui-radius);
}
.management-billing :deep(.bill-summary-footer) {
  padding: 16px 24px;
  border-top: 1px solid var(--ui-divider);
  background: var(--ui-surface-subtle);
}
.management-billing :deep(.summary-divider) {
  background: var(--ui-divider);
}
.management-billing :deep(.due-label) {
  font-size: 14px;
  font-weight: 600;
}
.management-billing :deep(.due-amount) {
  font-size: 24px;
  font-weight: 600;
}
.management-billing :deep(.pm-radio-btn) {
  min-height: 34px;
  border: 1px solid var(--ui-stroke-strong);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font-size: 13px;
  font-weight: 500;
}
.management-billing :deep(.pm-radio-btn.active) {
  border-color: var(--ui-brand);
  background: var(--ui-selected);
  color: var(--ui-brand);
}
.management-billing :deep(.settle-btn) {
  min-height: 36px;
  border: 1px solid var(--ui-brand);
  border-radius: var(--ui-radius);
  background: var(--ui-brand);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  box-shadow: none;
}
.management-billing :deep(.settle-btn:hover) {
  background: var(--ui-brand-hover);
  box-shadow: none;
}
@media (max-height: 700px) {
  .management-billing :deep(.bill-dialog) {
    overflow-y: auto;
  }
  .management-billing :deep(.dialog-content) {
    flex: none;
    overflow: visible;
  }
  .management-billing :deep(.bill-scroll) {
    flex: none;
    overflow: visible;
  }
}
@media (max-width: 520px) {
  .management-billing :deep(.dialog-header),
  .management-billing :deep(.bill-scroll),
  .management-billing :deep(.bill-summary-footer) {
    padding: 16px;
  }
}
</style>
