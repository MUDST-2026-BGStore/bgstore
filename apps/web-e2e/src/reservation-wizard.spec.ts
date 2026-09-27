import { expect, test, type Page } from '@playwright/test';

const staffUser = {
  subject: 'staff-1',
  username: 'staff@example.test',
  email: 'staff@example.test',
  firstName: 'Local',
  lastName: 'Staff',
  roles: ['STAFF'],
  onboardingRequired: false,
};

const clientUser = {
  subject: 'client-1',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Somchai',
  lastName: 'Jaidee',
  roles: ['CLIENT'],
  onboardingRequired: false,
  clientProfile: { phone: '0812345678' },
};

const branches = {
  items: [
    {
      id: 'branch-1',
      name: 'Central Rama II',
      address: 'Rama II',
      opensAt: '09:00',
      closesAt: '19:00',
    },
    {
      id: 'branch-2',
      name: 'Big C Ratchadamri',
      address: 'Ratchadamri',
      opensAt: '10:00',
      closesAt: '20:00',
    },
  ],
};

const clients = {
  items: [
    {
      subject: 'client-1',
      displayName: 'Somchai J.',
      firstName: 'Somchai',
      lastName: 'J.',
      phone: '0812345678',
    },
  ],
};

const tables = {
  tables: [
    { id: 1, name: 'Table 1', capacity: 4, available: true },
    { id: 2, name: 'Table 2', capacity: 6, available: false },
    { id: 3, name: 'Table 3', capacity: 8, available: true },
  ],
};

async function mockUser(
  page: Page,
  user: typeof staffUser | typeof clientUser,
) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(user),
    }),
  );
}

async function mockBranches(page: Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
}

async function mockClients(page: Page) {
  await page.route('**/api/v1/clients', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(clients),
    }),
  );
}

/**
 * Step 2 fires the availability query on mount. Recording the URLs lets a test
 * assert the branch name and the selected day reach the API without hardcoding
 * the Bangkok-relative "tomorrow" date the picker starts on.
 */
function recordAvailability(page: Page) {
  const urls: string[] = [];
  void page.route('**/api/v1/reservations/availability**', (route) => {
    urls.push(route.request().url());
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(tables),
    });
  });
  return urls;
}

test('staff picks a registered client and lands on the time and party step', async ({
  page,
}) => {
  await mockUser(page, staffUser);
  await mockBranches(page);
  await mockClients(page);

  await page.goto('/staff/reservations/new');

  // Step 1 of 4 is the client step, with the staff-only lookup field.
  await expect(page.getByText('Client', { exact: true })).toBeVisible();
  const search = page.locator('#reservation-client-search');
  await expect(search).toBeVisible();
  await expect(search).toHaveAttribute(
    'placeholder',
    'Search by name or phone number',
  );
  await expect(page.getByText('Find a client')).toBeVisible();
  await expect(page.locator('#reservation-branch')).toHaveValue(
    'Central Rama II',
  );

  // Typing the exact display name auto-fills the contact fields.
  await search.fill('Somchai J.');
  await expect(page.locator('#reservation-first-name')).toHaveValue('Somchai');
  await expect(page.locator('#reservation-last-name')).toHaveValue('J.');
  await expect(page.locator('#reservation-phone')).toHaveValue('0812345678');

  // The test changes the branch, then continues without touching anything else.
  await page.locator('#reservation-branch').selectOption('Big C Ratchadamri');

  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await expect(page.getByText('Time & Party', { exact: true })).toBeVisible();
  // Step 2 shows the client's auto-filled identity in its own step context.
  await expect(page.getByRole('button', { name: 'Date' })).toBeVisible();
});

