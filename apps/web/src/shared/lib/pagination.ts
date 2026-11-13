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
