import { expect, test, type Page } from '@playwright/test';

const staffUser = {
  subject: 'staff-subject',
  username: 'staff@example.test',
  email: 'staff@example.test',
  firstName: 'Local',
  lastName: 'Staff',
  roles: ['STAFF'],
  onboardingRequired: false,
};

const managerUser = {
  ...staffUser,
  subject: 'manager-subject',
  username: 'manager@example.test',
  email: 'manager@example.test',
  firstName: 'Local',
  lastName: 'Manager',
  roles: ['MANAGER'],
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
  ],
};

async function stubSession(page: Page, user: typeof staffUser) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(user),
    }),
  );
}

async function stubBranches(page: Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
}

const reservation = (overrides: Record<string, unknown> = {}) => ({
  id: 'res-1',
  title: 'Ticket to Ride',
  date: '2026-09-20',
  timeSlot: '18:00–20:00',
  partySize: 4,
  tableId: 1,
  tableName: 'Map Room',
  seats: 4,
  ratePerHour: 120,
  status: 'Reserved',
  customerName: 'Ada Lovelace',
  phoneNumber: '0800000000',
  checkInTime: '',
  actualCheckOut: '',
  overtimeMinutes: 0,
  totalPrice: 240,
  canCancel: true,
  ...overrides,
});

const reservationPage = (
  items: unknown[],
  page = 1,
  total = items.length,
  pageSize = 10,
) => ({
  items,
  total,
  page,
  pageSize,
  totalPages: Math.max(1, Math.ceil(total / pageSize)),
});

test.describe('floor overview', () => {
  test('renders stats, rows and filters the floor table', async ({ page }) => {
    await stubSession(page, staffUser);
    await page.route('**/api/v1/floor-overview*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          counts: { available: 8, occupied: 3, reserved: 5 },
          items: [
            {
              id: 1,
              name: 'Map Room',
              capacity: 6,
              status: 'Available',
              shape: 'Round',
              reservedSlots: [],
            },
          ],
          total: 1,
          page: 1,
          pageSize: 5,
          totalPages: 1,
        }),
      }),
    );

    await page.goto('/');

    await expect(
      page.getByRole('heading', { name: 'Floor overview' }),
    ).toBeVisible();
    const stats = page.getByTestId('floor-stat');
    await expect(stats).toHaveCount(3);
    await expect(stats.nth(0)).toContainText('8');
    await expect(stats.nth(1)).toContainText('3');
    await expect(stats.nth(2)).toContainText('5');
    await expect(
      page.getByPlaceholder('Search table name or ID'),
    ).toBeVisible();
    await expect(page.getByLabel('Filter by status')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Map Room' })).toBeVisible();
  });

  test('shows an error alert and recovers on retry', async ({ page }) => {
    let failing = true;
    await stubSession(page, staffUser);
    await page.route('**/api/v1/floor-overview*', (route) => {
      if (failing) {
        return route.fulfill({ status: 500 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          counts: { available: 1, occupied: 0, reserved: 0 },
          items: [
            {
              id: 2,
              name: 'Library',
              capacity: 4,
              status: 'Available',
              shape: 'Square',
              reservedSlots: [],
            },
          ],
          total: 1,
          page: 1,
          pageSize: 5,
          totalPages: 1,
        }),
      });
    });

    await page.goto('/');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('We could not load the floor overview.');
    failing = false;
    await alert.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByRole('cell', { name: 'Library' })).toBeVisible();
  });
});

test.describe('table management', () => {
  const tablesResponse = {
    items: [
      {
        id: 1,
        name: 'Map Room',
        capacity: 6,
        status: 'Available',
        shape: 'Round',
        branch: 'Central Rama II',
        branchId: 'branch-1',
        active: true,
        lastUpdated: '2026-09-01T10:00:00Z',
      },
    ],
    total: 1,
    page: 1,
    pageSize: 5,
    totalPages: 1,
  };

  test('creates a table from the add form', async ({ page }) => {
    let created = false;
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/tables**', (route) => {
      if (route.request().method() === 'POST') {
        created = true;
        const body = route.request().postDataJSON() as { name: string };
        return route.fulfill({
          contentType: 'application/json',
          status: 201,
          body: JSON.stringify({ ...tablesResponse.items[0], name: body.name }),
        });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          created
            ? {
                ...tablesResponse,
                total: 2,
                items: [
                  ...tablesResponse.items,
                  { ...tablesResponse.items[0], id: 2, name: 'New Table' },
                ],
              }
            : tablesResponse,
        ),
      });
    });

    await page.goto('/tables');

    await expect(
      page.getByRole('heading', { name: 'Table management' }),
    ).toBeVisible();
    await expect(page.getByText('Total tables')).toBeVisible();
    await page.getByRole('button', { name: '+ Add table' }).click();
    await expect(
      page.getByRole('heading', { name: 'Add table' }),
    ).toBeVisible();
    await page.getByLabel('Table name').fill('New Table');
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('cell', { name: 'New Table' })).toBeVisible();
  });

  test('deletes a table after confirm and refetches the list', async ({
    page,
  }) => {
    let deleted = false;
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/tables**', (route) => {
      if (route.request().method() === 'DELETE') {
        deleted = true;
        return route.fulfill({ status: 204 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          deleted ? { ...tablesResponse, items: [], total: 0 } : tablesResponse,
        ),
      });
    });

    page.on('dialog', (dialog) => dialog.accept());

    await page.goto('/tables');
    await expect(page.getByRole('cell', { name: 'Map Room' })).toBeVisible();
    await page.getByRole('button', { name: 'Delete' }).click();

    await expect(page.getByRole('cell', { name: 'Map Room' })).toHaveCount(0);
    await expect(page.getByText('No tables match your filters.')).toBeVisible();
  });
});

