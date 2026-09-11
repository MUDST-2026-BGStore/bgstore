import { expect, test, type Page } from '@playwright/test';

const kittensId = '1a1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e12';

const kittens = {
  id: kittensId,
  title: { en: 'Exploding Kittens', th: 'เหมียวระเบิด' },
  category: 'card',
  minPlayers: 2,
  maxPlayers: 5,
  playTimeMinutes: 15,
  coverImageUrl: 'https://cdn.example.test/ek-box.png',
  branchCount: 1,
  copies: 3,
  available: 3,
  status: 'available',
};

/** A 1×1 PNG, so photo requests resolve without leaving the test. */
const pixel = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
);

async function signInAsGuest(page: Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        subject: 'client-subject',
        username: 'client@example.test',
        email: 'client@example.test',
        firstName: 'Local',
        lastName: 'Client',
        roles: ['CLIENT'],
        clientProfile: { phone: '+66812345678', completed: true },
        onboardingRequired: false,
      }),
    }),
  );
  await page.route('https://cdn.example.test/**', (route) =>
    route.fulfill({ contentType: 'image/png', body: pixel }),
  );
  await page.route('**/api/v1/games?*', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        items: [kittens],
        page: { number: 0, size: 24, totalElements: 1, totalPages: 1 },
        stats: { titles: 1, availableNow: 3, inUse: 0 },
      }),
    }),
  );
  await page.route(`**/api/v1/games/${kittensId}`, (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        ...kittens,
        description: null,
        difficulty: 'Easy',
        tags: ['Popular'],
        imageUrls: ['https://cdn.example.test/ek-box.png'],
        guide: {
          goal: { en: 'Be the last player left standing.' },
          steps: [{ title: { en: 'Deal the cards' } }],
        },
        lifecycle: 'active',
        addedAt: '2025-01-12T09:00:00Z',
        totalCopies: 3,
        stock: [
          {
            branchId: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
            branchName: 'Central Rama II',
            copies: 3,
            available: 3,
            inUse: 0,
            status: 'available',
          },
        ],
      }),
    }),
  );
}

test('a guest browses the catalogue and opens a game to read how to play', async ({
  page,
}) => {
  await signInAsGuest(page);

  await page.goto('/games');

  await expect(page.getByRole('heading', { name: 'All games' })).toBeVisible();
  await expect(page.getByTestId('catalogue-count')).toHaveText('1 game');

  await page.getByRole('link', { name: /Exploding Kittens/ }).click();

  await expect(
    page.getByRole('heading', { level: 1, name: 'Exploding Kittens' }),
  ).toBeVisible();
  await expect(page.getByTestId('catalogue-availability')).toHaveText(
    '3 sets free',
  );
  await expect(
    page.getByRole('heading', { name: 'How to play' }),
  ).toBeVisible();
  await expect(page.getByTestId('catalogue-step')).toHaveText(/Deal the cards/);

  await page.getByRole('button', { name: 'Branches with this game' }).click();
  await expect(page.getByTestId('catalogue-branches')).toContainText(
    'Central Rama II',
  );
});
