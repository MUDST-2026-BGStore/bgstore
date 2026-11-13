import { computed, ref, type Ref } from 'vue';
import type { BranchGame } from '@theparlor/api-client';
import {
  games,
  type CatalogGame,
  type Category,
} from '../../../shared/data/catalog';
import { useBranchGamesQuery } from './branch-games';
import type { useWorkspaceReservations } from './useWorkspaceReservations';

/** A library game; live games may have no recorded difficulty. */
export type WorkspaceGame = Omit<CatalogGame, 'difficulty'> & {
  difficulty: CatalogGame['difficulty'] | null;
};

const difficulties: Record<string, CatalogGame['difficulty']> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  ADVANCED: 'Advanced',
};

const languageName = (code: string) =>
  ({ en: 'English', th: 'Thai' })[code.toLowerCase()] ?? code.toUpperCase();

export function toCatalogGame({ game, editions }: BranchGame): WorkspaceGame {
  const primaryEdition = editions[0];
  return {
    id: game.id,
    name: game.name,
    designer: game.designer ?? '',
    categories: game.categories as Category[],
    mechanics: game.mechanics,
    players: [game.minPlayers, game.maxPlayers],
    playMinutes: [game.playMinutesMin, game.playMinutesMax],
    teachMinutes: game.teachMinutes ?? 0,
    setupMinutes: game.setupMinutes ?? 0,
    difficulty: game.difficulty
      ? (difficulties[game.difficulty] ?? null)
      : null,
    language: [
      ...new Set(editions.map((edition) => languageName(edition.languageCode))),
    ].join(' · '),
    description: game.descriptions?.['en-US'] ?? '',
    retailPrice: primaryEdition
      ? Math.round(primaryEdition.salePrice.amountMinor / 100)
      : null,
  };
}

export function useWorkspaceGames({
  selectedBranchId,
  branchFloorTables,
  getTableStatus,
}: Pick<
  ReturnType<typeof useWorkspaceReservations>,
  'branchFloorTables' | 'getTableStatus'
> & {
  selectedBranchId: Ref<string>;
}) {
  const branchGames = useBranchGamesQuery(selectedBranchId);
  // The branch's play library; sample games remain only while it offers none.
  const libraryGames = computed<WorkspaceGame[]>(() => {
    const live = (branchGames.data.value ?? []).filter(
      (offering) => offering.rentalEnabled,
    );
    return live.length ? live.map(toCatalogGame) : games;
  });
  // ==========================================
  // FEATURE 1: ASSIGNED GAMES PER TABLE & STRICT RULES
  // Rule 1: Only start session can be assigned game
  // Rule 2: Cannot assign game to assigned table
  // ==========================================
  const gameSearchQuery = ref('');
  const gameStatusFilter = ref<'ALL' | 'AVAILABLE' | 'IN_USE'>('ALL');
  const selectedGameToAssign = ref<WorkspaceGame | null>(null);
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

  function openAssignGame(game: WorkspaceGame) {
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
    const firstAvail =
      libraryGames.value.find((g) => !getGameLocation(g.name)) ||
      libraryGames.value[0];
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
    return libraryGames.value.filter((g) => {
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
    return libraryGames.value.filter((g) => {
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
    libraryGames,
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
