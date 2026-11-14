import { expect, test } from '@playwright/test';

test('rejected early check-in keeps the booking unchanged after reload', async ({
  page,
}) => {
  let checkInRequests = 0;
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.fallback();
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
    if (path === '/api/auth/csrf') {
      return route.fulfill({
        json: { headerName: 'X-CSRF-Token', token: 'test' },
      });
    }
    if (path.endsWith('/reservations/res-thm-9812/check-in')) {
      checkInRequests += 1;
      return route.fulfill({ status: 409, json: { message: 'Too early' } });
    }
    return route.fulfill({ json: { items: [] } });
  });

  await page.goto('/workspace/management');
  const activeSessions = page
    .getByRole('region', { name: 'Branch summary' })
    .getByRole('button', { name: /^Active sessions/ });
  await expect(activeSessions).toContainText('2');
  await page.getByRole('button', { name: 'Bookings', exact: true }).click();
  const booking = page.getByRole('row').filter({ hasText: 'Table 3' });
  await expect(booking).toContainText('Confirmed');
  await booking.getByRole('button', { name: 'Check in & seat' }).click();
  await expect(
    page.getByText('Check-in failed. Refresh and retry.'),
  ).toBeVisible();
  await expect(booking).toContainText('Confirmed');
  expect(checkInRequests).toBe(1);

  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await expect(activeSessions).toContainText('2');
  await page.reload();
  await page.getByRole('button', { name: 'Bookings', exact: true }).click();
  await expect(
    page.getByRole('row').filter({ hasText: 'Table 3' }),
  ).toContainText('Confirmed');
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await expect(activeSessions).toContainText('2');

  // The old code could persist an impossible early check-in in browser storage.
  await page.evaluate(() => {
    const key = 'theparlor.account:workspace-sample-reservations';
    const reservations = JSON.parse(localStorage.getItem(key) || '[]');
    const record = reservations.find(
      (item: { id: string }) => item.id === 'res-thm-9812',
    );
    record.status = 'CHECKED_IN';
    record.checkedInAt = new Date(
      Date.parse(record.startsAt) - 60_000,
    ).toISOString();
    localStorage.setItem(key, JSON.stringify(reservations));
  });
  await page.reload();
  await page.getByRole('button', { name: 'Bookings', exact: true }).click();
  await expect(
    page.getByRole('row').filter({ hasText: 'Table 3' }),
  ).toContainText('Confirmed');
  const repaired = await page.evaluate(() => {
    const reservations = JSON.parse(
      localStorage.getItem('theparlor.account:workspace-sample-reservations') ||
        '[]',
    );
    return reservations.find(
      (item: { id: string }) => item.id === 'res-thm-9812',
    );
  });
  expect(repaired.status).toBe('CONFIRMED');
  expect(repaired.checkedInAt).toBeUndefined();
});
