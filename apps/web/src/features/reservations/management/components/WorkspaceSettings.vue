<script setup lang="ts">
import {
  AppBadge,
  AppButton,
  AppField,
  AppFormActions,
  AppFormGrid,
  AppFormSection,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const { venueSettings } = useManagementWorkspace();
</script>
<template>
  <AppPage title="Venue settings">
    <template #actions><AppBadge>Administrator</AppBadge></template>
    <AppPanel aria-label="Venue configuration">
      <AppFormSection title="Branch identity" title-id="venue-identity"
        ><AppFormGrid :columns="1"
          ><AppField label="Branch name" input-id="venue-name"
            ><input
              id="venue-name"
              v-model="venueSettings.branchName"
              type="text" /></AppField
          ><AppField label="Physical address" input-id="venue-address"
            ><input
              id="venue-address"
              v-model="venueSettings.address"
              type="text" /></AppField></AppFormGrid
      ></AppFormSection>
      <AppFormSection title="Operating hours" title-id="venue-hours"
        ><AppFormGrid
          ><AppField label="Opening time" input-id="venue-open"
            ><input
              id="venue-open"
              v-model="venueSettings.openTime"
              type="time" /></AppField
          ><AppField label="Closing time" input-id="venue-close"
            ><input
              id="venue-close"
              v-model="venueSettings.closeTime"
              type="time" /></AppField></AppFormGrid
      ></AppFormSection>
      <!-- Preserve editing without suggesting that persistence has been implemented. -->
      <AppFormActions
        ><AppButton appearance="primary" disabled
          >Save changes</AppButton
        ></AppFormActions
      >
    </AppPanel>
  </AppPage>
</template>
