import { expect, test } from '@playwright/test';

const branches = {
  items: [
    {
      id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
      name: 'Central Rama II',
      address: '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
      opensAt: '09:00',
      closesAt: '19:00',
    },
    {
      id: '3f0d7d5a-9a2b-4a71-8f0e-000000000002',
      name: 'Big C Ratchadamri',
      address: 'ราชดำริ ปทุมวัน กรุงเทพฯ 10330',
      opensAt: '10:00',
      closesAt: '20:00',
    },
  ],
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
});

test('renders the editorial client home with real branch data', async ({
  page,
}) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'BGStore' })).toBeAttached();
  await expect(
    page.getByRole('heading', {
      name: 'Games, friends, and a table waiting.',
    }),
  ).toBeVisible();
  await expect(
    page.locator('nav[aria-label="Primary navigation"]'),
  ).toBeVisible();
  await expect(page.locator('.client-home-hero')).toBeVisible();
  await expect(page.getByTestId('home-branch')).toHaveCount(2);
  await expect(page.getByTestId('home-branch').first()).toContainText(
    '09:00–19:00',
  );

  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(page.getByTestId('hero-dot').nth(1)).toHaveAttribute(
    'aria-current',
    'true',
  );
});

test('keeps the client composition usable on a narrow viewport', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  await expect(
    page.getByRole('heading', {
      name: 'Games, friends, and a table waiting.',
    }),
  ).toBeVisible();
  await expect(page.getByTestId('home-branch').first()).toBeVisible();
  await expect(page.locator('.client-nav-pill')).toBeHidden();
});

test('keeps the client branch directory out of the staff table workflow', async ({
  page,
}) => {
  await page.goto('/branches');

  await expect(
    page.locator('nav[aria-label="Primary navigation"]'),
  ).toBeVisible();
  await expect(
    page.locator('nav[aria-label="Staff quick navigation"]'),
  ).toHaveCount(0);
  await expect(page.locator('a[href="/tables"]')).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: 'Reservations coming soon' }),
  ).toBeDisabled();
});