test.describe('branch workspace', () => {
  test('reports a failed branch creation', async ({ page }) => {
    await stubSession(page, managerUser);
    await page.route('**/api/v1/branches', (route) => {
      if (route.request().method() === 'POST') {
        return route.fulfill({ status: 400 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(branches),
      });
    });

    await page.goto('/branches');

    await expect(
      page.getByRole('heading', { name: 'Branch workspace' }),
    ).toBeVisible();
    await page.getByLabel('Branch name').fill('Broken branch');
    await page.getByRole('button', { name: 'Create branch' }).click();

    await expect(page.getByRole('alert')).toContainText(
      'The branch could not be created',
    );
  });
});

test.describe('game inventory', () => {
  const gameSummary = {
    id: 'game-1',
    title: { en: 'Ticket to Ride', th: 'ตั๋วรถไฟ' },
    category: 'Strategy',
    minPlayers: 2,
    maxPlayers: 5,
    playTimeMinutes: 60,
    coverImageUrl: null,
    branchName: 'Central Rama II',
    branchCount: 1,
    copies: 3,
    available: 2,
    status: 'Available',
  };

  const gamesResponse = {
    items: [gameSummary],
    page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    stats: { titles: 1, availableNow: 2, inUse: 1 },
  };

  test('renders the inventory and retires a game', async ({ page }) => {
    let retired = false;
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/games/*', (route) => {
      if (route.request().method() === 'DELETE') {
        retired = true;
        return route.fulfill({ status: 204 });
      }
      return route.fallback();
    });
    await page.route('**/api/v1/games?*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          retired
            ? {
                items: [],
                page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
                stats: { titles: 0, availableNow: 0, inUse: 0 },
              }
            : gamesResponse,
        ),
      }),
    );

    page.on('dialog', (dialog) => dialog.accept());

    await page.goto('/games');

    await expect(
      page.getByRole('heading', { name: 'Game inventory' }),
    ).toBeVisible();
    await expect(page.getByText('Titles')).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'Ticket to Ride' }),
    ).toBeVisible();
    await expect(page.getByText('+ Add game')).toBeVisible();

    await page.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByTestId('inventory-empty')).toBeVisible();
  });

  test('shows the empty state and the load-error retry path', async ({
    page,
  }) => {
    let failing = true;
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/games?*', (route) => {
      if (failing) {
        return route.fulfill({ status: 500 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          stats: { titles: 0, availableNow: 0, inUse: 0 },
        }),
      });
    });

    await page.goto('/games');

    await expect(page.getByTestId('inventory-error')).toBeVisible();
    failing = false;
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByTestId('inventory-empty')).toBeVisible();
  });
});

test.describe('game detail', () => {
  const game = {
    id: 'game-1',
    title: { en: 'Ticket to Ride', th: 'ตั๋วรถไฟ' },
    description: { en: 'Build routes across the map.', th: 'สร้างเส้นทาง' },
    category: 'Strategy',
    minPlayers: 2,
    maxPlayers: 5,
    playTimeMinutes: 60,
    difficulty: 'Easy to teach',
    tags: ['classic'],
    imageUrls: [],
    guide: { en: '', th: '' },
    lifecycle: 'Active',
    status: 'Available',
    addedAt: '2026-09-01T10:00:00Z',
    lastPlayedAt: null,
    totalCopies: 3,
    branchCount: 1,
    stock: [],
  };

  test('renders the staff game overview', async ({ page }) => {
    await stubSession(page, staffUser);
    await stubBranches(page);
    await page.route('**/api/v1/games/game-1', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(game),
      }),
    );

    await page.goto('/games/game-1');

    await expect(
      page.getByRole('heading', { name: 'Ticket to Ride' }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'Copies by branch' }),
    ).toBeVisible();
  });

  test('renders a not-found state for an unknown game', async ({ page }) => {
    await stubSession(page, staffUser);
    await stubBranches(page);
    await page.route('**/api/v1/games/missing-id', (route) =>
      route.fulfill({ status: 404 }),
    );

    await page.goto('/games/missing-id');

    await expect(page.getByTestId('game-not-found')).toBeVisible();
  });
});

