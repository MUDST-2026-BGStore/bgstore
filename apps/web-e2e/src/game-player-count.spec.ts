import { expect, test } from '@playwright/test';

test('Taboo player count matches between customer catalog and staff library', async ({
  page,
}) => {
  const response = await page.request.get('/api/v1/games?q=Taboo');
  expect(response.ok()).toBe(true);
  const catalog = await response.json();
  const taboo = catalog.items.find(
    (game: { name: string }) => game.name === 'Taboo',
  );
  expect(taboo).toBeTruthy();
  expect([taboo.minPlayers, taboo.maxPlayers]).toEqual([4, 20]);

  await page.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.fallback();
    if (path === '/api/v1/games') return route.fallback();
    if (path === '/api/auth/session') {
      return route.fulfill({
        json: {
          authenticated: true,
          profile: {
            subject: 'operator',
            username: 'Operator',
            roles: ['staff'],
            emailVerified: true,
          },
        },
      });
    }
    if (path === '/api/v1/me') {
      return route.fulfill({
        json: { id: 'operator', preferences: { preferredLocale: 'en-US' } },
      });
    }
    if (path === '/api/v1/branches') {
      return route.fulfill({ json: { items: [{ id: 'siam', name: 'Siam' }] } });
    }
    return route.fulfill({ json: { items: [] } });
  });

  await page.goto('/');
  await page.getByRole('searchbox', { name: 'Search games' }).fill('Taboo');
  const customerCard = page.locator('.game-card').filter({ hasText: 'Taboo' });
  await expect(customerCard).toContainText('4–20 players');

  await page.goto('/workspace/management');
  await page.getByRole('button', { name: 'Game library', exact: true }).click();
  await page
    .getByRole('searchbox', { name: 'Search game library' })
    .fill('Taboo');
  const staffRow = page
    .getByRole('region', { name: 'Game library table' })
    .getByRole('row')
    .filter({ hasText: 'Taboo' });
  await expect(staffRow).toContainText('4–20');
});
