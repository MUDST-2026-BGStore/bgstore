import { expect, test, type Page } from './coverage-fixture';

// Covers the staff/manager workspace interactions the earlier journeys did
// not reach: floor overview states, session console error and validation
// paths, inventory filters and retire confirmation, game form validation,
// table form validation and view mode, the staff client picker, permissions
// error states, and role-gated profile links.

const managerUser = {
  subject: 'manager-subject',
  username: 'manager@example.test',
  email: 'manager@example.test',
  firstName: 'Mina',
  lastName: 'Manager',
  roles: ['MANAGER'],
  onboardingRequired: false,
};

const staffUser = {
  ...managerUser,
  subject: 'staff-subject',
  username: 'staff@example.test',
  roles: ['STAFF'],
};

const branch = {
  id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
  name: 'Central Rama II',
  address: '160 Rama II Road, Bangkok',
  opensAt: '09:00',
  closesAt: '19:00',
  phone: '+66 2 123 4567',
  status: 'ACTIVE',
};

async function mockManager(page: Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({ json: managerUser }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [branch] } }),
  );
}

async function mockStaff(page: Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({ json: staffUser }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [branch] } }),
  );
}

function reservation(id: string, status: string) {
  return {
    id,
    title: 'Table reservation',
    date: '16 Oct 2026',
    timeSlot: '13:00–15:00',
    partySize: 4,
    tableId: 12,
    tableName: 'Table 12',
    seats: 6,
    ratePerHour: 50,
    status,
    customerName: 'Local Client',
    phoneNumber: '+66812345678',
    checkInTime: '',
    actualCheckOut: '',
    overtimeMinutes: 0,
    totalPrice: 0,
    canCancel: true,
  };
}

function floorTable(id: number, name: string) {
  return {
    id,
    name,
    capacity: 6,
    shape: 'Round',
    status: 'Available',
    reservedSlots: [],
  };
}

test('floor overview retries after a failure and paginates through tables', async ({
  page,
}) => {
  await mockManager(page);
  let fail = true;
  await page.route('**/api/v1/floor-overview**', (route) => {
    const params = new URL(route.request().url()).searchParams;
    const pageNumber = Number(params.get('page') ?? '1');
    if (fail) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    if (pageNumber >= 2) {
      return route.fulfill({
        json: {
          counts: { available: 1, occupied: 0, reserved: 0 },
          items: [floorTable(6, 'Table 6')],
          total: 6,
          page: pageNumber,
          pageSize: 5,
          totalPages: 2,
        },
      });
    }
    return route.fulfill({
      json: {
        counts: { available: 1, occupied: 0, reserved: 0 },
        items: [1, 2, 3, 4, 5].map((n) => floorTable(n, `Table ${n}`)),
        total: 6,
        page: 1,
        pageSize: 5,
        totalPages: 2,
      },
    });
  });

  await page.goto('/');
  await expect(
    page.getByText('We could not load the floor overview.'),
  ).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('Showing 1–5 of 6')).toBeVisible();
  await page.getByRole('button', { name: 'Page 2' }).click();
  await expect(page.getByText('Showing 6–6 of 6')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Table 6' })).toBeVisible();
});

