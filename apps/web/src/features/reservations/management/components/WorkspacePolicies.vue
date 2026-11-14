<script setup lang="ts">
import {
  AppBadge,
  AppDataTable,
  AppEmptyState,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
import {
  PRE_ORDER_DISCOUNT_PERCENT,
  PRE_ORDER_WINDOW_DAYS,
} from '../../data/booking';
import { useManagementWorkspace } from '../context';

const { branchFloorTables } = useManagementWorkspace();

const ruleColumns = [
  { label: 'Policy' },
  { label: 'Setting' },
  { label: 'How it applies' },
];
const rateColumns = [
  { label: 'Table label' },
  { label: 'Seats', numeric: true },
  { label: 'Hourly rate', numeric: true },
];
</script>

<template>
  <AppPage title="Venue policies">
    <template #actions><AppBadge>Read-only</AppBadge></template>
    <AppPanel title="Reservation & billing rules" title-id="reservation-rules">
      <AppDataTable
        class="workspace-records"
        label="Venue policy rules"
        :columns="ruleColumns"
      >
        <tr>
          <th scope="row">Table time</th>
          <td>Every started hour, one-hour minimum</td>
          <td>
            Billed from check-in to checkout at the rate locked when booked.
          </td>
        </tr>
        <tr>
          <th scope="row">Food pre-orders</th>
          <td>{{ PRE_ORDER_DISCOUNT_PERCENT }}% off</td>
          <td>
            Visits within {{ PRE_ORDER_WINDOW_DAYS }} days; card charged at
            check-in.
          </td>
        </tr>
        <tr>
          <th scope="row">No-shows</th>
          <td>Marked by staff</td>
          <td>
            Releases the table, held games and pre-ordered food; the card hold
            is voided.
          </td>
        </tr>
      </AppDataTable>
    </AppPanel>
    <AppPanel title="Table hourly rates" title-id="table-rates">
      <template #command>
        <span class="workspace-count">Per table</span>
      </template>
      <AppEmptyState
        v-if="!branchFloorTables.length"
        title="No tables set up for this branch."
        compact
      />
      <AppDataTable
        v-else
        class="workspace-records"
        label="Table hourly rates"
        :columns="rateColumns"
      >
        <tr v-for="table in branchFloorTables" :key="table.id">
          <th scope="row">{{ table.label || `Table ${table.number}` }}</th>
          <td class="numeric">{{ table.seats }}</td>
          <td class="numeric">฿{{ table.rate }}</td>
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
