import { inject, provide, type InjectionKey } from 'vue';
import { useWorkspaceNavigation } from './useWorkspaceNavigation';
import { useWorkspaceReservations } from './useWorkspaceReservations';
import { useWorkspaceGames } from './useWorkspaceGames';
import { useWorkspacePantry } from './useWorkspacePantry';
import { useWorkspaceSettings } from './useWorkspaceSettings';
import { useWorkspaceSaleStock } from './useWorkspaceSaleStock';

function createWorkspace() {
  const reservations = useWorkspaceReservations();
  const pantry = useWorkspacePantry();
  return {
    ...useWorkspaceNavigation(),
    ...reservations,
    ...useWorkspaceGames(reservations),
    ...pantry,
    saleStock: useWorkspaceSaleStock(pantry.branchStockItems),
    ...useWorkspaceSettings(),
  };
}

// One workspace session owns state across view switches; no module-level mutable state.
const workspaceKey: InjectionKey<ReturnType<typeof createWorkspace>> = Symbol(
  'management-workspace',
);
export function provideManagementWorkspace() {
  const workspace = createWorkspace();
  provide(workspaceKey, workspace);
  return workspace;
}
export function useManagementWorkspace() {
  const workspace = inject(workspaceKey);
  if (!workspace)
    throw new Error('Management views require a workspace provider');
  return workspace;
}
