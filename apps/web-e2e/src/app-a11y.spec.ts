import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from './coverage-fixture';

// Whole-page WCAG 2 A/AA audits across every major client and staff surface.
// The journeys already audit the reservation card and checkout page; this
// extends automated accessibility checks to the remaining routes.

const clientUser = {
  subject: 'client-subject',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Local',
  lastName: 'Client',
  roles: ['CLIENT'],
  clientProfile: { phone: '+66812345678', completed: true },
  onboardingRequired: false,
};

const managerUser = {
  ...clientUser,
  subject: 'manager-subject',
  username: 'manager@example.test',
  roles: ['MANAGER'],
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

const pixel =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

function gameSummary(id: string) {
  return {
    id,
    title: { en: 'Sushi Go', th: null },
    category: 'CARD',
    minPlayers: 2,
    maxPlayers: 4,
    playTimeMinutes: 30,
    coverImageUrl: 'https://cdn.example.test/cover.png',
    branchName: branch.name,
    branchCount: 1,
    copies: 3,
    available: 2,
    status: 'AVAILABLE',
  };
}

const gameDetail = {
  id: 'g-1',
  title: { en: 'Sushi Go', th: null },
  description: { en: 'Fast card drafting.', th: null },
  category: 'CARD',
  minPlayers: 2,
  maxPlayers: 4,
  playTimeMinutes: 30,
  difficulty: 'Easy',
  tags: ['cards'],
  imageUrls: ['https://cdn.example.test/cover.png'],
  guide: { goal: 'Score points.', players: null, equipment: null, steps: [] },
  lifecycle: 'ACTIVE',
  status: 'AVAILABLE',
  addedAt: '2026-10-01T00:00:00Z',
  lastPlayedAt: null,
  totalCopies: 3,
  branchCount: 1,
  stock: [
    {
      branchId: branch.id,
      branchName: branch.name,
      copies: 3,
      available: 2,
      inUse: 1,
      status: 'AVAILABLE',
    },
  ],
};

function reservationItem(id: string, status: string) {
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
    totalPrice: 300,
    canCancel: true,
    thumbnailUrl: 'https://cdn.example.test/table.png',
  };
}

async function mockCommonRoutes(page: Page, user: typeof clientUser) {
  await page.route('**/api/v1/me', (route) => route.fulfill({ json: user }));
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [branch] } }),
  );
  await page.route('**/api/v1/games?*', (route) =>
    route.fulfill({
      json: {
        items: [gameSummary('g-1')],
        page: { number: 1, size: 24, totalElements: 1, totalPages: 1 },
        stats: { titles: 1, availableNow: 1, inUse: 0 },
      },
    }),
  );
  await page.route('**/api/v1/games/g-1', (route) =>
    route.fulfill({ json: gameDetail }),
  );
  await page.route('**/cdn.example.test/**', (route) =>
    route.fulfill({
      contentType: 'image/png',
      body: Buffer.from(pixel, 'base64'),
    }),
  );
}

async function audit(page: Page, ready: () => ReturnType<Page['getByRole']>) {
  await expect(ready()).toBeVisible();
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  return results.violations;
}

test('client home holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto('/');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Find your table' }),
  );
  expect(violations).toEqual([]);
});

test('branch directory holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto('/branches');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Branch directory' }),
  );
  expect(violations).toEqual([]);
});

test('branch detail holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto(`/branches/${branch.id}`);
  const violations = await audit(page, () =>
    page.getByRole('heading', { level: 1, name: branch.name }),
  );
  expect(violations).toEqual([]);
});

test('game catalogue holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto('/games');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'All games' }),
  );
  expect(violations).toEqual([]);
});

test('game catalogue detail holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto('/games/g-1');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Sushi Go' }),
  );
  expect(violations).toEqual([]);
});

test('reservation history holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.route('**/api/v1/reservations?*', (route) =>
    route.fulfill({
      json: {
        items: [reservationItem('r-1', 'Reserved')],
        total: 1,
        page: 1,
        pageSize: 4,
        totalPages: 1,
      },
    }),
  );
  await page.goto('/history');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Reservation History' }),
  );
  expect(violations).toEqual([]);
});

test('reservation history detail holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.route('**/api/v1/reservations/r-1', (route) =>
    route.fulfill({ json: reservationItem('r-1', 'Reserved') }),
  );
  await page.goto('/history/r-1');
  const violations = await audit(page, () =>
    page.getByTestId('reservation-detail-card'),
  );
  expect(violations).toEqual([]);
});

test('client profile holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, clientUser);
  await page.goto('/profile');
  const violations = await audit(page, () =>
    page.getByRole('button', { name: 'Edit profile' }),
  );
  expect(violations).toEqual([]);
});

test('floor overview holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.route('**/api/v1/floor-overview**', (route) =>
    route.fulfill({
      json: {
        counts: { available: 1, occupied: 0, reserved: 0 },
        items: [
          {
            id: 12,
            name: 'Table 12',
            capacity: 6,
            shape: 'ROUND',
            status: 'AVAILABLE',
            reservedSlots: [],
          },
        ],
        total: 1,
        page: 1,
        pageSize: 5,
        totalPages: 1,
      },
    }),
  );
  await page.goto('/');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Floor overview' }),
  );
  expect(violations).toEqual([]);
});

test('table management holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.route('**/api/v1/tables**', (route) =>
    route.fulfill({
      json: {
        items: [
          {
            id: 12,
            name: 'Table 12',
            branchId: branch.id,
            capacity: 6,
            status: 'AVAILABLE',
            shape: 'ROUND',
            active: true,
            lastUpdated: '2026-10-15T09:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 5,
        totalPages: 1,
      },
    }),
  );
  await page.goto('/tables');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Table management' }),
  );
  expect(violations).toEqual([]);
});

test('session console holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.route('**/api/v1/sessions', (route) =>
    route.fulfill({ json: { items: [reservationItem('r-1', 'Reserved')] } }),
  );
  await page.goto('/staff/sessions');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Session console' }),
  );
  expect(violations).toEqual([]);
});

test('game inventory holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.goto('/games');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Game inventory' }),
  );
  expect(violations).toEqual([]);
});

test('staff branch access holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.route('**/api/v1/staff/branch-assignments', (route) =>
    route.fulfill({
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
    }),
  );
  await page.goto('/staff/permissions');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Staff branch access' }),
  );
  expect(violations).toEqual([]);
});

test('staff profile holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, managerUser);
  await page.goto('/profile');
  const violations = await audit(page, () =>
    page.getByText(
      'Staff account details are managed by the identity provider.',
    ),
  );
  expect(violations).toEqual([]);
});

test('onboarding holds WCAG 2 A/AA', async ({ page }) => {
  await mockCommonRoutes(page, {
    ...clientUser,
    firstName: '',
    lastName: '',
    onboardingRequired: true,
  });
  await page.goto('/onboarding');
  const violations = await audit(page, () =>
    page.getByRole('heading', { name: 'Tell us about yourself' }),
  );
  expect(violations).toEqual([]);
});
