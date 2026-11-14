<script setup lang="ts">
import {
  AppButton,
  AppDialog,
  AppDialogPanel,
  AppField,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  getCustomerName,
  tableLabel,
  showCancelModal,
  cancelTargetRes,
  cancelReason,
  cancelMutation,
  submitCancel,
} = useManagementWorkspace();
</script>
<template>
  <AppDialog
    v-if="showCancelModal"
    title-id="CancelBookingDialog-title"
    size="compact"
    @close="showCancelModal = false"
  >
    <AppDialogPanel
      title-id="CancelBookingDialog-title"
      title="Cancel booking"
      :description="`${getCustomerName(cancelTargetRes?.customer)} · ${tableLabel(cancelTargetRes?.tableId)}`"
      as="form"
      @submit.prevent="submitCancel"
      @close="showCancelModal = false"
    >
      <AppField input-id="CancelBookingDialog-field-1" label="Reason"
        ><input
          id="CancelBookingDialog-field-1"
          v-model="cancelReason"
          type="text"
          required
      /></AppField>
      <template #footer
        ><AppButton @click="showCancelModal = false">Keep booking</AppButton
        ><AppButton
          type="submit"
          appearance="primary"
          intent="danger"
          :disabled="cancelMutation.isPending.value"
          >Cancel booking</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
