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
  showNoShowModal,
  noShowTargetRes,
  noShowReason,
  noShowMutation,
  submitNoShow,
} = useManagementWorkspace();
</script>
<template>
  <AppDialog
    v-if="showNoShowModal"
    title-id="NoShowDialog-title"
    size="compact"
    @close="showNoShowModal = false"
  >
    <AppDialogPanel
      title-id="NoShowDialog-title"
      title="Mark no-show"
      :description="`${getCustomerName(noShowTargetRes?.customer)} · ${tableLabel(noShowTargetRes?.tableId)}`"
      as="form"
      @submit.prevent="submitNoShow"
      @close="showNoShowModal = false"
    >
      <AppField input-id="NoShowDialog-field-1" label="Reason">
        <input
          id="NoShowDialog-field-1"
          v-model="noShowReason"
          type="text"
          required
        />
      </AppField>
      <template #footer>
        <AppButton @click="showNoShowModal = false">Keep booking</AppButton>
        <AppButton
          type="submit"
          appearance="primary"
          intent="danger"
          :disabled="noShowMutation.isPending.value"
        >
          Mark no-show
        </AppButton>
      </template>
    </AppDialogPanel>
  </AppDialog>
</template>