test.describe('add game', () => {
  test('submits the required fields and returns to the inventory', async ({
    page,
  }) => {
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/games?*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          stats: { titles: 0, availableNow: 0, inUse: 0 },
        }),
      }),
    );
    let posted: Record<string, unknown> | undefined;
    await page.route('**/api/v1/games', (route) => {
      if (route.request().method() === 'POST') {
        posted = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({
          contentType: 'application/json',
          status: 201,
          body: JSON.stringify({
            id: 'game-new',
            title: { en: 'New Arrival', th: '' },
            category: 'Strategy',
            minPlayers: 2,
            maxPlayers: 4,
            playTimeMinutes: 45,
            difficulty: '',
            tags: [],
            imageUrls: [],
            guide: { en: '', th: '' },
            lifecycle: 'Active',
            status: 'Available',
            addedAt: '2026-09-01T10:00:00Z',
            lastPlayedAt: null,
            totalCopies: 1,
            branchCount: 1,
            stock: [],
          }),
        });
      }
      return route.fallback();
    });

    await page.goto('/games/new');

    await expect(page.getByRole('heading', { name: 'Add game' })).toBeVisible();
    await page.getByLabel('Game title (English)').fill('New Arrival');
    await page.getByLabel('Min players').fill('2');
    await page.getByLabel('Max players').fill('4');
    await page.getByRole('button', { name: 'Add game' }).click();

    await expect(page).toHaveURL(/\/games\?.*saved=/);
    await expect(page.getByTestId('inventory-saved')).toContainText(
      'New Arrival',
    );
    expect(posted?.title).toEqual({ en: 'New Arrival', th: null });
  });
});

test.describe('staff reservations', () => {
  test('renders reservations with status badges', async ({ page }) => {
    await stubSession(page, staffUser);
    await page.route('**/api/v1/staff/reservations*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          reservationPage([
            reservation(),
            reservation({
              id: 'res-2',
              status: 'CheckedIn',
              customerName: 'Grace Hopper',
              tableName: 'Library',
            }),
          ]),
        ),
      }),
    );

    await page.goto('/staff/reservations');

    await expect(
      page.getByRole('heading', { name: 'Reservations' }),
    ).toBeVisible();
    const rows = page.getByTestId('staff-reservation-row');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('Ada Lovelace');
    await expect(rows.nth(0)).toContainText('Map Room');
    await expect(rows.nth(0)).toContainText('4 guests');
    await expect(rows.nth(0)).toContainText('Waiting to start');
    await expect(rows.nth(1)).toContainText('In play');
    await expect(
      page.locator('.owner-sidebar nav [aria-current="page"]'),
    ).toHaveText('Reservations');
  });

  test('refetches with the selected status filter', async ({ page }) => {
    const statuses: Array<string | null> = [];
    await stubSession(page, staffUser);
    await page.route('**/api/v1/staff/reservations*', (route) => {
      const url = new URL(route.request().url());
      statuses.push(url.searchParams.get('status'));
      const status = url.searchParams.get('status');
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          reservationPage(
            status === 'Completed'
              ? [reservation({ id: 'res-done', status: 'Completed' })]
              : [reservation()],
          ),
        ),
      });
    });

    await page.goto('/staff/reservations');
    await expect(page.getByTestId('staff-reservation-row')).toHaveCount(1);

    await page.getByLabel('Status').selectOption('Completed');

    await expect(
      page.getByTestId('staff-reservation-row').getByText('Completed'),
    ).toBeVisible();
    expect(statuses).toContain('Completed');
  });

  test('shows an empty state when no reservations match', async ({ page }) => {
    await stubSession(page, staffUser);
    await page.route('**/api/v1/staff/reservations*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservationPage([])),
      }),
    );

    await page.goto('/staff/reservations');

    await expect(page.getByTestId('staff-reservations-empty')).toContainText(
      'No reservations match this view yet.',
    );
  });

  test('shows an error alert and recovers on retry', async ({ page }) => {
    let failing = true;
    await stubSession(page, staffUser);
    await page.route('**/api/v1/staff/reservations*', (route) => {
      if (failing) {
        return route.fulfill({ status: 500 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservationPage([reservation()])),
      });
    });

    await page.goto('/staff/reservations');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('Reservations could not be loaded.');
    failing = false;
    await alert.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByTestId('staff-reservation-row')).toHaveCount(1);
  });

  test('paginates when more than one page of reservations exists', async ({
    page,
  }) => {
    const pages: Array<string | null> = [];
    await stubSession(page, staffUser);
    await page.route('**/api/v1/staff/reservations*', (route) => {
      const url = new URL(route.request().url());
      const page = url.searchParams.get('page');
      pages.push(page);
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          reservationPage(
            [reservation({ id: `res-${page}` })],
            Number(page),
            25,
          ),
        ),
      });
    });

    await page.goto('/staff/reservations');

    const pagination = page.locator('nav[aria-label="Pagination"]');
    await expect(pagination).toBeVisible();
    await pagination.getByRole('button', { name: 'Page 2' }).click();

    await expect.poll(() => pages).toContain('2');
  });

  test('navigates to the new reservation wizard', async ({ page }) => {
    await stubSession(page, staffUser);
    await stubBranches(page);
    await page.route('**/api/v1/staff/reservations*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservationPage([reservation()])),
      }),
    );
    await page.route('**/api/v1/clients*', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    );

    await page.goto('/staff/reservations');
    await page.getByTestId('new-reservation').click();

    await expect(page).toHaveURL(/\/staff\/reservations\/new/);
    await expect(
      page.getByRole('heading', { name: 'Create reservation' }),
    ).toBeVisible();
  });
});

