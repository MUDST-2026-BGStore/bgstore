<script setup lang="ts">
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
const { walkIn } = useManagementWorkspace();
const {
  open,
  tableId,
  partySize,
  durationHours,
  guestName,
  error,
  submitting,
  availability,
  tableOptions,
  blockedReason,
  canSubmit,
  selectTable,
  submit,
} = walkIn;
</script>
<template>
  <AppDialog v-if="open" title-id="WalkInDialog-title" @close="open = false">
    <AppDialogPanel
      title-id="WalkInDialog-title"
      title="Seat walk-in"
      as="form"
      @submit.prevent="submit"
      @close="open = false"
    >
      <AppFormGrid>
        <p
          v-if="availability.isPending.value && !tableOptions.length"
          class="field-full-width"
          role="status"
        >
          Checking tables…
        </p>
        <AppChoiceGroup
          v-else
          class="field-full-width"
          label="Table"
          :model-value="tableId"
          :options="tableOptions"
          @update:model-value="selectTable"
        />
        <AppField input-id="walkin-guests" label="Guests"
          ><AppNumberStepper
            v-model="partySize"
            input-id="walkin-guests"
            label="Guests"
            :min="1"
        /></AppField>
        <AppField input-id="WalkInDialog-field-1" label="Duration"
          ><select id="WalkInDialog-field-1" v-model.number="durationHours">
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
            v-model="guestName"
            type="text"
            maxlength="200"
            placeholder="Walk-in guests"
        /></AppField>
        <AppMessageBar
          v-if="error || blockedReason"
          tone="error"
          class="field-full-width"
          >{{ error || blockedReason }}</AppMessageBar
        >
      </AppFormGrid>
      <template #footer
        ><AppButton @click="open = false">Cancel</AppButton
        ><AppButton type="submit" appearance="primary" :disabled="!canSubmit">{{
          submitting ? 'Seating…' : 'Seat walk-in'
        }}</AppButton></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
