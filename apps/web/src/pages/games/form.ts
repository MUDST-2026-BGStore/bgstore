import type {
  Branch,
  GameCategory,
  GameDetail,
  GameLifecycle,
  GameRequest,
  LocalizedDescription,
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
  /** The catalogue's canonical title; the only one of the four that is required. */
  titleEn: string;
  titleTh: string;
  descriptionEn: string;
  descriptionTh: string;
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
    titleEn: '',
    titleTh: '',
    descriptionEn: '',
    descriptionTh: '',
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
    // The form edits each language on its own, so it fills in what is stored
    // rather than what the reader's locale resolves to.
    titleEn: game.title.en,
    titleTh: game.title.th ?? '',
    descriptionEn: game.description?.en ?? '',
    descriptionTh: game.description?.th ?? '',
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
 * A number the user left blank is sent as null rather than coerced to a
 * placeholder, so the API — the single source of validation — reports it as the
 * missing value it is. Text the browser could not read as a number never gets
 * here: {@link numericErrorsOf} stops the submit, because JSON has no way to
 * carry "four" in a field the contract types as an integer.
 */
export function toGameRequest(values: GameFormValues): GameRequest {
  return {
    title: { en: values.titleEn.trim(), th: values.titleTh.trim() || null },
    description: descriptionOf(values),
    // An unchosen category is left out rather than sent as "", which the API
    // could only read as an unknown category. Absent, it is reported as
    // required — which is what it is.
    category: (values.category || undefined) as GameCategory,
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

/** Nothing at all when neither language was filled in, matching the API. */
function descriptionOf(values: GameFormValues): LocalizedDescription | null {
  const en = values.descriptionEn.trim();
  const th = values.descriptionTh.trim();

  return en || th ? { en: en || null, th: th || null } : null;
}

/**
 * Numeric fields holding text the browser cannot read as a number, as the field
 * paths the API would use.
 *
 * A blank field is deliberately not reported here. Blank means the value is
 * missing, and the API answers that with `required`; text such as "four" means
 * the value is wrong, and only the browser can say so, because the conversion
 * to a number happens here and a malformed one cannot be put into the request
 * at all. Reporting both the same way — which is what sending null for each did
 * — tells someone who typed "four" that they typed nothing.
 */
export function numericErrorsOf(
  values: GameFormValues,
): Record<string, string> {
  const errors: Record<string, string> = {};
  const check = (field: string, value: string) => {
    if (value.trim() !== '' && toNumber(value) === null) {
      errors[field] = 'invalid';
    }
  };

  check('minPlayers', values.minPlayers);
  check('maxPlayers', values.maxPlayers);
  check('playTimeMinutes', values.playTimeMinutes);
  values.copies.forEach((row, index) => {
    check('copies[' + index + '].copies', row.copies);
  });

  return errors;
}

/**
 * Blank is nothing; anything that is not a whole number is unreadable. Every
 * numeric field on this form is a count, so a decimal or a sign is as wrong as
 * a word, and saying so here beats a round-trip that reports a range violation.
 */
function toNumber(value: string): number | null {
  const trimmed = value.trim();

  return /^\d+$/.test(trimmed) ? Number(trimmed) : null;
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

/** Category `<option>`s, labelled through the caller's translator. */
export function categoryOptions(
  translate: (key: string) => string,
): { value: string; label: string }[] {
  return gameCategories.map((category) => ({
    value: category,
    label: translate('games.category.' + category),
  }));
}
