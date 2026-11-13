import { computed, type MaybeRefOrGetter, toValue } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import {
  listGames,
  listCategories,
  listMechanics,
  listGameEditions,
  useGetGame,
  type Game,
  type Category,
  type Mechanic,
} from '@theparlor/api-client';
import { collectPages } from '../../../shared/lib/pagination';

export { collectPages };

export function useCatalog() {
  return useQuery({
    queryKey: ['storefront', 'catalog'],
    meta: { public: true },
    queryFn: ({ signal }) =>
      collectPages((cursor) =>
        listGames({ limit: 100, cursor }, undefined, signal),
      ),
    staleTime: 60_000,
    retry: false,
  });
}

export function useTaxonomy() {
  return useQuery({
    queryKey: ['storefront', 'taxonomy'],
    meta: { public: true },
    queryFn: async ({ signal }) => {
      const [categories, mechanics] = await Promise.all([
        collectPages((cursor) =>
          listCategories({ limit: 100, cursor }, undefined, signal),
        ),
        collectPages((cursor) =>
          listMechanics({ limit: 100, cursor }, undefined, signal),
        ),
      ]);
      return { categories, mechanics };
    },
    staleTime: 300_000,
    retry: false,
  });
}

/**
 * Cover image lookup by game ID for views that only know the ID, such as cart and
 * order lines. It shares the cached catalog read; unknown games have no cover.
 */
export function useGameCovers() {
  const catalog = useCatalog();
  const covers = computed(
    () =>
      new Map(
        (catalog.data.value ?? []).map((game) => [game.id, imageUrl(game)]),
      ),
  );
  return (gameId: string | undefined) =>
    gameId ? covers.value.get(gameId) : undefined;
}

// A malformed route is a local 404, not a request to /games/undefined or a slug fallback.
export const isGameId = (id: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);

export function useProduct(id: MaybeRefOrGetter<string>) {
  const gameId = computed(() => toValue(id));
  const valid = computed(() => isGameId(gameId.value));
  const game = useGetGame(gameId, {
    query: {
      enabled: valid,
      staleTime: 60_000,
      retry: false,
      meta: { public: true },
    },
  });
  const editions = useQuery({
    queryKey: computed(() => ['storefront', 'editions', gameId.value]),
    meta: { public: true },
    enabled: computed(() => valid.value && Boolean(game.data.value)),
    queryFn: ({ signal }) => {
      const id = gameId.value;
      return collectPages((cursor) =>
        listGameEditions(id, { limit: 100, cursor }, undefined, signal),
      );
    },
    staleTime: 60_000,
    retry: false,
  });
  return { game, editions, valid };
}

export function localizedDescription(game: Game, locale: string): string {
  return game.descriptions?.[locale] ?? game.descriptions?.['en-US'] ?? '';
}

export function taxonomyLabel(
  items: Array<Category | Mechanic> | undefined,
  key: string,
  locale: string,
): string {
  const entry = items?.find((item) => item.name === key);
  return entry?.names[locale] ?? entry?.names['en-US'] ?? key;
}

export function cover(game: Game) {
  return [...(game.images ?? [])].sort((a, b) => a.sortOrder - b.sortOrder)[0];
}

export function imageUrl(game: Game): string | undefined {
  const url = cover(game)?.url;
  if (!url) return undefined;
  if (url.startsWith('/') && !url.startsWith('//')) return url;
  try {
    const parsed = new URL(url);
    return ['https:', 'http:'].includes(parsed.protocol) ? url : undefined;
  } catch {
    return undefined;
  }
}