test('client mode prefills the booking form and sends the chosen date to availability', async ({
  page,
}) => {
  const availabilityUrls = recordAvailability(page);
  await mockUser(page, clientUser);
  await mockBranches(page);

  await page.goto('/reservations/new');

  // No client search in client mode; the signed-in identity is prefilled.
  await expect(page.locator('#reservation-client-search')).toHaveCount(0);
  await expect(page.locator('#reservation-first-name')).toHaveValue('Somchai');
  await expect(page.locator('#reservation-last-name')).toHaveValue('Jaidee');
  await expect(page.locator('#reservation-branch')).toHaveValue(
    'Central Rama II',
  );

  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Time & Party', { exact: true })).toBeVisible();

  // Availability fires once step 2 mounts, with the branch sent by name.
  await expect.poll(() => availabilityUrls.length).toBe(1);
  expect(availabilityUrls[0]).toContain('branch=Central%20Rama%20II');
  expect(availabilityUrls[0]).toContain('partySize=5');
  expect(availabilityUrls[0]).toContain('startTime=09%3A00');
  expect(availabilityUrls[0]).toContain('endTime=12%3A00');

  // Pick a day that is actually bookable, then confirm it reaches the API.
  await page.getByRole('button', { name: 'Date' }).click();
  const dialog = page.getByRole('dialog', {
    name: 'Choose a reservation date',
  });
  await expect(dialog).toBeVisible();
  const day = dialog.locator('.calendar-day:not([disabled])').nth(6);
  const pickedDate = await day.getAttribute('data-date');
  expect(pickedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  await day.click();
  await expect(dialog).toHaveCount(0);

  await expect
    .poll(() =>
      availabilityUrls.some((url) => url.includes(`date=${pickedDate}`)),
    )
    .toBe(true);
});

test('asks a client without a phone on file for one', async ({ page }) => {
  // A client whose profile carries no phone must supply one before continuing.
  await mockUser(page, {
    ...clientUser,
    clientProfile: undefined,
  } as unknown as typeof clientUser);
  await mockBranches(page);

  await page.goto('/reservations/new');

  await expect(page.locator('#reservation-phone')).toHaveValue('');
  await expect(page.getByTestId('phone-required-hint')).toBeVisible();
  await expect(page.getByTestId('phone-required-hint')).toContainText(
    'Add a phone number so staff can reach you at check-in.',
  );
  await expect(
    page.getByRole('button', { name: 'Next', exact: true }),
  ).toBeDisabled();
});

test('reports a failed availability load', async ({ page }) => {
  await page.route('**/api/v1/reservations/availability**', (route) =>
    route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 500, title: 'Internal Server Error' }),
    }),
  );
  await mockUser(page, clientUser);
  await mockBranches(page);

  await page.goto('/reservations/new');

  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Time & Party', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await expect(
    page.getByRole('heading', { name: 'Select table' }),
  ).toBeVisible();
  await expect(page.getByRole('alert')).toContainText(
    'We could not load table availability. Please try again.',
  );
});

