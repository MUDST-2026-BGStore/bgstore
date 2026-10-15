import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from './coverage-fixture';

const tables = [
  { id: 3, name: 'Table 3', capacity: 4, available: true },
  { id: 6, name: 'Table 6', capacity: 6, available: true },
  { id: 10, name: 'Table 10', capacity: 8, available: false },
];

test.beforeEach(async ({ page }) => {
  // Freeze the calendar, not timers or network requests, so the past-date
  // boundary is independent of the day the suite runs.
  await page.clock.setFixedTime(new Date('2026-10-15T05:00:00Z'));
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      json: {
        subject: 'client-journey',
        username: 'client@example.test',
        email: 'client@example.test',
        firstName: 'Local',
        lastName: 'Client',
        roles: ['CLIENT'],
        onboardingRequired: false,
        clientProfile: { phone: '+66812345678', completed: true },
      },
    }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      json: { items: [{ id: 'central', name: 'Central Rama II' }] },
    }),
  );
  await page.route('**/api/v1/reservations/availability?*', (route) =>
    route.fulfill({ json: { tables } }),
  );
});

async function chooseTable(page: Page) {
  await page.goto('/reservations/new?branch=Central%20Rama%20II');
  await expect(page.locator('#reservation-first-name')).toHaveValue('Local');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByRole('button', { name: /Table 3/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /Table 10/ })).toBeDisabled();
  await page.getByRole('button', { name: /Table 6/ }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.locator('.confirmation-list')).toContainText('5 guests');
}

test('client books a fitting table, gets staff help, and requests settlement', async ({
  page,
}) => {
  let booking: Record<string, unknown> | undefined;
  await page.route('**/api/v1/reservations', async (route) => {
    booking = route.request().postDataJSON() as Record<string, unknown>;
    await route.fulfill({ status: 201, json: { id: 'journey-reservation' } });
  });
  await chooseTable(page);
  const audit = await new AxeBuilder({ page })
    .include('.reservation-card')
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  expect(audit.violations).toEqual([]);
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Reservation details ready' }),
  ).toBeVisible();
  expect(booking).toMatchObject({
    branch: 'Central Rama II',
    date: '2026-10-16',
    startTime: '09:00',
    endTime: '12:00',
    partySize: 5,
    tableId: 6,
    customerName: 'Local Client',
    phoneNumber: '+66812345678',
  });

  // Check-in is staff-owned. This fixture is the resulting API state, not a
  // pretend client-side payment: clients can only request staff settlement.
  await page.route('**/api/v1/me/active-session', (route) =>
    route.fulfill({
      json: {
        reservationId: 'journey-reservation',
        locationName: 'Central Rama II',
        tableName: 'Table 6',
        startedAt: '2026-10-15T04:00:00Z',
        partySize: 5,
        ratePerHour: 50,
        accruedAmount: 125,
        currency: 'THB',
      },
    }),
  );
  await page.route(
    '**/api/v1/reservations/journey-reservation/assistance',
    (route) =>
      route.fulfill({
        status: 201,
        json: { requestId: 'help-1', status: 'Pending' },
      }),
  );
  await page.goto('/sessions/active');
  await expect(page.getByText('Table 6')).toBeVisible();
  await page.getByRole('button', { name: 'Call Staff', exact: true }).click();
  await expect(page.getByRole('status')).toContainText(
    'A staff member has been asked to come to your table.',
  );
  await page.goto('/sessions/checkout');
  await expect(page.getByText(/125\.00/).first()).toBeVisible();
  const checkoutAudit = await new AxeBuilder({ page })
    .include('.checkout-page')
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  expect(checkoutAudit.violations).toEqual([]);
  await page.getByRole('button', { name: 'Request settlement' }).click();
  await expect(
    page.getByRole('heading', { name: 'Settlement requested' }),
  ).toBeVisible();
});

test('keeps the booking draft after a conflict and allows a fresh selection', async ({
  page,
}) => {
  let available = true;
  await page.route('**/api/v1/reservations/availability?*', (route) =>
    route.fulfill({
      json: {
        tables: tables.map((table) =>
          table.id === 6 ? { ...table, available } : table,
        ),
      },
    }),
  );
  await page.route('**/api/v1/reservations', (route) => {
    available = false;
    return route.fulfill({
      status: 409,
      json: {
        status: 409,
        detail: 'This table was just reserved. Choose another table or time.',
      },
    });
  });
  await chooseTable(page);
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText(
    'This table was just reserved',
  );
  await expect(page.locator('.confirmation-list')).toContainText(
    'Local Client',
  );
  await expect(
    page.getByRole('heading', { name: 'Reservation details ready' }),
  ).toHaveCount(0);
  // Reloading availability through a different time must not offer the
  // stale table. The draft survives moving back through the wizard.
  await page.getByRole('button', { name: 'Back', exact: true }).click();
  await page.getByRole('button', { name: 'Back', exact: true }).click();
  await page.getByLabel('Start time').selectOption('10:00');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByRole('button', { name: /Table 6/ })).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Next', exact: true }),
  ).toBeDisabled();
});

test('prevents today, past dates and invalid time ranges before table selection', async ({
  page,
}) => {
  await page.goto('/reservations/new');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Date', exact: true }).click();
  await expect(page.locator('[data-date="2026-10-14"]')).toBeDisabled();
  await expect(page.locator('[data-date="2026-10-15"]')).toBeDisabled();
  await page.locator('[data-date="2026-10-16"]').click();
  await page.getByLabel('Start time').selectOption('13:00');
  await page.getByLabel('End time').selectOption('12:00');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Next', exact: true }),
  ).toBeDisabled();
});
