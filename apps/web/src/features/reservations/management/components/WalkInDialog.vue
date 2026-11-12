<script setup lang="ts">
import { computed } from 'vue';
import {
  AppButton,
  AppChoiceGroup,
  AppDialog,
  AppDialogPanel,
  AppField,
  AppFormGrid,
  AppMessageBar,
  AppNumberStepper,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  branchFloorTables,
  getTableStatus,
  showWalkInModal,
  walkInTableId,
  walkInPartySize,
  walkInDuration,
  walkInGuestName,
  walkInError,
  staffCreateMutation,
  handleStaffWalkInSubmit,
} = useManagementWorkspace();
const tables = computed(() =>
  branchFloorTables.map((table) => ({
    id: table.id,
    label: `Table ${table.number}`,
    description: `${table.seats} seats · ฿${table.rate}/hr\n${getTableStatus(table).state === 'OCCUPIED' ? 'In play' : getTableStatus(table).state === 'RESERVED' ? 'Reserved' : 'Free'}`,
  })),
);
function selectTable(id: string) {
  walkInTableId.value = id;
  const table = branchFloorTables.find((table) => table.id === id);
  if (table) walkInPartySize.value = table.seats;
}
</script>
<template>
  <AppDialog
    v-if="showWalkInModal"
    title-id="WalkInDialog-title"
    @close="showWalkInModal = false"
  >
    <AppDialogPanel
      title-id="WalkInDialog-title"
      title="Seat walk-in"
      as="form"
      @submit.prevent="handleStaffWalkInSubmit"
      @close="showWalkInModal = false"
    >
      <AppFormGrid>
        <AppChoiceGroup
          class="field-full-width"
          label="Table"
          :model-value="walkInTableId"
          :options="tables"
          @update:model-value="selectTable"
        />
        <AppField input-id="walkin-guests" label="Guests"
          ><AppNumberStepper
            v-model="walkInPartySize"
            input-id="walkin-guests"
            label="Guests"
            :min="1"
        /></AppField>
        <AppField input-id="WalkInDialog-field-1" label="Duration"
          ><select id="WalkInDialog-field-1" v-model.number="walkInDuration">
            <option :value="1">1 Hour</option>
            <option :value="2">2 Hours</option>
            <option :value="3">3 Hours</option>
            <option :value="4">4 Hours</option>
          </select></AppField
        >
        <AppField
          class="field-full-width"
          input-id="WalkInDialog-field-2"
          label="Guest name (optional)"
          ><input
            id="WalkInDialog-field-2"
            v-model="walkInGuestName"
            type="text"
            placeholder="Walk-in Guests"
        /></AppField>
        <AppMessageBar
          v-if="walkInError"
          tone="error"
          class="field-full-width"
          >{{ walkInError }}</AppMessageBar
        >
      </AppFormGrid>
      <template #footer
        ><AppButton @click="showWalkInModal = false">Cancel</AppButton
        ><AppButton
          type="submit"
          appearance="primary"
          :disabled="staffCreateMutation.isPending.value"
          >{{
            staffCreateMutation.isPending.value ? 'Seating…' : 'Seat walk-in'
          }}</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
