<script setup lang="ts">
import { computed } from 'vue';
import {
  AppBadge,
  AppButton,
  AppDataTable,
  AppTableActions,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';

const {
  branchFloorTables,
  getTableStatus,
  getTableAssignedGames,
  getCustomerName,
  getElapsedMinutes,
  calculateCurrentPlayFee,
  formatTime,
  openWalkInForTable,
  openCheckBill,
  handleCheckIn,
  actionInProgress,
} = useManagementWorkspace();
const columns = [
  { label: 'Table' },
  { label: 'Guest / game' },
  { label: 'Time' },
  { label: 'Table fee', numeric: true },
  { label: 'Actions', action: true },
];
const rows = computed(() =>
  branchFloorTables.map((table) => ({
    table,
    ...getTableStatus(table),
    games: getTableAssignedGames(table.id),
  })),
);
</script>

<template>
  <AppDataTable
    label="Floor status table"
    caption="Current table status, guests, and table fees"
    :columns="columns"
    table-class="overview-floor-table"
  >
    <tr v-for="row in rows" :key="row.table.id">
      <th scope="row">
        <strong class="table-number">{{ row.table.number }}</strong
        ><AppBadge
          :tone="
            row.state === 'OCCUPIED'
              ? 'success'
              : row.state === 'RESERVED'
                ? 'warning'
                : 'neutral'
          "
          >{{
            row.state === 'OCCUPIED'
              ? 'In play'
              : row.state === 'RESERVED'
                ? 'Reserved'
                : 'Free'
          }}</AppBadge
        >
      </th>
      <td>
        <template v-if="row.reservation"
          ><span class="guest-name">{{
            getCustomerName(row.reservation.customer)
          }}</span
          ><span class="row-detail"
            >{{ row.reservation.partySize }} guests<template
              v-if="row.games.length"
            >
              · {{ row.games.join(', ') }}</template
            ></span
          ></template
        >
        <span v-else class="row-detail"
          >{{ row.table.seats }} seats · ฿{{ row.table.rate }}/hr</span
        >
      </td>
      <td>
        <span v-if="row.state === 'OCCUPIED'"
          >{{ getElapsedMinutes(row.reservation) }} min</span
        ><span v-else-if="row.reservation" class="row-detail"
          >Due {{ formatTime(row.reservation.startsAt) }}</span
        ><span v-else class="row-detail">—</span>
      </td>
      <td class="numeric">
        <template v-if="row.state === 'OCCUPIED'"
          >฿{{ calculateCurrentPlayFee(row.reservation) }}</template
        ><span v-else class="row-detail">—</span>
      </td>
      <AppTableActions>
        <AppButton
          v-if="row.state === 'OCCUPIED'"
          appearance="primary"
          :aria-label="`Check bill for Table ${row.table.number}`"
          @click="openCheckBill(row.reservation)"
          ><i class="fa-solid fa-receipt" aria-hidden="true"></i>Check
          bill</AppButton
        >
        <AppButton
          v-else-if="row.state === 'RESERVED'"
          appearance="primary"
          :disabled="actionInProgress === row.reservation?.id"
          :aria-label="`Check in at Table ${row.table.number}`"
          @click="handleCheckIn(row.reservation)"
          ><i class="fa-solid fa-check" aria-hidden="true"></i>Check
          in</AppButton
        >
        <AppButton
          v-else
          :aria-label="`Seat walk-in at Table ${row.table.number}`"
          @click="openWalkInForTable(row.table)"
          ><i class="fa-solid fa-plus" aria-hidden="true"></i>Seat
          walk-in</AppButton
        >
      </AppTableActions>
    </tr>
  </AppDataTable>
</template>

<style scoped>
.table-number {
  display: block;
  margin-bottom: 2px;
  font-size: 14px;
  font-weight: 600;
}
.guest-name {
  display: block;
}
.row-detail {
  display: block;
  font-size: 12px;
  line-height: 16px;
  color: var(--ui-muted);
  white-space: nowrap;
}
</style>
