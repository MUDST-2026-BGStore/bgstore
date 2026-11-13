import { expect, test } from '@playwright/test';

test('shows branch pantry availability and refreshes it after an adjustment', async ({
  page,
}) => {
  const stock: Record<string, number> = { siam: 5, silom: 2 };
  const adjustments: Array<{ branch: string; delta: number; reason: string }> =
    [];
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (!path.startsWith('/api/')) return route.fallback();
    if (path === '/api/auth/session') {
      return route.fulfill({
        json: {
          authenticated: true,
          profile: {
            subject: 'staff',
            username: 'Staff',
            roles: ['staff'],
            emailVerified: true,
          },
        },
      });
    }
    if (path === '/api/v1/me') {
      return route.fulfill({
        json: { id: 'staff', preferences: { preferredLocale: 'en-US' } },
      });
    }
    if (path === '/api/v1/branches') {
      return route.fulfill({
        json: {
          items: [
            { id: 'siam', name: 'Siam' },
            { id: 'silom', name: 'Silom' },
          ],
        },
      });
    }
    if (path === '/api/auth/csrf') {
      return route.fulfill({
        json: { headerName: 'X-CSRF-Token', token: 'test' },
      });
    }
    const menu = path.match(/^\/api\/v1\/branches\/(siam|silom)\/menu$/);
    if (menu) {
      return route.fulfill({
        json: {
          branchId: menu[1],
          items: [
            {
              item: {
                id: 'cola',
                sku: 'COLA',
                category: 'BEVERAGES',
                names: { 'en-US': 'Cola' },
                price: { amount: 40, currency: 'THB' },
                popular: false,
              },
              available: stock[menu[1]] ?? 0,
            },
          ],
        },
      });
    }
    const adjustment = path.match(
      /^\/api\/v1\/branches\/(siam|silom)\/menu\/cola\/stock-adjustments$/,
    );
    if (adjustment && route.request().method() === 'POST') {
      expect(route.request().headers()['x-csrf-token']).toBe('test');
      const body = route.request().postDataJSON() as {
        delta: number;
        reason: string;
      };
      adjustments.push({ branch: adjustment[1], ...body });
      stock[adjustment[1]] += body.delta;
      return route.fulfill({
        status: 201,
        json: {
          onHand: stock[adjustment[1]],
          held: 0,
          available: stock[adjustment[1]],
        },
      });
    }
    return route.fulfill({ json: { items: [] } });
  });

  await page.goto('/workspace/management');
  await page.getByRole('button', { name: 'Review inventory' }).click();
  const pantry = page.getByRole('region', { name: 'Food & drinks' });
  await expect(pantry.getByRole('row', { name: /Cola COLA 5/ })).toBeVisible();
  await pantry.getByRole('button', { name: 'Adjust stock' }).click();
  const dialog = page.getByRole('dialog', { name: 'Adjust Cola' });
  await dialog.getByLabel('Change in units').fill('3');
  await dialog.getByLabel('Reason').fill('Delivery');
  await dialog.getByRole('button', { name: 'Save adjustment' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(pantry.getByRole('row', { name: /Cola COLA 8/ })).toBeVisible();
  expect(adjustments).toEqual([
    { branch: 'siam', delta: 3, reason: 'Delivery' },
  ]);

  await page
    .getByRole('combobox', { name: 'Branch', exact: true })
    .selectOption('silom');
  await expect(pantry.getByRole('row', { name: /Cola COLA 2/ })).toBeVisible();
});
