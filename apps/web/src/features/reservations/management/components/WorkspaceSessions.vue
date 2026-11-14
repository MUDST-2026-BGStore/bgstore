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
  tableLabel,
  formatTime,
  getElapsedMinutes,
  getSessionTiming,
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
  { label: 'Scheduled end' },
  { label: 'Played' },
  { label: 'Remaining' },
  { label: 'Hourly rate', numeric: true },
  { label: 'Est. table fee', numeric: true },
  { label: 'Actions', action: true },
];
const search = ref('');
const visibleSessions = computed(() =>
  activeFloorReservations.value.filter((res) =>
    `${tableLabel(res.tableId)} ${getCustomerName(res.customer)}`
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
      getTableAssignedGames(res.tableId).length,
  ),
);
function returnSelectedGames() {
  for (const res of returnableSessions.value) {
    const tableId = res.tableId;
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
            :label="`Select session at ${tableLabel(res.tableId)}`"
            :model-value="selection.selectedIds.value.has(res.id)"
            @update:model-value="selection.toggle(res.id, $event)"
          />
          <td>
            <strong class="record-primary">{{
              tableLabel(res.tableId)
            }}</strong>
          </td>
          <td>
            <strong class="record-primary">{{
              getCustomerName(res.customer)
            }}</strong
            ><span class="record-secondary">{{ res.partySize }} guests</span>
          </td>
          <td>
            <div
              v-if="getTableAssignedGames(res.tableId).length"
              class="session-chips-wrap"
            >
              <span class="record-primary">{{
                getTableAssignedGames(res.tableId)[0]
              }}</span
              ><AppButton
                appearance="subtle"
                :aria-label="`Return ${getTableAssignedGames(res.tableId)[0]} from ${tableLabel(res.tableId)}`"
                @click="
                  unassignGameFromTable(
                    res.tableId,
                    getTableAssignedGames(res.tableId)[0],
                  )
                "
                >Return to shelf</AppButton
              >
            </div>
            <AppButton
              v-else
              :aria-label="`Assign game to ${tableLabel(res.tableId)}`"
              @click="openAssignGameForTable(res.tableId)"
              >Assign game</AppButton
            >
          </td>
          <td>{{ formatTime(res.checkedInAt || res.startsAt) }}</td>
          <td>{{ formatTime(res.endsAt) }}</td>
          <td>{{ getElapsedMinutes(res) }} min</td>
          <td>
            <template v-if="getSessionTiming(res)">
              <span
                v-if="getSessionTiming(res)?.expiringSoon"
                class="record-primary"
                role="status"
                >{{
                  Math.ceil(getSessionTiming(res)!.remainingSeconds / 60)
                }}
                min · ending soon</span
              >
              <span v-else-if="getSessionTiming(res)?.ended"
                >Over by
                {{ Math.ceil(getSessionTiming(res)!.overtimeSeconds / 60) }}
                min</span
              >
              <span v-else
                >{{
                  Math.ceil(getSessionTiming(res)!.remainingSeconds / 60)
                }}
                min</span
              >
            </template>
            <template v-else>–</template>
          </td>
          <td class="numeric">฿{{ res.hourlyRate ?? 200 }}</td>
          <td class="numeric">
            <strong class="record-primary"
              >฿{{ calculateCurrentPlayFee(res) }}</strong
            >
          </td>
          <AppTableActions
            ><AppButton
              :aria-label="`Add refreshments to ${tableLabel(res.tableId)}`"
              @click="openAddSnacks(res)"
              >Refreshments</AppButton
            ><AppButton
              appearance="primary"
              :aria-label="`Check bill for ${tableLabel(res.tableId)}`"
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
