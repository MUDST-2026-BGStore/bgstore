// TODO(workspace-inventory-api): Reads the fixture catalog and local reservations.
// Branch offerings and rental copies now have endpoints (branch games, rental
// copies); wire this composable to them.
import { computed, ref } from 'vue';
import { games, type CatalogGame } from '../../../shared/data/catalog';
import type { useWorkspaceReservations } from './useWorkspaceReservations';

export function useWorkspaceGames({
  branchFloorTables,
  getTableStatus,
}: Pick<
  ReturnType<typeof useWorkspaceReservations>,
  'branchFloorTables' | 'getTableStatus'
>) {
  // ==========================================
  // FEATURE 1: ASSIGNED GAMES PER TABLE & STRICT RULES
  // Rule 1: Only start session can be assigned game
  // Rule 2: Cannot assign game to assigned table
  // ==========================================
  const gameSearchQuery = ref('');
  const gameStatusFilter = ref<'ALL' | 'AVAILABLE' | 'IN_USE'>('ALL');
  const selectedGameToAssign = ref<CatalogGame | null>(null);
  const assignGameTargetTableId = ref('1');
  const showAssignGameModal = ref(false);
  const assignModalSearch = ref('');

  // Table assigned games tracking
  const tableAssignedGames = ref<Record<string, string[]>>({
    '1': ['Azul'],
    '2': ['Wingspan'],
  });

  function getTableAssignedGames(tableId: string | number): string[] {
    return tableAssignedGames.value[String(tableId)] || [];
  }

  function getGameLocation(gameName: string): string | null {
    for (const [tId, gList] of Object.entries(tableAssignedGames.value)) {
      if (gList.some((g) => g.toLowerCase() === gameName.toLowerCase())) {
        return `Table ${tId}`;
      }
    }
    return null;
  }

  // Tables that have an active started session
  const activeStartedTables = computed(() => {
    return branchFloorTables.filter(
      (t) => getTableStatus(t).state === 'OCCUPIED',
    );
  });

  // Tables eligible to receive a game:
  // 1. Must have a started session (state === 'OCCUPIED')
  // 2. Must not already have a game assigned (not an assigned table)
  const eligibleTablesForGame = computed(() => {
    return activeStartedTables.value.filter(
      (t) => getTableAssignedGames(t.id).length === 0,
    );
  });

  function openAssignGame(game: CatalogGame) {
    selectedGameToAssign.value = game;
    assignModalSearch.value = '';
    // Default to first eligible table if available
    const firstEligible = eligibleTablesForGame.value[0];
    if (firstEligible) {
      assignGameTargetTableId.value = firstEligible.id;
    }
    showAssignGameModal.value = true;
  }

  function openAssignGameForTable(tableId: string | number) {
    const idStr = String(tableId);
    const tbl = branchFloorTables.find((t) => t.id === idStr);

    // Rule 1 check: Only started sessions can be assigned game
    if (!tbl || getTableStatus(tbl).state !== 'OCCUPIED') {
      return;
    }

    // Rule 2 check: Cannot assign game to assigned table
    if (getTableAssignedGames(idStr).length > 0) {
      return;
    }

    assignGameTargetTableId.value = idStr;
    const firstAvail = games.find((g) => !getGameLocation(g.name)) || games[0];
    selectedGameToAssign.value = firstAvail || null;
    assignModalSearch.value = '';
    showAssignGameModal.value = true;
  }

  function confirmAssignGame() {
    if (!selectedGameToAssign.value) return;
    const tId = assignGameTargetTableId.value;
    const tbl = branchFloorTables.find((t) => t.id === tId);

    // Strict enforcement:
    // 1. Only start session can be assigned game
    if (!tbl || getTableStatus(tbl).state !== 'OCCUPIED') {
      return;
    }
    // 2. Cannot assign game to assigned table
    if (getTableAssignedGames(tId).length > 0) {
      return;
    }
    // 3. Game must be available
    if (getGameLocation(selectedGameToAssign.value.name)) {
      return;
    }

    if (!tableAssignedGames.value[tId]) {
      tableAssignedGames.value[tId] = [];
    }
    tableAssignedGames.value[tId].push(selectedGameToAssign.value.name);
    showAssignGameModal.value = false;
  }

  function unassignGameFromTable(tableId: string | number, gameName: string) {
    const idStr = String(tableId);
    if (tableAssignedGames.value[idStr]) {
      tableAssignedGames.value[idStr] = tableAssignedGames.value[idStr].filter(
        (g) => g.toLowerCase() !== gameName.toLowerCase(),
      );
    }
  }

  function returnGameFromAnyTable(gameName: string) {
    for (const tId of Object.keys(tableAssignedGames.value)) {
      tableAssignedGames.value[tId] = tableAssignedGames.value[tId].filter(
        (g) => g.toLowerCase() !== gameName.toLowerCase(),
      );
    }
  }

  const filteredGames = computed(() => {
    const q = gameSearchQuery.value.trim().toLowerCase();
    return games.filter((g) => {
      const isAssigned = !!getGameLocation(g.name);
      if (gameStatusFilter.value === 'AVAILABLE' && isAssigned) return false;
      if (gameStatusFilter.value === 'IN_USE' && !isAssigned) return false;

      if (!q) return true;
      return (
        g.name.toLowerCase().includes(q) ||
        g.designer.toLowerCase().includes(q) ||
        g.categories.some((c) => c.toLowerCase().includes(q))
      );
    });
  });

  const assignableGamesList = computed(() => {
    const q = assignModalSearch.value.trim().toLowerCase();
    return games.filter((g) => {
      if (!q) return true;
      return (
        g.name.toLowerCase().includes(q) ||
        g.designer.toLowerCase().includes(q) ||
        g.categories.some((c) => c.toLowerCase().includes(q))
      );
    });
  });

  // Check if currently selected target table in modal is valid
  const targetTableEligible = computed(() => {
    const tbl = branchFloorTables.find(
      (t) => t.id === assignGameTargetTableId.value,
    );
    if (!tbl) return false;
    const isStarted = getTableStatus(tbl).state === 'OCCUPIED';
    const hasGame = getTableAssignedGames(tbl.id).length > 0;
    return isStarted && !hasGame;
  });

  const canConfirmAssign = computed(() => {
    return (
      !!selectedGameToAssign.value &&
      !getGameLocation(selectedGameToAssign.value.name) &&
      targetTableEligible.value
    );
  });
  return {
    gameSearchQuery,
    gameStatusFilter,
    selectedGameToAssign,
    assignGameTargetTableId,
    showAssignGameModal,
    assignModalSearch,
    tableAssignedGames,
    getTableAssignedGames,
    getGameLocation,
    activeStartedTables,
    eligibleTablesForGame,
    openAssignGame,
    openAssignGameForTable,
    confirmAssignGame,
    unassignGameFromTable,
    returnGameFromAnyTable,
    filteredGames,
    assignableGamesList,
    targetTableEligible,
    canConfirmAssign,
  };
}
