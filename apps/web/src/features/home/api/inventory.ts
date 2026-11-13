import { computed, type MaybeRefOrGetter, toValue } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { getBranchGame, type BranchGame } from '@theparlor/api-client';
import { httpStatus } from '../../../shared/lib/api-errors';

export const offeringKey = (branchId?: string, gameId?: string) =>
  ['storefront', 'offering', branchId ?? '', gameId ?? ''] as const;

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
  return useQuery({
    queryKey: computed(() => offeringKey(branch.value, game.value)),
    meta: { public: true },
    enabled: computed(() => Boolean(branch.value && game.value)),
    staleTime: 30_000,
    retry: false,
    queryFn: async ({ signal }): Promise<BranchGame | null> => {
      try {
        return await getBranchGame(
          branch.value!,
          game.value!,
          undefined,
          signal,
        );
      } catch (error) {
        if (httpStatus(error) === 404) return null;
        throw error;
      }
    },
  });
}
