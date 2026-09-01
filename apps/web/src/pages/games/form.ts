import type {
  Branch,
  GameCategory,
  GameDetail,
  GameLifecycle,
  GameRequest,
  ValidationProblem,
} from '../../generated/api/types.gen';

/** Matches the `maxlength` the design puts on the short description. */
export const descriptionMaxLength = 160;

/** The contract's category enum, in the order the design lists it. */
export const gameCategories: readonly GameCategory[] = [
  'family',
  'card',
  'party',
  'strategy',
];

/** One row of the "Copies per branch" table. */
export interface BranchCopies {
  branchId: string;
  branchName: string;
  /** Kept as text because it is bound to a text input. */
  copies: string;
}

/**
 * The form's own shape: every field is a string because it is bound to an
 * input, and `toGameRequest` converts back to the contract types on submit.
 *
 * `lifecycle` and `tags` have no control in the design; they ride along so an
 * edit does not silently retire a game or drop its tags.
 */
export interface GameFormValues {
  title: string;
  description: string;
  /** A {@link GameCategory} value, or empty while the select is unset. */
  category: string;
  playTimeMinutes: string;
  minPlayers: string;
  maxPlayers: string;
  difficulty: string;
  copies: BranchCopies[];
  lifecycle: GameLifecycle;
  tags: string[];
}

export function emptyForm(branches: readonly Branch[]): GameFormValues {
  return {
    title: '',
    description: '',
    category: '',
    playTimeMinutes: '',
    minPlayers: '',
    maxPlayers: '',
    difficulty: '',
    copies: branchRows(branches, {}),
    lifecycle: 'active',
    tags: [],
  };
}

export function formValuesOf(
  game: GameDetail,
  branches: readonly Branch[],
): GameFormValues {
  const stocked: Record<string, number> = {};
  for (const entry of game.stock) {
    stocked[entry.branchId] = entry.copies;
  }

  return {
    title: game.title,
    description: game.description ?? '',
    category: game.category,
    playTimeMinutes: game.playTimeMinutes?.toString() ?? '',
    minPlayers: game.minPlayers.toString(),
    maxPlayers: game.maxPlayers.toString(),
    difficulty: game.difficulty ?? '',
    copies: branchRows(branches, stocked),
    lifecycle: game.lifecycle,
    tags: [...game.tags],
  };
}

function branchRows(
  branches: readonly Branch[],
  copies: Record<string, number>,
): BranchCopies[] {
  return branches.map((branch) => ({
    branchId: branch.id,
    branchName: branch.name,
    copies: (copies[branch.id] ?? 0).toString(),
  }));
}

/**
 * Builds the request body.
 *
 * Numbers that the user has not filled in are sent as they were typed rather
 * than coerced to a placeholder, so the API — the single source of validation —
 * decides what is acceptable. `NaN` would not survive JSON, so an unparseable
 * number is sent as null and comes back as "required".
 */
export function toGameRequest(values: GameFormValues): GameRequest {
  return {
    title: values.title.trim(),
    description: values.description.trim() || null,
    category: values.category as GameCategory,
    minPlayers: toNumber(values.minPlayers) as number,
    maxPlayers: toNumber(values.maxPlayers) as number,
    playTimeMinutes: toNumber(values.playTimeMinutes),
    difficulty: values.difficulty.trim() || null,
    tags: values.tags,
    lifecycle: values.lifecycle,
    copies: values.copies.map((row) => ({
      branchId: row.branchId,
      copies: toNumber(row.copies) as number,
    })),
  };
}

function toNumber(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === '') {
    return null;
  }
  const parsed = Number(trimmed);

  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Turns the API's 422 body into a field path to message-key map the form can
 * look up per input. Anything that is not a validation problem returns no
 * entries, so the caller falls back to a general failure notice.
 */
export function fieldErrorsOf(failure: unknown): Record<string, string> {
  const problem = failure as ValidationProblem | undefined;
  if (!problem || !Array.isArray(problem.errors)) {
    return {};
  }

  const errors: Record<string, string> = {};
  for (const error of problem.errors) {
    // The first message for a field is the most specific one the API found.
    errors[error.field] ??= error.message;
  }

  return errors;
}

/** Whether a failure carries the given HTTP status. */
export function hasStatus(failure: unknown, status: number): boolean {
  return (failure as { status?: unknown } | undefined)?.status === status;
}