test('floor overview shows an empty state and resets to the first page when filtered', async ({
  page,
}) => {
  await mockManager(page);
  const pages: string[] = [];
  await page.route('**/api/v1/floor-overview**', (route) => {
    const params = new URL(route.request().url()).searchParams;
    pages.push(
      `page=${params.get('page') ?? ''} status=${params.get('status') ?? ''} search=${params.get('search') ?? ''}`,
    );
    if (params.get('status') || params.get('search')) {
      return route.fulfill({
        json: {
          counts: { available: 0, occupied: 0, reserved: 0 },
          items: [],
          total: 0,
          page: Number(params.get('page') ?? '1'),
          pageSize: 5,
          totalPages: 0,
        },
      });
    }
    if (params.get('page') === '2') {
      return route.fulfill({
        json: {
          counts: { available: 1, occupied: 0, reserved: 0 },
          items: [floorTable(6, 'Table 6')],
          total: 6,
          page: 2,
          pageSize: 5,
          totalPages: 2,
        },
      });
    }
    return route.fulfill({
      json: {
        counts: { available: 0, occupied: 0, reserved: 0 },
        items: [1, 2, 3, 4, 5].map((n) => floorTable(n, `Table ${n}`)),
        total: 6,
        page: 1,
        pageSize: 5,
        totalPages: 2,
      },
    });
  });

  await page.goto('/');
  await expect(page.getByText('Showing 1–5 of 6')).toBeVisible();
  await page.getByRole('button', { name: 'Page 2' }).click();
  await expect(page.getByText('Showing 6–6 of 6')).toBeVisible();

  const statusValue = await page
    .locator('#floor-status')
    .locator('option')
    .nth(1)
    .getAttribute('value');
  await page.locator('#floor-status').selectOption({ index: 1 });
  await expect
    .poll(() => pages.at(-1) ?? '')
    .toContain(`status=${statusValue}`);
  await expect.poll(() => pages.at(-1) ?? '').toContain('page=1');
  await expect(page.getByText('No tables match your filters.')).toBeVisible();

  await page.locator('#floor-search').fill('Table 9');
  await expect.poll(() => pages.at(-1) ?? '').toContain('search=Table 9');
});

test('session console recovers from load failures and reports check-in failures', async ({
  page,
}) => {
  await mockManager(page);
  let fail = true;
  await page.route('**/api/v1/sessions', (route) => {
    if (fail) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    return route.fulfill({ json: { items: [reservation('r-1', 'Reserved')] } });
  });
  await page.route('**/api/v1/reservations/r-1/check-in', (route) =>
    route.fulfill({ status: 409, json: { status: 409 } }),
  );

  await page.goto('/staff/sessions');
  await expect(page.getByText('Sessions could not be loaded.')).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('button', { name: 'Check in' })).toBeVisible();

  await page.getByRole('button', { name: 'Check in' }).click();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'That guest could not be checked in.' }),
  ).toBeVisible();
});

test('session console validates amounts and surfaces gateway declines', async ({
  page,
}) => {
  await mockManager(page);
  await page.route('**/api/v1/sessions', (route) =>
    route.fulfill({ json: { items: [reservation('r-1', 'CheckedIn')] } }),
  );
  await page.route('**/api/v1/reservations/r-1/check-out', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'insufficient_funds', title: 'Declined' }),
    }),
  );

  await page.goto('/staff/sessions');
  await page.getByRole('button', { name: 'Check out' }).click();
  const dialog = page.getByRole('dialog', { name: 'Close the session' });

  await dialog.getByRole('button', { name: 'Close session' }).click();
  await expect(dialog.getByText('Enter the confirmed amount.')).toBeVisible();

  await dialog.locator('#checkout-amount').fill('0');
  await dialog.getByRole('button', { name: 'Close session' }).click();
  await expect(
    dialog.getByText('A settled amount must be greater than 0.'),
  ).toBeVisible();

  await dialog.locator('#checkout-amount').fill('350');
  await dialog.locator('#checkout-method').selectOption({ label: 'Card' });
  await expect(
    dialog.getByRole('button', { name: 'Close session' }),
  ).toBeDisabled();

  await dialog.locator('#checkout-method').selectOption({ label: 'Cash' });
  await dialog.getByRole('button', { name: 'Close session' }).click();
  await expect(
    dialog.getByText(
      'The card network declined the charge: insufficient funds.',
    ),
  ).toBeVisible();
});

