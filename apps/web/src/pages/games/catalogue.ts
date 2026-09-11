import type { BranchStock, GameCategory } from '../../generated/api/types.gen';

/** A message lookup, as `useI18n().t` provides it. */
type Translate = (key: string, named: Record<string, unknown>) => string;

/** The category chips, in the order the client catalogue design lists them. */
export const catalogueCategories: readonly GameCategory[] = [
  'card',
  'party',
  'strategy',
  'family',
];

/** "2–5 players", or "4 players" when the range is a single count. */
export function playerCount(
  minPlayers: number,
  maxPlayers: number,
  translate: Translate,
): string {
  return minPlayers === maxPlayers
    ? translate('catalogue.playersExact', { count: minPlayers })
    : translate('catalogue.players', { min: minPlayers, max: maxPlayers });
}

/** "15 min", or nothing when the catalogue does not say. */
export function playTime(
  minutes: number | null | undefined,
  translate: Translate,
): string {
  return minutes ? translate('catalogue.minutes', { minutes }) : '';
}

/** The card's second line: player count, then play time when it is known. */
export function cardMeta(
  game: {
    minPlayers: number;
    maxPlayers: number;
    playTimeMinutes?: number | null;
  },
  translate: Translate,
): string {
  return [
    playerCount(game.minPlayers, game.maxPlayers, translate),
    playTime(game.playTimeMinutes, translate),
  ]
    .filter(Boolean)
    .join(' · ');
}

/** Copies on a shelf right now, over every branch. */
export function freeCopies(stock: readonly BranchStock[]): number {
  return stock.reduce((total, branch) => total + branch.available, 0);
}

/**
 * Branches a guest could find the game at: the ones holding a copy, whether or
 * not it is out on a table right now.
 */
export function stockedBranches(stock: readonly BranchStock[]): BranchStock[] {
  return stock.filter((branch) => branch.copies > 0);
}

/** Steps past the first wrap around, so the gallery arrows never dead-end. */
export function wrapIndex(index: number, length: number): number {
  return length === 0 ? 0 : ((index % length) + length) % length;
}
