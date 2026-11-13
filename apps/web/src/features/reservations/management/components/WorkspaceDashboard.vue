<script setup lang="ts">
import { computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { getBranchMenu } from '@theparlor/api-client';
import {
  AppBadge,
  AppButton,
  AppDataTable,
  AppMetricCard,
  AppPage,
  AppPanel,
  AppTableActions,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
import OverviewFloorTable from './OverviewFloorTable.vue';
const {
  activeTab,
  selectedBranchId,
  selectedBranch,
  allReservations,
  branchFloorTables,
  occupiedTablesCount,
  freeTablesCount,
  upcomingReservations,
  activeFloorReservations,
  needsYouItems,
  getTableStatus,
  getElapsedMinutes,
  calculateCurrentPlayFee,
  formatTime,
  getCustomerName,
  openWalkInForTable,
  handleCheckIn,
  actionInProgress,
  openCancel,
  openCheckBill,
  openAddSnacks,
} = useManagementWorkspace();
const pantryQuery = useQuery({
  queryKey: ['branch-menu', selectedBranchId],
  queryFn: ({ signal }) =>
    getBranchMenu(selectedBranchId.value, undefined, signal),
  enabled: computed(() => !!selectedBranchId.value),
});
const pantryItems = computed(() => pantryQuery.data.value?.items ?? []);
const queueColumns = [
  { label: 'Table' },
  { label: 'Guest' },
  { label: 'Time' },
  { label: 'Table fee', numeric: true },
  { label: 'Actions', action: true },
];
function tableNumber(reservation: { id: string }): string {
  const table = branchFloorTables.find(
    (table) => getTableStatus(table).reservation?.id === reservation.id,
  );
  return table ? String(table.number) : '—';
}
</script>

<template>
  <AppPage
    title="Overview"
    :subtitle="selectedBranch?.name || 'Select a branch'"
    class="dashboard-workbench"
  >
    <template #actions
      ><AppButton
        appearance="primary"
        :disabled="!selectedBranch"
        @click="openWalkInForTable()"
        ><i class="fa-solid fa-plus" aria-hidden="true"></i>Seat
        walk-in</AppButton
      ></template
    >
    <section class="dashboard-summary" aria-label="Branch summary">
      <AppMetricCard
        as="button"
        label="Bookings"
        :value="allReservations.length"
        caption="All statuses"
        @click="activeTab = 'bookings'"
      />
      <AppMetricCard
        as="button"
        label="Tables in play"
        :value="occupiedTablesCount"
        :total="branchFloorTables.length"
        :caption="`${freeTablesCount} free · ${branchFloorTables.length - occupiedTablesCount - freeTablesCount} reserved`"
        @click="activeTab = 'floor'"
      />
      <AppMetricCard
        as="button"
        label="Awaiting arrival"
        :value="upcomingReservations.length"
        :caption="
          upcomingReservations[0]
            ? `Next ${formatTime(upcomingReservations[0].startsAt)}`
            : 'No arrivals'
        "
        @click="activeTab = 'bookings'"
      />
      <AppMetricCard
        as="button"
        label="Active sessions"
        :value="activeFloorReservations.length"
        caption="Checked in"
        @click="activeTab = 'sessions'"
      />
    </section>
    <div class="dashboard-columns">
      <AppPanel title="Floor status" title-id="overview-floor-title">
        <template #meta
          ><span class="dashboard-count"
            >{{ branchFloorTables.length }} tables</span
          ></template
        >
        <template #command
          ><AppButton appearance="subtle" @click="activeTab = 'floor'"
            >Open floor<i
              class="fa-solid fa-arrow-right"
              aria-hidden="true"
            ></i></AppButton
        ></template>
        <OverviewFloorTable />
      </AppPanel>
      <div class="dashboard-sidebar">
        <AppPanel title="Upcoming arrivals" title-id="overview-arrivals-title">
          <template #command
            ><span class="dashboard-count">{{
              upcomingReservations.length
            }}</span></template
          >
          <div v-if="!upcomingReservations.length" class="dashboard-empty">
            No upcoming arrivals
          </div>
          <div v-else>
            <article
              v-for="reservation in upcomingReservations.slice(0, 5)"
              :key="reservation.id"
              class="dashboard-arrival"
            >
              <time :datetime="reservation.startsAt">{{
                formatTime(reservation.startsAt)
              }}</time>
              <strong>{{ getCustomerName(reservation.customer) }}</strong>
              <p>
                Table {{ tableNumber(reservation) }}
                <span aria-hidden="true">·</span>
                {{ reservation.partySize }} guests
              </p>
              <div class="dashboard-actions">
                <AppButton
                  appearance="primary"
                  :disabled="actionInProgress === reservation.id"
                  @click="handleCheckIn(reservation)"
                  >Check in</AppButton
                >
                <AppButton intent="danger" @click="openCancel(reservation)"
                  >Cancel booking</AppButton
                >
              </div>
            </article>
          </div>
          <template #footer
            ><AppButton appearance="subtle" @click="activeTab = 'bookings'"
              >All bookings<i
                class="fa-solid fa-arrow-right"
                aria-hidden="true"
              ></i></AppButton
          ></template>
        </AppPanel>
        <AppPanel title="Pantry watch" title-id="overview-pantry-title">
          <p v-if="pantryQuery.isPending.value">Loading pantry stock…</p>
          <p v-else-if="pantryQuery.isError.value" role="alert">
            Pantry stock could not be loaded.
          </p>
          <dl v-else class="dashboard-stock-counts">
            <div>
              <dt>Available items</dt>
              <dd>
                {{ pantryItems.filter((row) => row.available > 0).length }}
              </dd>
            </div>
            <div>
              <dt>Unavailable items</dt>
              <dd class="stock-out">
                {{ pantryItems.filter((row) => row.available === 0).length }}
              </dd>
            </div>
          </dl>
          <template #footer
            ><AppButton appearance="subtle" @click="activeTab = 'inventory'"
              >Review inventory<i
                class="fa-solid fa-arrow-right"
                aria-hidden="true"
              ></i></AppButton
          ></template>
        </AppPanel>
      </div>
    </div>
    <aside aria-label="Service tasks">
      <AppPanel title="Service queue" title-id="overview-attention-title">
        <template #meta
          ><span class="dashboard-count">{{
            needsYouItems.length
          }}</span></template
        >
        <template #command
          ><AppButton appearance="subtle" @click="activeTab = 'sessions'"
            >View sessions<i
              class="fa-solid fa-arrow-right"
              aria-hidden="true"
            ></i></AppButton
        ></template>
        <div v-if="!needsYouItems.length" class="dashboard-empty">
          No service requests
        </div>
        <AppDataTable
          v-else
          label="Service queue table"
          caption="Tables requiring service, guests, time, and current table fees"
          :columns="queueColumns"
          table-class="dashboard-queue-table"
        >
          <tr v-for="item in needsYouItems" :key="item.id">
            <th scope="row">
              <strong class="queue-table-label"
                >Table {{ tableNumber(item.reservation) }}</strong
              ><AppBadge :tone="item.type === 'LATE' ? 'danger' : 'success'">{{
                item.type === 'LATE' ? 'Late arrival' : 'In play'
              }}</AppBadge>
            </th>
            <td>
              <span>{{ getCustomerName(item.reservation.customer) }}</span
              ><span class="queue-detail"
                >{{ item.reservation.partySize }} guests</span
              >
            </td>
            <td class="queue-time">
              <template v-if="item.type === 'IN_PLAY'"
                >{{ getElapsedMinutes(item.reservation) }} min</template
              ><template v-else
                >Due {{ formatTime(item.reservation.startsAt) }}</template
              >
            </td>
            <td class="numeric">
              <template v-if="item.type === 'IN_PLAY'"
                >฿{{ calculateCurrentPlayFee(item.reservation) }}</template
              ><span v-else class="queue-detail">—</span>
            </td>
            <AppTableActions>
              <template v-if="item.type === 'IN_PLAY'"
                ><AppButton
                  v-if="!item.reservation.live"
                  @click="openAddSnacks(item.reservation)"
                  >Refreshments</AppButton
                ><AppButton
                  appearance="primary"
                  @click="openCheckBill(item.reservation)"
                  ><i class="fa-solid fa-receipt" aria-hidden="true"></i>Check
                  bill</AppButton
                ></template
              >
              <AppButton
                v-else
                appearance="primary"
                :disabled="actionInProgress === item.reservation.id"
                @click="handleCheckIn(item.reservation)"
                >Check in</AppButton
              >
            </AppTableActions>
          </tr>
        </AppDataTable>
      </AppPanel>
    </aside>
  </AppPage>
</template>

<style scoped>
.dashboard-workbench {
  font-size: 14px;
  line-height: 20px;
}
.dashboard-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.dashboard-columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 24px;
}
.dashboard-sidebar {
  display: grid;
  min-width: 0;
  gap: 20px;
}
.dashboard-count {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}
.dashboard-arrival {
  padding: 4px 20px 20px;
}
.dashboard-arrival + .dashboard-arrival {
  padding-top: 20px;
  border-top: 1px solid var(--ui-divider);
}
.dashboard-arrival time {
  display: block;
  margin-bottom: 8px;
  font-size: 20px;
  line-height: 26px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.dashboard-arrival > strong {
  display: block;
  font-size: 14px;
  font-weight: 400;
}
.dashboard-arrival p {
  margin: 4px 0 16px;
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
}
.dashboard-arrival p span {
  margin-inline: 4px;
}
.dashboard-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.dashboard-stock-counts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  padding: 4px 20px 20px;
}
.dashboard-stock-counts > div + div {
  border-left: 1px solid var(--ui-divider);
  padding-left: 20px;
}
.dashboard-stock-counts dt {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
}
.dashboard-stock-counts dd {
  margin: 4px 0 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.stock-low {
  color: var(--ui-warning);
}
.stock-out {
  color: var(--ui-danger);
}
.dashboard-empty {
  padding: 20px;
  color: var(--ui-muted);
}
.queue-table-label {
  display: block;
  font-weight: 600;
}
.queue-detail {
  display: block;
  font-size: 12px;
  line-height: 16px;
  color: var(--ui-muted);
}
.queue-time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1200px) {
  .dashboard-columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .dashboard-sidebar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    gap: 24px;
  }
}
@media (max-width: 1100px) {
  .dashboard-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 800px) {
  .dashboard-summary {
    gap: 12px;
  }
  .dashboard-columns,
  .dashboard-sidebar {
    gap: 16px;
  }
}
@media (max-width: 600px) {
  .dashboard-sidebar {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