test('game inventory filters, pages, and confirms retirement', async ({
  page,
}) => {
  await mockManager(page);
  const seen: string[] = [];
  let deleteCalls = 0;
  const row = (id: string, title: string, status = 'available') => ({
    id,
    title: { en: title, th: null },
    category: 'card',
    minPlayers: 2,
    maxPlayers: 4,
    playTimeMinutes: 30,
    coverImageUrl: null,
    branchName: branch.name,
    branchCount: 1,
    copies: 3,
    available: 2,
    status,
  });
  await page.route('**/api/v1/games?*', (route) => {
    const params = new URL(route.request().url()).searchParams;
    seen.push(
      `page=${params.get('page') ?? ''} branch=${params.get('branchId') ?? ''} category=${params.get('category') ?? ''} status=${params.get('status') ?? ''} search=${params.get('search') ?? ''}`,
    );
    const pageNumber = Number(params.get('page') ?? '0');
    if (pageNumber >= 1) {
      return route.fulfill({
        json: {
          items: [row('g-6', 'Mysterium')],
          page: {
            number: pageNumber,
            size: 20,
            totalElements: 21,
            totalPages: 2,
          },
          stats: { titles: 21, availableNow: 12, inUse: 3 },
        },
      });
    }
    return route.fulfill({
      json: {
        items: [
          row('g-1', 'Sushi Go'),
          row('g-2', 'Love Letter', 'allCopiesOut'),
        ],
        page: {
          number: pageNumber,
          size: 20,
          totalElements: 21,
          totalPages: 2,
        },
        stats: { titles: 21, availableNow: 12, inUse: 3 },
      },
    });
  });
  await page.route('**/api/v1/games/g-*', (route) => {
    if (route.request().method() === 'DELETE') {
      deleteCalls += 1;
      if (route.request().url().endsWith('/games/g-2') && deleteCalls > 1) {
        return route.fulfill({ status: 409, json: { status: 409 } });
      }
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: { status: 404 } });
  });

  await page.goto('/games');
  await expect(page.getByText('Showing 1–2 of 21')).toBeVisible();
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('Showing 21–21 of 21')).toBeVisible();
  await expect.poll(() => seen.at(-1) ?? '').toContain('page=1');
  await page.getByRole('button', { name: 'Previous' }).click();

  await page.locator('#game-search').fill('love');
  await expect.poll(() => seen.at(-1) ?? '').toContain('search=love');

  const categoryValue = await page
    .locator('#category-filter')
    .locator('option')
    .nth(1)
    .getAttribute('value');
  await page.locator('#category-filter').selectOption({ index: 1 });
  await expect
    .poll(() => seen.at(-1) ?? '')
    .toContain(`category=${categoryValue}`);

  const statusValue = await page
    .locator('#status-filter')
    .locator('option')
    .nth(1)
    .getAttribute('value');
  await page.locator('#status-filter').selectOption({ index: 1 });
  await expect.poll(() => seen.at(-1) ?? '').toContain(`status=${statusValue}`);

  // Retirement asks for confirmation first; dismissing keeps the game listed.
  let acceptDialogs = false;
  page.on('dialog', (dialog) => {
    void (acceptDialogs ? dialog.accept() : dialog.dismiss());
  });
  await page.getByRole('button', { name: 'Delete' }).first().click();
  await expect(deleteCalls).toBe(0);

  acceptDialogs = true;
  await page.getByRole('button', { name: 'Delete' }).first().click();
  await expect(deleteCalls).toBe(1);
});

test('game inventory shows the saved notice, empty state, and load error', async ({
  page,
}) => {
  await mockManager(page);
  await page.route('**/api/v1/games?*', (route) =>
    route.fulfill({
      json: {
        items: [],
        page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
        stats: { titles: 0, availableNow: 0, inUse: 0 },
      },
    }),
  );

  await page.goto('/games?saved=Sushi%20Go');
  await expect(page.getByTestId('inventory-saved')).toContainText('Sushi Go');
  await expect(page.getByTestId('inventory-empty')).toBeVisible();

  await page.route('**/api/v1/games?*', (route) =>
    route.fulfill({ status: 503, json: { status: 503 } }),
  );
  await page.goto('/games');
  await expect(page.getByTestId('inventory-error')).toBeVisible();
  await expect(page.getByTestId('inventory-delete-error')).toHaveCount(0);
});

