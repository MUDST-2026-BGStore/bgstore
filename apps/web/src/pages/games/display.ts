import type { GameSummary } from '../../generated/api/types.gen';

/**
 * Formats an API timestamp the way the design writes dates ("12 Jan 2025").
 *
 * `locale` is the active i18n locale so the Thai UI gets Thai month names.
 */
export function formatDate(
  isoTimestamp: string | null | undefined,
  locale: string,
): string | undefined {
  if (!isoTimestamp) {
    return undefined;
  }

  const parsed = new Date(isoTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }

  // The design writes dates day-first ("12 Jan 2025"), which `en` on its own
  // renders month-first, so the parts are assembled here. Month names still
  // come from the active locale.
  const parts = new Intl.DateTimeFormat(locale, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).formatToParts(parsed);

  const partOf = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';

  return `${partOf('day')} ${partOf('month')} ${partOf('year')}`;
}

/** The 1-based row range the "Showing {from}–{to} of {total}" line reports. */
export function pageRange(
  pageNumber: number,
  pageSize: number,
  rowsOnPage: number,
): { from: number; to: number } {
  const from = pageNumber * pageSize + 1;

  return { from, to: from + rowsOnPage - 1 };
}

/**
 * The list shows one row per game but a single Branch column, so a game held at
 * several branches reports the count instead of a name. `branchName` is only
 * sent when the row's figures come from exactly one branch.
 */
export function branchLabel(
  row: GameSummary,
  manyBranches: (count: number) => string,
  none: string,
): string {
  if (row.branchName) {
    return row.branchName;
  }

  return row.branchCount > 0 ? manyBranches(row.branchCount) : none;
}
