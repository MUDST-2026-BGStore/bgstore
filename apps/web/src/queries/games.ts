import { queryOptions } from '@tanstack/vue-query';
import {
  createGame,
  getGame,
  listBranches,
  listGames,
  retireGame,
  updateGame,
} from '../generated/api/sdk.gen';
import type {
  GameDetail,
  GameListResponse,
  GameRequest,
  ListGamesData,
} from '../generated/api/types.gen';

export type GameListQuery = NonNullable<ListGamesData['query']>;

/** Rows per inventory page. The contract caps `size` at 100. */
export const gamesPageSize = 20;

export const branchesQueryOptions = () =>
  queryOptions({
    queryKey: ['branches'] as const,
    queryFn: async () => {
      const { data } = await listBranches({ throwOnError: true });
      return data.items;
    },
    // The branch directory is reference data; refetching it on every filter
    // change would be noise.
    staleTime: 5 * 60_000,
  });

export const gamesQueryOptions = (query: GameListQuery) =>
  queryOptions({
    queryKey: ['games', 'list', query] as const,
    queryFn: async (): Promise<GameListResponse> => {
      const { data } = await listGames({ query, throwOnError: true });
      return data;
    },
    // Paging should not blank the table out from under the reader.
    placeholderData: (previous) => previous,
  });

export const gameQueryOptions = (gameId: string) =>
  queryOptions({
    queryKey: ['games', 'detail', gameId] as const,
    queryFn: async (): Promise<GameDetail> => {
      const { data } = await getGame({ path: { gameId }, throwOnError: true });
      return data;
    },
    // A game that does not exist will not start existing on a retry.
    retry: false,
  });

export async function createGameRequest(
  body: GameRequest,
): Promise<GameDetail> {
  const { data } = await createGame({ body, throwOnError: true });
  return data;
}

export async function updateGameRequest(
  gameId: string,
  body: GameRequest,
): Promise<GameDetail> {
  const { data } = await updateGame({
    body,
    path: { gameId },
    throwOnError: true,
  });
  return data;
}

export async function retireGameRequest(gameId: string): Promise<void> {
  await retireGame({ path: { gameId }, throwOnError: true });
}
