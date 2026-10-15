import { expect, test } from './coverage-fixture';

const branch = {
  id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
  name: 'Central Rama II',
  address: '160 Rama II Road, Bangkok 10150',
  opensAt: '09:00',
  closesAt: '19:00',
};

const clientUser = {
  subject: 'visual-client-subject',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Local',
  lastName: 'Client',
  roles: ['CLIENT'],
  clientProfile: { phone: '+66812345678', completed: true },
  onboardingRequired: false,
};

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-15T05:00:00Z'));
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({ json: clientUser }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [branch] } }),
  );
});

test('booking confirmation stays readable on desktop and mobile', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name !== 'chromium' &&
      testInfo.project.name !== 'mobile-chromium',
    'visual baselines are maintained for desktop and mobile Chromium only',
  );

  await page.route('**/api/v1/reservations/availability?*', (route) =>
    route.fulfill({
      json: {
        tables: [
          { id: 3, name: 'Table 3', capacity: 4, available: true },
          { id: 6, name: 'Table 6', capacity: 6, available: true },
          { id: 10, name: 'Table 10', capacity: 8, available: false },
        ],
      },
    }),
  );
  await page.goto('/reservations/new?branch=Central%20Rama%20II');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: /Table 6/ }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  const confirmation = page.locator('.confirmation-list');
  await expect(confirmation).toContainText('Local Client');
  await expect(confirmation).toContainText('Central Rama II');
  await expect(confirmation).toContainText('Table 6');
  await expect(confirmation).toContainText('5 guests');

  await expect(page).toHaveScreenshot('booking-confirmation.png', {
    fullPage: true,
  });
});

test('client checkout shows the same server-backed receipt at a frozen time', async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name !== 'chromium' &&
      testInfo.project.name !== 'mobile-chromium',
    'visual baselines are maintained for desktop and mobile Chromium only',
  );

  await page.route('**/api/v1/me/active-session', (route) =>
    route.fulfill({
      json: {
        reservationId: 'visual-session',
        locationName: 'Central Rama II',
        tableName: 'Table 8',
        startedAt: '2026-10-15T03:00:00Z',
        partySize: 4,
        ratePerHour: 150,
        accruedAmount: 300,
        currency: 'THB',
      },
    }),
  );

  await page.goto('/sessions/checkout');
  await expect(
    page.getByRole('heading', { name: 'Settle your session' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Session Bill' }),
  ).toBeVisible();
  await expect(page.getByText('Table 8')).toBeVisible();
  await expect(page.locator('.receipt-card')).toContainText('300.00');

  await expect(page).toHaveScreenshot('client-checkout.png', {
    fullPage: true,
  });
});
