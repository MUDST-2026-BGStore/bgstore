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

/** Finish cursor traversal before exposing results so local filters/counts are complete.
 * This small single-store catalog is cached as one query, never persisted in Pinia.
 * Move the remaining local filters to server queries if catalog size requires it.
 */
export async function collectPages<T>(
  fetchPage: (cursor?: string) => Promise<{ items: T[]; nextCursor?: string }>,
): Promise<T[]> {
  const items: T[] = [];
  const cursors = new Set<string>();
  let cursor: string | undefined;
  do {
    const page = await fetchPage(cursor);
    items.push(...page.items);
    cursor = page.nextCursor || undefined;
    if (cursor) {
      if (cursors.has(cursor))
        throw new Error('Catalog pagination repeated a cursor');
      cursors.add(cursor);
    }
  } while (cursor);
  return items;
}

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
