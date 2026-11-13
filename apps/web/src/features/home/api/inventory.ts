import { computed, type MaybeRefOrGetter, toValue } from 'vue';
import { useQueries, useQuery } from '@tanstack/vue-query';
import {
  getBranchGame,
  listBranchGames,
  type BranchGame,
} from '@theparlor/api-client';
import { httpStatus } from '../../../shared/lib/api-errors';
import { collectPages } from '../../../shared/lib/pagination';

export const offeringKey = (branchId?: string, gameId?: string) =>
  ['storefront', 'offering', branchId ?? '', gameId ?? ''] as const;

export const branchGamesKey = (branchId?: string) =>
  ['storefront', 'branch-games', branchId ?? ''] as const;

export function branchOfferingQueryOptions(
  branchId: string | undefined,
  gameId: string | undefined,
) {
  return {
    queryKey: offeringKey(branchId, gameId),
    meta: { public: true },
    enabled: Boolean(branchId && gameId),
    staleTime: 30_000,
    retry: false,
    queryFn: async ({
      signal,
    }: {
      signal: AbortSignal;
    }): Promise<BranchGame | null> => {
      if (!branchId || !gameId) return null;
      try {
        return await getBranchGame(branchId, gameId, undefined, signal);
      } catch (error) {
        if (httpStatus(error) === 404) return null;
        throw error;
      }
    },
  };
}

/**
 * One branch's price and sale stock for a game, plus its play-library membership.
 * Resolves to null when the branch does not offer the game. Stock here is advisory:
 * the cart and checkout revalidate on the server.
 */
export function useBranchOffering(
  branchId: MaybeRefOrGetter<string | undefined>,
  gameId: MaybeRefOrGetter<string | undefined>,
) {
  const branch = computed(() => toValue(branchId));
  const game = computed(() => toValue(gameId));
  return useQuery(
    computed(() => branchOfferingQueryOptions(branch.value, game.value)),
  );
}

/**
 * Read-only offerings for a game across multiple branches, sharing the per-branch
 * offering cache with product pages.
 */
export function useBranchOfferings(
  branchIds: MaybeRefOrGetter<readonly string[]>,
  gameId: MaybeRefOrGetter<string | undefined>,
) {
  const ids = computed(() => toValue(branchIds));
  const game = computed(() => toValue(gameId));
  return useQueries({
    queries: computed(() =>
      ids.value.map((branchId) =>
        branchOfferingQueryOptions(branchId, game.value),
      ),
    ),
  });
}

/**
 * All active game offerings at a branch, used to filter catalog discovery by what is
 * sold at the chosen store or included in its rental library.
 */
export function useBranchGames(branchId: MaybeRefOrGetter<string | undefined>) {
  const branch = computed(() => toValue(branchId));
  return useQuery({
    queryKey: computed(() => branchGamesKey(branch.value)),
    meta: { public: true },
    enabled: computed(() => Boolean(branch.value)),
    staleTime: 30_000,
    retry: false,
    queryFn: ({ signal }): Promise<BranchGame[]> => {
      const id = branch.value;
      if (!id) return Promise.resolve([]);
      return collectPages((cursor) =>
        listBranchGames(id, { limit: 100, cursor }, undefined, signal),
      );
    },
  });
}
