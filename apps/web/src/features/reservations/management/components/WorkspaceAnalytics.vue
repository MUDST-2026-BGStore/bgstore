<script setup lang="ts">
// TODO(analytics-api): There is no reporting endpoint. Every figure and chart here is
// computed from local sample data.
import { computed, ref } from 'vue';
import {
  AppButton,
  AppDataTable,
  AppEmptyState,
  AppMetricCard,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  branchFloorTables,
  occupiedTablesCount,
  activeFloorReservations,
  calculateCurrentPlayFee,
  getElapsedMinutes,
  getCustomerName,
  activeTab,
  tableLabel,
} = useManagementWorkspace();
const columns = [
  { label: 'Table' },
  { label: 'Guest' },
  { label: 'Played' },
  { label: 'Hourly rate', numeric: true },
  { label: 'Est. table fee', numeric: true },
];
const totalTableFeesToday = computed(() =>
  activeFloorReservations.value.reduce(
    (sum, reservation) => sum + calculateCurrentPlayFee(reservation),
    0,
  ),
);
const todayFoodDrinkRevenue = ref(2450);
const todayTotalGross = computed(
  () => totalTableFeesToday.value + todayFoodDrinkRevenue.value,
);
</script>
<template>
  <AppPage title="Revenue & analytics">
    <template #actions
      ><AppButton @click="activeTab = 'sessions'"
        >View live sessions<i
          class="fa-solid fa-arrow-right"
          aria-hidden="true"
        ></i></AppButton
    ></template>
    <AppPanel aria-label="Revenue summary"
      ><div class="analytics-summary">
        <AppMetricCard
          appearance="plain"
          label="Estimated total"
          :value="`฿${todayTotalGross}`"
          caption="Table time + refreshments"
        />
        <AppMetricCard
          appearance="plain"
          label="Open table fees"
          :value="`฿${totalTableFeesToday}`"
          caption="Actual elapsed playtime"
        />
        <AppMetricCard
          appearance="plain"
          label="Refreshments"
          :value="`฿${todayFoodDrinkRevenue}`"
        />
        <AppMetricCard
          appearance="plain"
          label="Current utilization"
          :value="`${Math.round((occupiedTablesCount / (branchFloorTables.length || 1)) * 100)}%`"
          :caption="`${occupiedTablesCount} of ${branchFloorTables.length} tables in play`"
        /></div
    ></AppPanel>
    <AppPanel title="Open-session fee breakdown" title-id="session-fees-title">
      <template #command
        ><span class="workspace-results"
          >{{ activeFloorReservations.length }} sessions</span
        ></template
      >
      <AppDataTable
        class="workspace-records"
        label="Session fee breakdown"
        :columns="columns"
      >
        <tr
          v-for="reservation in activeFloorReservations"
          :key="reservation.id"
        >
          <td>{{ tableLabel(reservation.tableId) }}</td>
          <td>{{ getCustomerName(reservation.customer) }}</td>
          <td>{{ getElapsedMinutes(reservation) }} min</td>
          <td class="numeric">฿{{ reservation.hourlyRate ?? 200 }}</td>
          <td class="numeric">฿{{ calculateCurrentPlayFee(reservation) }}</td>
        </tr>
        <tr v-if="!activeFloorReservations.length">
          <td colspan="5">
            <AppEmptyState compact title="No active sessions." />
          </td>
        </tr>
        <template #footer
          ><tr>
            <th scope="row" colspan="4">Total open table fees</th>
            <td class="numeric">฿{{ totalTableFeesToday }}</td>
          </tr></template
        >
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
<style scoped>
.analytics-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
@media (max-width: 1000px) {
  .analytics-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
