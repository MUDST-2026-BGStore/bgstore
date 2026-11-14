import { computed, type Ref } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { listBranchGames } from '@theparlor/api-client';
import { collectPages } from '../../../shared/lib/pagination';

export const branchGamesKey = (branchId: string) =>
  ['workspace', 'branchGames', branchId] as const;

/** Every game the selected branch offers, shared by library, stock and rental views. */
export function useBranchGamesQuery(selectedBranchId: Ref<string>) {
  return useQuery({
    queryKey: computed(() => branchGamesKey(selectedBranchId.value)),
    enabled: computed(() => Boolean(selectedBranchId.value)),
    queryFn: ({ signal }) =>
      collectPages((cursor) =>
        listBranchGames(
          selectedBranchId.value,
          { limit: 100, cursor },
          undefined,
          signal,
        ),
      ),
  });
}
