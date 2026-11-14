import fc from 'fast-check';
import { expect, it } from 'vitest';
import { collectPages } from './pagination';

it('returns every item of every page in order', async () => {
  await fc.assert(
    fc.asyncProperty(
      fc.array(fc.array(fc.integer(), { maxLength: 5 }), {
        minLength: 1,
        maxLength: 12,
      }),
      async (pages) => {
        const fetchPage = async (cursor?: string) => {
          const index = cursor ? Number(cursor) : 0;
          return {
            items: pages[index],
            nextCursor:
              index + 1 < pages.length ? String(index + 1) : undefined,
          };
        };
        expect(await collectPages(fetchPage)).toEqual(pages.flat());
      },
    ),
  );
});

it('stops instead of looping when the server repeats a cursor', async () => {
  await fc.assert(
    fc.asyncProperty(fc.string({ minLength: 1 }), async (cursor) => {
      await expect(
        collectPages(async () => ({ items: [1], nextCursor: cursor })),
      ).rejects.toThrow('repeated a cursor');
    }),
  );
});
