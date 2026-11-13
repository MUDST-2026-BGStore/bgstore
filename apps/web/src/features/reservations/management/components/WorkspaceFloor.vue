<script setup lang="ts">
import { computed, ref } from 'vue';
import { useManagementWorkspace } from '../context';
import FloorTableCard from './FloorTableCard.vue';
import {
  AppButton,
  AppEmptyState,
  AppFilterButton,
  AppPage,
  AppPanel,
  AppToolbar,
} from '../../../../shared/ui';
const {
  branchFloorTables,
  openWalkInForTable,
  getTableStatus,
  selectedBranch,
} = useManagementWorkspace();
const statusFilter = ref('ALL');
const filters = computed(() => [
  { id: 'ALL', label: 'All tables', count: branchFloorTables.length },
  ...(['OCCUPIED', 'RESERVED', 'FREE'] as const).map((state) => ({
    id: state,
    label:
      state === 'OCCUPIED'
        ? 'In play'
        : state === 'RESERVED'
          ? 'Reserved'
          : 'Free',
    count: branchFloorTables.filter(
      (table) => getTableStatus(table).state === state,
    ).length,
  })),
]);
const visibleTables = computed(() =>
  branchFloorTables.filter(
    (table) =>
      statusFilter.value === 'ALL' ||
      getTableStatus(table).state === statusFilter.value,
  ),
);
</script>

<template>
  <AppPage title="Tables & floor"
    ><template #actions
      ><AppButton
        appearance="primary"
        :disabled="!selectedBranch"
        @click="openWalkInForTable()"
        ><i class="fa-solid fa-plus" aria-hidden="true"></i>Seat
        walk-in</AppButton
      ></template
    >
    <AppPanel aria-label="Floor tables">
      <AppToolbar>
        <div
          class="workspace-filters"
          role="group"
          aria-label="Filter tables by status"
        >
          <AppFilterButton
            v-for="filter in filters"
            :key="filter.id"
            :pressed="statusFilter === filter.id"
            @click="statusFilter = filter.id"
            >{{ filter.label
            }}<span class="workspace-count">{{
              filter.count
            }}</span></AppFilterButton
          >
        </div>
        <span class="workspace-results" role="status"
          >{{ visibleTables.length }} of
          {{ branchFloorTables.length }} tables</span
        >
      </AppToolbar>
      <div v-if="visibleTables.length" class="floor-tables-grid">
        <FloorTableCard
          v-for="table in visibleTables"
          :key="table.id"
          :table="table"
        />
      </div>
      <AppEmptyState v-else title="No tables with this status"
        ><AppButton @click="statusFilter = 'ALL'"
          >Show all tables</AppButton
        ></AppEmptyState
      >
    </AppPanel>
  </AppPage>
</template>
<style scoped>
.floor-tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: 12px;
  padding: 16px;
}
@media (max-width: 700px) {
  .floor-tables-grid {
    padding: 12px;
  }
}
</style>
