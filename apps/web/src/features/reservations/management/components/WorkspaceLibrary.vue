<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { games, type CatalogGame } from '../../../../shared/data/catalog';
import {
  AppBadge,
  AppButton,
  AppDataTable,
  AppDescriptionList,
  AppEmptyState,
  AppFilterButton,
  AppInspector as WorkspaceRecordDetails,
  AppMasterDetail,
  AppPage,
  AppPanel,
  AppTableToolbar,
  AppRecordLink,
  AppSearchField,
  AppTableActions,
  AppTableSelect,
  useTableSelection,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const {
  gameSearchQuery,
  gameStatusFilter,
  getGameLocation,
  openAssignGame,
  returnGameFromAnyTable,
  filteredGames,
  selectedBranchId,
} = useManagementWorkspace();
const columns = [
  { label: 'Game' },
  { label: 'Players' },
  { label: 'Play time' },
  { label: 'Difficulty' },
  { label: 'Location' },
  { label: 'Actions', action: true },
];
const selectedGame = ref<CatalogGame | null>(null);
const table = ref<InstanceType<typeof AppDataTable>>();
const page = ref<InstanceType<typeof AppPage>>();
const selection = useTableSelection(
  computed(() => filteredGames.value.map((game) => game.id)),
);
const returnableGames = computed(() =>
  filteredGames.value.filter(
    (game) =>
      selection.selectedIds.value.has(game.id) && getGameLocation(game.name),
  ),
);
async function returnSelectedGames() {
  for (const game of returnableGames.value) returnGameFromAnyTable(game.name);
  selection.clear();
  await nextTick();
  if (table.value) table.value.focus();
  else page.value?.focusHeading();
}
let detailsTrigger: HTMLElement | null = null;
function showDetails(game: CatalogGame, event: MouseEvent) {
  const target = event.currentTarget as HTMLElement;
  detailsTrigger = target.matches('button')
    ? target
    : target.querySelector<HTMLButtonElement>('[data-record-trigger]');
  selectedGame.value = game;
}
function closeDetails() {
  selectedGame.value = null;
  detailsTrigger?.focus({ preventScroll: true });
}
watch([gameSearchQuery, gameStatusFilter, selectedBranchId], () => {
  selectedGame.value = null;
  selection.clear();
});
</script>

<template>
  <AppPage ref="page" title="Game library">
    <AppPanel aria-label="Game collection">
      <AppTableToolbar
        :result-label="`${filteredGames.length} games`"
        :selected-count="selection.count.value"
        @clear-selection="selection.clear"
      >
        <div
          class="workspace-filters"
          role="group"
          aria-label="Game shelf status"
        >
          <AppFilterButton
            :pressed="gameStatusFilter === 'ALL'"
            @click="gameStatusFilter = 'ALL'"
            >All games
            <span class="workspace-count">{{
              games.length
            }}</span></AppFilterButton
          >
          <AppFilterButton
            :pressed="gameStatusFilter === 'AVAILABLE'"
            @click="gameStatusFilter = 'AVAILABLE'"
            >On shelf
            <span class="workspace-count">{{
              games.filter((game) => !getGameLocation(game.name)).length
            }}</span></AppFilterButton
          >
          <AppFilterButton
            :pressed="gameStatusFilter === 'IN_USE'"
            @click="gameStatusFilter = 'IN_USE'"
            >In use
            <span class="workspace-count">{{
              games.filter((game) => !!getGameLocation(game.name)).length
            }}</span></AppFilterButton
          >
        </div>
        <AppSearchField
          v-model="gameSearchQuery"
          label="Search game library"
          placeholder="Name, designer, or genre"
        />
        <template #selection
          ><AppButton
            appearance="primary"
            :disabled="!returnableGames.length"
            @click="returnSelectedGames"
            >Return to shelf ({{ returnableGames.length }})</AppButton
          ></template
        >
      </AppTableToolbar>
      <AppEmptyState
        v-if="!filteredGames.length"
        title="No games match these filters"
        ><AppButton
          @click="
            gameSearchQuery = '';
            gameStatusFilter = 'ALL';
          "
          >Clear filters</AppButton
        ></AppEmptyState
      >
      <AppMasterDetail
        v-else
        class="workspace-master-detail"
        :inspecting="!!selectedGame"
      >
        <AppDataTable
          ref="table"
          class="workspace-records"
          label="Game library table"
          caption="Games, shelf status, and table assignments"
          :columns="columns"
        >
          <template #selection-header
            ><AppTableSelect
              header
              label="Select all visible games"
              :model-value="selection.allSelected.value"
              :indeterminate="selection.indeterminate.value"
              @update:model-value="selection.toggleAll"
          /></template>
          <tr
            v-for="game in filteredGames"
            :key="game.id"
            data-record-row
            :data-active="selectedGame?.id === game.id"
            :aria-selected="selection.selectedIds.value.has(game.id)"
            @click="showDetails(game, $event)"
          >
            <AppTableSelect
              :label="`Select ${game.name}`"
              :model-value="selection.selectedIds.value.has(game.id)"
              @update:model-value="selection.toggle(game.id, $event)"
            />
            <td class="library-game">
              <AppRecordLink
                data-record-trigger
                :aria-expanded="selectedGame?.id === game.id"
                @click.stop="showDetails(game, $event)"
                >{{ game.name }}</AppRecordLink
              ><span class="record-secondary">{{ game.designer || '—' }}</span>
            </td>
            <td>{{ game.players[0] }}–{{ game.players[1] }}</td>
            <td class="library-nowrap">
              {{ game.playMinutes[0] }}–{{ game.playMinutes[1] }} min
            </td>
            <td>{{ game.difficulty }}</td>
            <td>
              <AppBadge
                :tone="getGameLocation(game.name) ? 'warning' : 'success'"
                >{{ getGameLocation(game.name) || 'On shelf' }}</AppBadge
              >
            </td>
            <AppTableActions
              ><AppButton
                v-if="getGameLocation(game.name)"
                :aria-label="`Return ${game.name} to shelf`"
                @click="returnGameFromAnyTable(game.name)"
                >Return to shelf</AppButton
              ><AppButton
                v-else
                appearance="primary"
                :aria-label="`Assign ${game.name} to table`"
                @click="openAssignGame(game)"
                ><i class="fa-solid fa-plus" aria-hidden="true"></i>Assign to
                table</AppButton
              ></AppTableActions
            >
          </tr>
        </AppDataTable>
        <WorkspaceRecordDetails
          v-if="selectedGame"
          :title="selectedGame.name"
          title-id="library-details-title"
          @close="closeDetails"
        >
          <AppDescriptionList
            :items="[
              { label: 'Designer', value: selectedGame.designer || '—' },
              {
                label: 'Players',
                value: `${selectedGame.players[0]}–${selectedGame.players[1]}`,
              },
              {
                label: 'Play time',
                value: `${selectedGame.playMinutes[0]}–${selectedGame.playMinutes[1]} min`,
              },
              { label: 'Difficulty', value: selectedGame.difficulty },
              {
                label: 'Genre',
                value: selectedGame.categories.join(', ') || '—',
              },
              {
                label: 'Location',
                value: getGameLocation(selectedGame.name) || 'On shelf',
              },
            ]"
          />
          <p class="record-details-description">
            {{ selectedGame.description }}
          </p>
          <AppButton
            v-if="getGameLocation(selectedGame.name)"
            @click="returnGameFromAnyTable(selectedGame.name)"
            >Return to shelf</AppButton
          ><AppButton
            v-else
            appearance="primary"
            @click="openAssignGame(selectedGame)"
            >Assign to table</AppButton
          >
        </WorkspaceRecordDetails>
      </AppMasterDetail>
    </AppPanel>
  </AppPage>
</template>

<style scoped>
.library-game {
  min-width: 200px;
  max-width: 420px;
}
.library-game button {
  color: var(--ui-brand);
  text-align: left;
  font-weight: 600;
}
.library-game button:hover,
.library-game button:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.library-nowrap {
  white-space: nowrap;
}
</style>
