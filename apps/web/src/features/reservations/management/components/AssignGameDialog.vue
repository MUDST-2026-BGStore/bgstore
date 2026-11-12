<script setup lang="ts">
import { computed } from 'vue';
import {
  AppButton,
  AppChoiceGroup,
  AppDialog,
  AppDialogPanel,
  AppFormGrid,
  AppSearchField,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  branchFloorTables,
  getTableStatus,
  selectedGameToAssign,
  assignGameTargetTableId,
  showAssignGameModal,
  assignModalSearch,
  getTableAssignedGames,
  getGameLocation,
  eligibleTablesForGame,
  confirmAssignGame,
  assignableGamesList,
  canConfirmAssign,
} = useManagementWorkspace();
const tables = computed(() =>
  branchFloorTables.map((table) => ({
    id: table.id,
    label: `Table ${table.number}`,
    disabled:
      getTableStatus(table).state !== 'OCCUPIED' ||
      getTableAssignedGames(table.id).length > 0,
    description:
      getTableStatus(table).state !== 'OCCUPIED'
        ? 'Not in play'
        : getTableAssignedGames(table.id)[0] || 'Available',
  })),
);
const games = computed(() =>
  assignableGamesList.value.map((game) => ({
    id: game.id,
    label: game.name,
    description: `${game.players[0]}–${game.players[1]} players · ${game.playMinutes[0]}–${game.playMinutes[1]} min\n${getGameLocation(game.name) ? `In use (${getGameLocation(game.name)})` : 'Available on shelf'}`,
    disabled: !!getGameLocation(game.name),
  })),
);
function selectGame(id: string) {
  const game = assignableGamesList.value.find((game) => game.id === id);
  if (game && !getGameLocation(game.name)) selectedGameToAssign.value = game;
}
</script>
<template>
  <AppDialog
    v-if="showAssignGameModal"
    title-id="AssignGameDialog-title"
    size="wide"
    @close="showAssignGameModal = false"
  >
    <AppDialogPanel
      title-id="AssignGameDialog-title"
      title="Assign game"
      description="One game per active table."
      @close="showAssignGameModal = false"
    >
      <AppFormGrid :columns="1">
        <p
          v-if="!eligibleTablesForGame.length"
          class="assignment-empty"
          role="status"
        >
          No active tables without a game.
        </p>
        <AppChoiceGroup
          v-model="assignGameTargetTableId"
          label="Table"
          :options="tables"
        />
        <AppSearchField
          v-model="assignModalSearch"
          label="Search games to assign"
          placeholder="Filter available games…"
        />
        <AppChoiceGroup
          label="Game"
          layout="list"
          :model-value="selectedGameToAssign?.id || ''"
          :options="games"
          @update:model-value="selectGame"
        />
        <p v-if="!games.length" class="assignment-empty" role="status">
          No matching games
        </p>
      </AppFormGrid>
      <template #footer
        ><AppButton @click="showAssignGameModal = false">Cancel</AppButton
        ><AppButton
          appearance="primary"
          :disabled="!canConfirmAssign"
          @click="confirmAssignGame"
          >Assign game</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
<style scoped>
.assignment-empty {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--ui-muted);
}
</style>