test.describe('staff profile and permissions', () => {
  test('renders the staff profile identity', async ({ page }) => {
    await stubSession(page, staffUser);
    await page.goto('/profile');

    await expect(
      page.getByRole('heading', { name: 'User profile' }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'Local Staff' }),
    ).toBeVisible();
    await expect(
      page.getByText('STAFF', { exact: true }).first(),
    ).toBeVisible();
    await expect(page.getByText('Managed in Keycloak')).toBeVisible();
  });

  test('saves staff branch assignments as a manager', async ({ page }) => {
    await stubSession(page, managerUser);
    await stubBranches(page);
    await page.route('**/api/v1/staff/branch-assignments/*', (route) => {
      if (route.request().method() === 'PUT') {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ items: [] }),
        });
      }
      return route.fallback();
    });
    await page.route('**/api/v1/staff/branch-assignments', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              staffSubject: 'staff-subject',
              displayName: 'Local Staff',
              email: 'staff@example.test',
              branchIds: [],
            },
          ],
        }),
      }),
    );

    await page.goto('/staff/permissions');

    await expect(
      page.getByRole('heading', { name: 'Staff branch access' }),
    ).toBeVisible();
    await page.getByRole('button', { name: /Local Staff/ }).click();
    await page.getByRole('checkbox', { name: 'Central Rama II' }).check();
    await page.getByRole('button', { name: 'Save assignments' }).click();

    await expect(page.getByRole('status')).toContainText(
      'Branch assignments saved.',
    );
  });

  test('denies the permissions screen to non-managers', async ({ page }) => {
    await stubSession(page, staffUser);
    await page.goto('/staff/permissions');

    await expect(
      page.getByRole('heading', {
        name: 'You do not have access to this page',
      }),
    ).toBeVisible();
  });
});

test.describe('session console', () => {
  test('checks a guest in and closes the session with a receipt', async ({
    page,
  }) => {
    await stubSession(page, staffUser);
    let status = 'Reserved';
    const served = () =>
      JSON.stringify({
        items: [reservation({ id: 'res-1', status, totalPrice: 120 })],
      });
    await page.route('**/api/v1/sessions', (route) =>
      route.fulfill({ contentType: 'application/json', body: served() }),
    );
    await page.route('**/api/v1/reservations/res-1/check-in', (route) => {
      status = 'CheckedIn';
      return route.fulfill({ contentType: 'application/json', body: served() });
    });
    await page.route('**/api/v1/reservations/res-1/check-out', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          tableName: 'Map Room',
          partySize: 4,
          totalDue: 120,
          paymentMethod: 'Cash',
          payment: null,
        }),
      }),
    );

    await page.goto('/staff/sessions');

    await expect(
      page.getByRole('heading', { name: 'Session console' }),
    ).toBeVisible();
    const row = page.getByRole('row').filter({ hasText: 'Map Room' });
    await expect(row).toContainText('Waiting to start');
    await row.getByRole('button', { name: 'Check in' }).click();

    await expect(row).toContainText('In play');
    await row.getByRole('button', { name: 'Check out' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByLabel('Final amount (THB)')).toHaveValue('120');
    await dialog.getByRole('button', { name: 'Close session' }).click();

    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
});
