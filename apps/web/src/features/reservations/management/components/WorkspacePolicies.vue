<script setup lang="ts">
// TODO(policies-api): Business policies have no backend. Values here are local
// sample settings and change nothing on the server.
import { ref } from 'vue';
import {
  AppBadge,
  AppDataTable,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
const ruleColumns = [
  { label: 'Policy' },
  { label: 'Setting' },
  { label: 'How it applies' },
];
const rateColumns = [
  { label: 'Table capacity' },
  { label: 'Hourly rate', numeric: true },
];
const venuePolicies = ref({
  noShowReleaseMinutes: 15,
  autoReleaseEnabled: true,
  playedTimeRecomputation: true,
  baseHourlyRate2Seats: 150,
  baseHourlyRate4Seats: 200,
  baseHourlyRate6Seats: 300,
  depositRequired: true,
  depositAmount: 100,
});
</script>

<template>
  <AppPage title="Venue policies"
    ><template #actions><AppBadge>Read-only</AppBadge></template>
    <AppPanel title="Reservation & billing rules" title-id="reservation-rules">
      <AppDataTable
        class="workspace-records"
        label="Venue policy rules"
        :columns="ruleColumns"
      >
        <tr>
          <th scope="row">No-show release</th>
          <td>{{ venuePolicies.noShowReleaseMinutes }} minutes</td>
          <td>Release unclaimed tables after the grace period.</td>
        </tr>
        <tr>
          <th scope="row">Played-time calculation</th>
          <td>From check-in</td>
          <td>Prorate fees by elapsed playtime.</td>
        </tr>
      </AppDataTable>
    </AppPanel>
    <AppPanel title="Standard hourly rates" title-id="table-rates"
      ><template #command
        ><span class="workspace-count">Per table</span></template
      >
      <AppDataTable
        class="workspace-records"
        label="Table hourly rates"
        :columns="rateColumns"
      >
        <tr>
          <th scope="row">2 seats</th>
          <td class="numeric">฿{{ venuePolicies.baseHourlyRate2Seats }}</td>
        </tr>
        <tr>
          <th scope="row">4 seats</th>
          <td class="numeric">฿{{ venuePolicies.baseHourlyRate4Seats }}</td>
        </tr>
        <tr>
          <th scope="row">6 seats</th>
          <td class="numeric">฿{{ venuePolicies.baseHourlyRate6Seats }}</td>
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