test('rejects an out-of-order time range and continues once it is valid', async ({
  page,
}) => {
  const availabilityUrls = recordAvailability(page);
  await mockUser(page, staffUser);
  await mockBranches(page);
  await mockClients(page);

  await page.goto('/staff/reservations/new');
  await page.locator('#reservation-client-search').fill('Somchai J.');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Time & Party', { exact: true })).toBeVisible();

  const next = page.getByRole('button', { name: 'Next', exact: true });
  const selects = page.locator('.step-form select');

  // Defaults are 09:00–12:00 and a party of 5, so the range is valid.
  await expect(selects.nth(0)).toHaveValue('09:00');
  await expect(selects.nth(1)).toHaveValue('12:00');
  await expect(
    page.getByRole('button', { name: 'Decrease party size' }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Increase party size' }),
  ).toBeVisible();
  await expect(next).toBeEnabled();

  // An end time at or before the start time is rejected.
  await selects.nth(1).selectOption('09:00');
  await expect(
    page.getByText('End time must be later than start time.'),
  ).toBeVisible();
  await expect(next).toBeDisabled();

  await selects.nth(1).selectOption('14:00');
  await expect(
    page.getByText('End time must be later than start time.'),
  ).toHaveCount(0);
  await expect(next).toBeEnabled();

  // The party-size button picker drives the availability query.
  await page
    .locator('.party-options')
    .getByRole('button', { name: '3', exact: true })
    .click();
  await expect
    .poll(() => availabilityUrls.some((url) => url.includes('partySize=3')))
    .toBe(true);

  await next.click();

  await expect(
    page.getByRole('heading', { name: 'Select table' }),
  ).toBeVisible();
  await expect
    .poll(() =>
      availabilityUrls.some((url) => url.includes('startTime=09%3A00')),
    )
    .toBe(true);
  await expect
    .poll(() => availabilityUrls.some((url) => url.includes('endTime=14%3A00')))
    .toBe(true);
});

test('selects an available table and confirms the reservation end to end', async ({
  page,
}) => {
  const availabilityUrls = recordAvailability(page);
  await mockUser(page, staffUser);
  await mockBranches(page);
  await mockClients(page);

  let createBody: Record<string, unknown> | null = null;
  await page.route('**/api/v1/reservations', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.continue();
    }
    createBody = route.request().postDataJSON() as Record<string, unknown>;
    return route.fulfill({
      contentType: 'application/json',
      status: 201,
      body: JSON.stringify({
        id: 'reservation-1',
        branch: 'Big C Ratchadamri',
        date: '2026-05-01',
        startTime: '09:00',
        endTime: '12:00',
        partySize: 3,
        status: 'CONFIRMED',
        tableId: 3,
      }),
    });
  });

  await page.goto('/staff/reservations/new');

  // Step 1: pick the client and branch 2.
  await page.locator('#reservation-client-search').fill('Somchai J.');
  await page.locator('#reservation-branch').selectOption('Big C Ratchadamri');
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  // Step 2: choose a real day, end 14:00, party of 3.
  await page.getByRole('button', { name: 'Date' }).click();
  const dialog = page.getByRole('dialog', {
    name: 'Choose a reservation date',
  });
  const day = dialog.locator('.calendar-day:not([disabled])').nth(6);
  const pickedDate = await day.getAttribute('data-date');
  await day.click();
  await expect(dialog).toHaveCount(0);

  const selects = page.locator('.step-form select');
  await selects.nth(1).selectOption('14:00');
  await page
    .locator('.party-options')
    .getByRole('button', { name: '3', exact: true })
    .click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  // Step 3: the reserved table is disabled, the free tables are selectable.
  await expect(
    page.getByRole('heading', { name: 'Select table' }),
  ).toBeVisible();
  await expect(page.getByText('Table 1')).toBeVisible();
  await expect(page.getByText('4 seats')).toBeVisible();
  const reservedTable = page.getByRole('button', { name: /Table 2/ });
  await expect(reservedTable).toBeDisabled();
  await expect(reservedTable).toContainText('Reserved');
  const tableThree = page.getByRole('button', { name: /Table 3/ });
  await expect(tableThree).toBeEnabled();

  await tableThree.click();
  await expect(tableThree).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  // Step 4: the summary reflects the draft.
  await expect(page.getByText('Table 3')).toBeVisible();
  await expect(page.getByText('Somchai J.', { exact: true })).toBeVisible();
  await expect(page.getByText('0812345678')).toBeVisible();
  await expect(page.getByText('Big C Ratchadamri')).toBeVisible();
  await expect(page.getByText('3 guests')).toBeVisible();
  await expect(page.getByText('09:00', { exact: true })).toBeVisible();
  await expect(page.getByText('14:00', { exact: true })).toBeVisible();
  expect(availabilityUrls.length).toBeGreaterThan(0);

  await page.getByRole('button', { name: 'Confirm', exact: true }).click();

  const success = page.locator('[role="status"]');
  await expect(success).toContainText('Reservation details ready');
  expect(createBody).toMatchObject({
    branch: 'Big C Ratchadamri',
    date: pickedDate,
    startTime: '09:00',
    endTime: '14:00',
    partySize: 3,
    tableId: 3,
    customerName: 'Somchai J.',
    phoneNumber: '0812345678',
    clientSubject: 'client-1',
  });

  // The review button returns to a usable wizard.
  await page.getByRole('button', { name: 'Review reservation' }).click();
  await expect(success).toHaveCount(0);
  await expect(page.getByText('Table 3')).toBeVisible();
});

test('surfaces a failed confirmation as an alert', async ({ page }) => {
  recordAvailability(page);
  await mockUser(page, staffUser);
  await mockBranches(page);
  await mockClients(page);
  await page.route('**/api/v1/reservations', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.continue();
    }
    return route.fulfill({
      status: 409,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        status: 409,
        title: 'Conflict',
        detail: 'That table was just reserved.',
      }),
    });
  });

  await page.goto('/staff/reservations/new');
  await page.locator('#reservation-client-search').fill('Somchai J.');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Select table' }),
  ).toBeVisible();
  await page.getByRole('button', { name: /Table 3/ }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();

  await expect(page.getByRole('alert')).toContainText(
    'That table was just reserved.',
  );
  await expect(page.getByText('Reservation details ready')).toHaveCount(0);
});

test('shows the empty state when a branch has no tables', async ({ page }) => {
  await page.route('**/api/v1/reservations/availability**', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ tables: [] }),
    }),
  );
  await mockUser(page, staffUser);
  await mockBranches(page);
  await mockClients(page);

  await page.goto('/staff/reservations/new');
  await page.locator('#reservation-client-search').fill('Somchai J.');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await expect(
    page.getByRole('heading', { name: 'Select table' }),
  ).toBeVisible();
  await expect(page.getByTestId('tables-empty')).toHaveText(
    'No tables are available for this branch yet.',
  );
  await expect(
    page.getByRole('button', { name: 'Next', exact: true }),
  ).toBeDisabled();
});