test('add game form reports API field errors', async ({ page }) => {
  await mockManager(page);
  // The form intentionally defers required-field checks to the API, so a 422
  // response is how the user sees per-field errors.
  await page.route('**/api/v1/games', (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({
        status: 422,
        json: {
          errors: [
            { field: 'titleEn', message: 'The English title is required.' },
          ],
        },
      });
    }
    return route.fallback();
  });

  await page.goto('/games/new');
  await expect(page.getByRole('heading', { name: 'Add game' })).toBeVisible();
  await page.getByRole('button', { name: 'Add game' }).click();
  await expect(page.getByTestId('form-error')).toContainText(
    'Check the highlighted fields',
  );
  await expect(page.locator('[data-testid$="-error"]').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Add game' })).toBeEnabled();
});

test('edit game form reports a missing game', async ({ page }) => {
  await mockManager(page);
  await page.route('**/api/v1/games/g-404', (route) =>
    route.fulfill({ status: 404, json: { status: 404 } }),
  );

  await page.goto('/games/g-404/edit');
  await expect(page.getByTestId('game-not-found')).toBeVisible();
});

test('table management validates entries and supports view mode', async ({
  page,
}) => {
  await mockManager(page);
  const tables = [
    {
      id: 1,
      name: 'Table 1',
      branchId: branch.id,
      branch: branch.name,
      capacity: 6,
      status: 'Available',
      shape: 'Round',
      active: true,
      lastUpdated: '2026-10-15T09:00:00Z',
    },
  ];
  await page.route('**/api/v1/tables?**', (route) =>
    route.fulfill({
      json: {
        items: tables,
        total: tables.length,
        page: 1,
        pageSize: 5,
        totalPages: 1,
      },
    }),
  );
  await page.route('**/api/v1/tables', (route) => {
    if (route.request().method() === 'POST') {
      const payload = route.request().postDataJSON() as Record<string, unknown>;
      const created = {
        id: tables.length + 1,
        name: String(payload.name ?? 'New table'),
        branchId: branch.id,
        branch: branch.name,
        capacity: Number(payload.capacity ?? 4),
        status: 'Available',
        shape: 'Round',
        active: true,
        lastUpdated: '2026-10-15T09:00:00Z',
      };
      tables.push(created);
      return route.fulfill({ status: 201, json: created });
    }
    return route.fulfill({
      json: {
        items: tables,
        total: tables.length,
        page: 1,
        pageSize: 5,
        totalPages: 1,
      },
    });
  });

  await page.goto('/tables');
  await expect(
    page.getByRole('heading', { name: 'Table management' }),
  ).toBeVisible();

  await page.getByRole('button', { name: '+ Add table' }).click();
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Table name is required.')).toBeVisible();

  await page.locator('#form-branch').selectOption({ label: 'Central Rama II' });
  await page.locator('#form-name').fill('Table 1');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(
    page.getByText('A table with this name already exists in this branch.'),
  ).toBeVisible();

  await page.locator('#form-name').fill('Table 2');
  await page.locator('#form-capacity').selectOption('4');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('cell', { name: 'Table 2' })).toBeVisible();

  await page
    .locator('tr', { hasText: 'Table 2' })
    .getByRole('button', { name: 'View' })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Table details' }),
  ).toBeVisible();
  await expect(page.getByText('4 seats')).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();
  await expect(
    page.getByRole('heading', { name: 'Table details' }),
  ).toHaveCount(0);
});

