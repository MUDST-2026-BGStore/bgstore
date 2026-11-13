<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  AppButton,
  AppDataTable,
  AppEmptyState,
  AppPage,
  AppPanel,
  AppTableToolbar,
  AppSearchField,
  AppTableActions,
  AppTableSelect,
  useTableSelection,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  activeFloorReservations,
  branchFloorTables,
  getTableStatus,
  formatTime,
  getElapsedMinutes,
  calculateCurrentPlayFee,
  getCustomerName,
  openWalkInForTable,
  openCheckBill,
  getTableAssignedGames,
  openAssignGameForTable,
  unassignGameFromTable,
  openAddSnacks,
  selectedBranchId,
} = useManagementWorkspace();
const columns = [
  { label: 'Table' },
  { label: 'Guest' },
  { label: 'Game' },
  { label: 'Checked in' },
  { label: 'Played' },
  { label: 'Hourly rate', numeric: true },
  { label: 'Table fee', numeric: true },
  { label: 'Actions', action: true },
];
function sessionTableId(reservation: { id: string; tableId: string }): string {
  return (
    branchFloorTables.find(
      (table) => getTableStatus(table).reservation?.id === reservation.id,
    )?.id ?? reservation.tableId
  );
}
const search = ref('');
const visibleSessions = computed(() =>
  activeFloorReservations.value.filter((res) =>
    `${res.tableId} ${getCustomerName(res.customer)}`
      .toLowerCase()
      .includes(search.value.trim().toLowerCase()),
  ),
);
const table = ref<InstanceType<typeof AppDataTable>>();
const selection = useTableSelection(
  computed(() => visibleSessions.value.map((res) => res.id)),
);
const returnableSessions = computed(() =>
  visibleSessions.value.filter(
    (res) =>
      selection.selectedIds.value.has(res.id) &&
      getTableAssignedGames(sessionTableId(res)).length,
  ),
);
function returnSelectedGames() {
  for (const res of returnableSessions.value) {
    const tableId = sessionTableId(res);
    for (const game of getTableAssignedGames(tableId))
      unassignGameFromTable(tableId, game);
  }
  selection.clear();
  table.value?.focus();
}
watch([search, selectedBranchId], selection.clear);
</script>

<template>
  <AppPage title="Live sessions"
    ><template #actions
      ><AppButton appearance="primary" @click="openWalkInForTable()"
        ><i class="fa-solid fa-plus" aria-hidden="true"></i>Seat
        walk-in</AppButton
      ></template
    >
    <AppPanel aria-label="Active play sessions">
      <AppTableToolbar
        :result-label="`${visibleSessions.length} of ${activeFloorReservations.length} active sessions`"
        :selected-count="selection.count.value"
        @clear-selection="selection.clear"
      >
        <AppSearchField
          v-model="search"
          label="Search live sessions"
          placeholder="Guest or table"
        /><template #selection
          ><AppButton
            appearance="primary"
            :disabled="!returnableSessions.length"
            @click="returnSelectedGames"
            >Return to shelf ({{ returnableSessions.length }})</AppButton
          ></template
        >
      </AppTableToolbar>
      <AppEmptyState
        v-if="!visibleSessions.length"
        :title="
          search ? 'No sessions match your search' : 'No active play sessions'
        "
        ><AppButton v-if="search" @click="search = ''">Clear search</AppButton
        ><AppButton v-else appearance="primary" @click="openWalkInForTable()"
          >Seat walk-in</AppButton
        ></AppEmptyState
      >
      <AppDataTable
        v-else
        ref="table"
        class="workspace-records"
        label="Live sessions table"
        caption="Active table sessions and billing actions"
        :columns="columns"
      >
        <template #selection-header
          ><AppTableSelect
            header
            label="Select all visible sessions"
            :model-value="selection.allSelected.value"
            :indeterminate="selection.indeterminate.value"
            @update:model-value="selection.toggleAll"
        /></template>
        <tr
          v-for="res in visibleSessions"
          :key="res.id"
          :aria-selected="selection.selectedIds.value.has(res.id)"
        >
          <AppTableSelect
            :label="`Select session at Table ${sessionTableId(res)}`"
            :model-value="selection.selectedIds.value.has(res.id)"
            @update:model-value="selection.toggle(res.id, $event)"
          />
          <td>
            <strong class="record-primary">{{ sessionTableId(res) }}</strong>
          </td>
          <td>
            <strong class="record-primary">{{
              getCustomerName(res.customer)
            }}</strong
            ><span class="record-secondary">{{ res.partySize }} guests</span>
          </td>
          <td>
            <div
              v-if="getTableAssignedGames(sessionTableId(res)).length"
              class="session-chips-wrap"
            >
              <span class="record-primary">{{
                getTableAssignedGames(sessionTableId(res))[0]
              }}</span
              ><AppButton
                appearance="subtle"
                :aria-label="`Return ${getTableAssignedGames(sessionTableId(res))[0]} from Table ${sessionTableId(res)}`"
                @click="
                  unassignGameFromTable(
                    sessionTableId(res),
                    getTableAssignedGames(sessionTableId(res))[0],
                  )
                "
                >Return to shelf</AppButton
              >
            </div>
            <AppButton
              v-else
              :aria-label="`Assign game to Table ${sessionTableId(res)}`"
              @click="openAssignGameForTable(sessionTableId(res))"
              >Assign game</AppButton
            >
          </td>
          <td>{{ formatTime(res.checkedInAt || res.startsAt) }}</td>
          <td>{{ getElapsedMinutes(res) }} min</td>
          <td class="numeric">฿{{ res.hourlyRate || 200 }}</td>
          <td class="numeric">
            <strong class="record-primary"
              >฿{{ calculateCurrentPlayFee(res) }}</strong
            >
          </td>
          <AppTableActions
            ><AppButton
              :aria-label="`Add refreshments to Table ${sessionTableId(res)}`"
              @click="openAddSnacks(res)"
              >Refreshments</AppButton
            ><AppButton
              appearance="primary"
              :aria-label="`Check bill for Table ${sessionTableId(res)}`"
              @click="openCheckBill(res)"
              ><i class="fa-solid fa-receipt" aria-hidden="true"></i>Check
              bill</AppButton
            ></AppTableActions
          >
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
