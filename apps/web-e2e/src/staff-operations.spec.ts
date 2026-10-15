import { expect, test, type Page } from './coverage-fixture';

const branch = {
  id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
  name: 'Central Rama II',
  address: '160 Rama II Road, Bangkok 10150',
  opensAt: '09:00',
  closesAt: '19:00',
};

const staffUser = {
  subject: 'staff-operations-subject',
  username: 'staff@example.test',
  email: 'staff@example.test',
  firstName: 'Local',
  lastName: 'Staff',
  roles: ['STAFF'],
  onboardingRequired: false,
};

const table = {
  id: 8,
  name: 'Table 8',
  branch: branch.name,
  capacity: 6,
  shape: 'Round',
  status: 'Available',
  active: true,
  lastUpdated: '2026-10-15T04:00:00Z',
};

const reservedTable = {
  ...table,
  id: 9,
  name: 'Table 9',
  status: 'Reserved',
};

const floorOverview = {
  counts: { available: 1, occupied: 0, reserved: 0 },
  items: [
    {
      id: 8,
      name: 'Table 8',
      capacity: 6,
      shape: 'Round',
      status: 'Available',
      reservedSlots: [],
    },
  ],
  total: 1,
  page: 1,
  pageSize: 5,
  totalPages: 1,
};

const playingReservation = {
  id: 'reservation-playing',
  title: 'Table 8',
  date: '2026-10-15',
  timeSlot: '10:00–13:00',
  partySize: 4,
  tableId: 8,
  tableName: 'Table 8',
  seats: 6,
  ratePerHour: 150,
  status: 'CheckedIn',
  customerName: 'Somchai P.',
  phoneNumber: '0898765432',
  checkInTime: '2026-10-15T03:00:00Z',
  actualCheckOut: '-',
  overtimeMinutes: 0,
  totalPrice: 0,
  canCancel: false,
};

async function mockStaff(page: Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({ json: staffUser }),
  );
}

async function mockBranches(page: Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [branch] } }),
  );
}

test('floor overview sends the selected status and normalized search to the API', async ({
  page,
}) => {
  await mockStaff(page);
  const observedQueries: URLSearchParams[] = [];
  await page.route('**/api/v1/floor-overview**', async (route) => {
    const query = new URL(route.request().url()).searchParams;
    observedQueries.push(query);
    await route.fulfill({ json: floorOverview });
  });

  await page.goto('/');
  await expect(
    page.getByRole('heading', { name: 'Floor overview' }),
  ).toBeVisible();
  await page.locator('#floor-search').fill('  Table 8  ');
  await page.locator('#floor-status').selectOption('Available');

  await expect(page.locator('tbody tr')).toContainText('Table 8');
  await expect
    .poll(() =>
      observedQueries.some(
        (query) =>
          query.get('search') === 'Table 8' &&
          query.get('status') === 'Available' &&
          query.get('page') === '1' &&
          query.get('pageSize') === '5',
      ),
    )
    .toBe(true);
});

test('table management filters the real branch table list by search and status', async ({
  page,
}) => {
  await mockStaff(page);
  await mockBranches(page);
  const observedQueries: URLSearchParams[] = [];
  await page.route('**/api/v1/tables**', async (route) => {
    const query = new URL(route.request().url()).searchParams;
    observedQueries.push(query);
    const matchesFilters =
      query.get('search') === 'Table 8' && query.get('status') === 'Available';
    const items = matchesFilters ? [table] : [table, reservedTable];
    await route.fulfill({
      json: {
        items,
        total: items.length,
        page: Number(query.get('page') ?? 1),
        pageSize: Number(query.get('pageSize') ?? 5),
        totalPages: 1,
      },
    });
  });

  await page.goto('/tables');
  await expect(
    page.getByRole('heading', { name: 'Table management' }),
  ).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Table 8' })).toBeVisible();
  await page.getByPlaceholder('Search tables').fill('Table 8');
  await page.locator('#table-status').selectOption('Available');

  await expect(page.getByRole('cell', { name: 'Table 8' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Table 9' })).toHaveCount(0);
  await expect
    .poll(() =>
      observedQueries.some(
        (query) =>
          query.get('branch') === branch.name &&
          query.get('search') === 'Table 8' &&
          query.get('status') === 'Available' &&
          query.get('page') === '1' &&
          query.get('pageSize') === '5',
      ),
    )
    .toBe(true);
});

test('table management retries after its table list fails to load', async ({
  page,
}) => {
  await mockStaff(page);
  await mockBranches(page);
  let allowTableResponse = false;
  await page.route('**/api/v1/tables**', async (route) => {
    if (!allowTableResponse) {
      await route.fulfill({ status: 503, json: { status: 503 } });
      return;
    }
    await route.fulfill({
      json: {
        items: [table],
        total: 1,
        page: 1,
        pageSize: 5,
        totalPages: 1,
      },
    });
  });

  await page.goto('/tables');
  await expect(page.getByRole('alert')).toContainText(
    'We could not load the tables.',
  );
  allowTableResponse = true;
  await page.getByRole('button', { name: 'Try again' }).click();

  await expect(page.getByRole('cell', { name: 'Table 8' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('a waived staff checkout records zero, shows its receipt, and sends no card data', async ({
  page,
}) => {
  await mockStaff(page);
  await page.route('**/api/v1/sessions', (route) =>
    route.fulfill({ json: { items: [playingReservation] } }),
  );
  let checkoutBody: unknown;
  await page.route(
    '**/api/v1/reservations/reservation-playing/check-out',
    async (route) => {
      checkoutBody = route.request().postDataJSON();
      await route.fulfill({
        json: {
          reservationId: 'reservation-playing',
          tableName: 'Table 8',
          partySize: 4,
          startedAt: '2026-10-15T03:00:00Z',
          endedAt: '2026-10-15T05:00:00Z',
          hours: 2,
          ratePerHour: 150,
          totalDue: 0,
          currency: 'THB',
          paymentMethod: 'Waived',
          paidAt: '2026-10-15T05:00:00Z',
        },
      });
    },
  );

  await page.goto('/staff/sessions');
  await expect(
    page.getByRole('heading', { name: 'Session console' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Check out' }).click();
  await page.locator('#checkout-method').selectOption('Waived');
  await expect(page.locator('#checkout-amount')).toBeDisabled();
  await expect(page.locator('#checkout-card-number')).toHaveCount(0);
  await page.getByRole('button', { name: 'Close session' }).click();

  const receipt = page.getByRole('dialog');
  await expect(receipt).toContainText('Session closed');
  await expect(receipt).toContainText('Table 8');
  await expect(receipt).toContainText('0.00');
  await expect(receipt).toContainText('Waived');
  await expect(receipt).toContainText('without a charge');
  expect(checkoutBody).toEqual({ finalAmount: 0, paymentMethod: 'Waived' });
});

test('a client is denied the staff table-management screen', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      json: {
        subject: 'client-subject',
        username: 'client@example.test',
        email: 'client@example.test',
        firstName: 'Local',
        lastName: 'Client',
        roles: ['CLIENT'],
        clientProfile: { phone: '+66812345678', completed: true },
        onboardingRequired: false,
      },
    }),
  );

  await page.goto('/tables');

  await expect(
    page.getByRole('heading', { name: 'You do not have access to this page' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Table management' }),
  ).toHaveCount(0);
});
