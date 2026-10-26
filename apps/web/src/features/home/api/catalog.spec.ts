import { expect, it } from 'vitest';
import { collectPages, localizedDescription, imageUrl } from './catalog';
import { apiGames } from './catalog.fixtures';

it('does not publish a truncated catalog when a later page fails', async () => {
  await expect(
    collectPages(async (cursor) => {
      if (cursor) throw new Error('offline');
      return { items: [1], nextCursor: 'next' };
    }),
  ).rejects.toThrow('offline');
});

it('rejects repeated cursors instead of looping indefinitely', async () => {
  await expect(
    collectPages(async () => ({ items: [1], nextCursor: 'repeat' })),
  ).rejects.toThrow('repeated a cursor');
});

it('uses English only as a description fallback and never rewrites media from UUIDs', () => {
  expect(localizedDescription(apiGames[0], 'th-TH')).toBe(
    'จัดเรียงกระเบื้องบนกระดานของคุณ',
  );
  expect(localizedDescription(apiGames[1], 'th-TH')).toBe(
    'Find the demon in your town.',
  );
  expect(imageUrl(apiGames[0])).toBe('/images/games/azul.jpg');
  expect(imageUrl(apiGames[1])).toBeUndefined();
  expect(
    imageUrl({
      ...apiGames[0],
      images: [{ id: 'unsafe', url: 'javascript:alert(1)', sortOrder: 0 }],
    }),
  ).toBeUndefined();
});