test('staff reservations can pick a registered client and recover from client load errors', async ({
  page,
}) => {
  await mockStaff(page);
  const clients = {
    items: [
      {
        subject: 'client-subject',
        displayName: 'Local Client',
        firstName: 'Local',
        lastName: 'Client',
        phone: '+66812345678',
      },
    ],
  };
  let failClients = true;
  await page.route('**/api/v1/clients**', (route) => {
    if (failClients) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    return route.fulfill({ json: clients });
  });

  await page.goto('/staff/reservations/new');
  await expect(
    page.getByText(
      'Registered clients could not be loaded. Enter a walk-in client instead.',
    ),
  ).toBeVisible();
  await expect(page.locator('#reservation-first-name')).toBeVisible();
  await expect(page.locator('#reservation-first-name')).toHaveValue('');

  failClients = false;
  await page.goto('/staff/reservations/new');
  await page.locator('#reservation-client-search').fill('Local');
  const options = page.locator('#reservation-client-options option');
  await expect(options).toHaveCount(1);

  await page.locator('#reservation-client-search').fill('Local Client');
  await expect(page.locator('#reservation-first-name')).toHaveValue('Local');
  await expect(page.locator('#reservation-last-name')).toHaveValue('Client');
  await expect(page.locator('#reservation-phone')).toHaveValue('+66812345678');
});

test('staff branch access console recovers from load and save errors', async ({
  page,
}) => {
  await mockManager(page);
  let failAssignments = true;
  await page.route('**/api/v1/staff/branch-assignments**', (route) => {
    if (route.request().method() === 'PUT') {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    if (failAssignments) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    return route.fulfill({
      json: {
        items: [
          {
            staffSubject: 'staff-subject',
            displayName: 'Local Staff',
            email: 'staff@example.test',
            branchIds: [branch.id],
          },
        ],
      },
    });
  });

  await page.goto('/staff/permissions');
  await expect(
    page.getByText('Staff accounts could not be loaded. Try again.'),
  ).toBeVisible();
  failAssignments = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('button', { name: 'Local Staff' })).toBeVisible();

  await page.getByRole('button', { name: 'Local Staff' }).click();
  await page.getByRole('checkbox').first().check();
  await page.getByRole('button', { name: 'Save assignments' }).click();
  await expect(
    page.getByText('Assignments could not be saved. Try again.'),
  ).toBeVisible();
});

test('staff permissions shows its empty state and branches error', async ({
  page,
}) => {
  await mockManager(page);
  let failBranches = false;
  let emptyAssignments = true;
  await page.route('**/api/v1/staff/branch-assignments**', (route) =>
    route.fulfill({
      json: {
        items: emptyAssignments
          ? []
          : [
              {
                staffSubject: 'staff-subject',
                displayName: 'Local Staff',
                email: 'staff@example.test',
                branchIds: [branch.id],
              },
            ],
      },
    }),
  );
  await page.route('**/api/v1/branches', (route) => {
    if (failBranches) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    return route.fulfill({ json: { items: [branch] } });
  });

  await page.goto('/staff/permissions');
  await expect(
    page.getByText('No staff accounts have signed in yet.'),
  ).toBeVisible();

  failBranches = true;
  emptyAssignments = false;
  await page.goto('/staff/permissions');
  await expect(page.getByRole('button', { name: 'Local Staff' })).toBeVisible();
  await page.getByRole('button', { name: 'Local Staff' }).click();
  await expect(
    page.getByText('Branches could not be loaded. Try again.'),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Save assignments' }),
  ).toBeDisabled();
});

test('staff profile links managers to branch access but not staff', async ({
  page,
}) => {
  await mockManager(page);
  await page.goto('/profile');
  await expect(
    page.getByRole('link', { name: 'Staff branch access' }),
  ).toBeVisible();

  await mockStaff(page);
  await page.goto('/profile');
  await expect(
    page.getByRole('link', { name: 'Staff branch access' }),
  ).toHaveCount(0);
  await expect(
    page.getByText(
      'Staff account details are managed by the identity provider.',
    ),
  ).toBeVisible();
});

test('staff game detail reports missing and error states', async ({ page }) => {
  await mockManager(page);
  let fail = true;
  await page.route('**/api/v1/games/g-404', (route) => {
    if (fail) {
      return route.fulfill({ status: 500, json: { status: 500 } });
    }
    return route.fulfill({ status: 404, json: { status: 404 } });
  });

  await page.goto('/games/g-404');
  await expect(
    page.getByText('The inventory could not be loaded.'),
  ).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(
    page.getByText('That game is no longer in the inventory.'),
  ).toBeVisible();
});
