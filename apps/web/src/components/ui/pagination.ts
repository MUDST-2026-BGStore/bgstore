/** A page number to render as a button, or a gap standing for hidden pages. */
export type PageItem = number | '…';

/**
 * The page buttons the Figma Pagination component draws: the first and last
 * page, the current page with its neighbours, and an ellipsis for each run of
 * hidden pages. Near either end it shows three pages ("1 2 3 … 9"), and a gap
 * of one page shows that page rather than an ellipsis of the same width.
 */
export function pageItems(current: number, totalPages: number): PageItem[] {
  const shown = new Set([1, totalPages, current - 1, current, current + 1]);
  if (current <= 2) {
    [1, 2, 3].forEach((page) => shown.add(page));
  }
  if (current >= totalPages - 1) {
    [totalPages - 2, totalPages - 1].forEach((page) => shown.add(page));
  }

  const pages = [...shown]
    .filter((page) => page >= 1 && page <= totalPages)
    .sort((a, b) => a - b);

  return pages.flatMap((page, index): PageItem[] => {
    const previous = pages[index - 1];
    if (previous === undefined || page - previous === 1) {
      return [page];
    }
    return page - previous === 2 ? [previous + 1, page] : ['…', page];
  });
}
