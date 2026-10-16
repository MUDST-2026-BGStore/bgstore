import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from './coverage-fixture';

// Covers the client-facing interactions that the earlier journeys did not
// reach: home branches/carousel, branch directory, game catalogue filters and
// gallery, history tabs/pagination, profile identity handling, session
// assistance edges, onboarding, and the auth redirect guard.

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

const onboardingUser = {
  ...clientUser,
  firstName: '',
  lastName: '',
  onboardingRequired: true,
};

function branch(id: string, name: string, status = 'ACTIVE') {
  return {
    id,
    name,
    address: `${id.slice(-2)} Riverside Road, Bangkok`,
    opensAt: '09:00',
    closesAt: '19:00',
    phone: '+66 2 123 4567',
    status,
  };
}

const activeBranch = branch(
  '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
  'Central Rama II',
);
const otherBranch = branch(
  '3f0d7d5a-9a2b-4a71-8f0e-000000000002',
  'Siam Square One',
);
const inactiveBranch = branch(
  '3f0d7d5a-9a2b-4a71-8f0e-000000000003',
  'Riverside Pier',
  'INACTIVE',
);

async function mockClient(page: Page, user = clientUser) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(user),
    }),
  );
}

async function mockBranches(page: Page, items = [activeBranch, otherBranch]) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items } }),
  );
}

function game(id: string, title: string) {
  return {
    id,
    title: { en: title, th: null },
    category: 'CARD',
    minPlayers: 2,
    maxPlayers: 4,
    playTimeMinutes: 30,
    coverImageUrl: null,
    branchName: 'Central Rama II',
    branchCount: 1,
    copies: 3,
    available: 2,
    status: 'AVAILABLE',
  };
}

test('home shows a loading state, recovers from a branch load failure, and toggles view all', async ({
  page,
}) => {
  await mockClient(page);
  let allowBranches = false;
  await page.route('**/api/v1/branches', (route) => {
    if (!allowBranches) {
      return route.fulfill({ status: 503, json: { status: 503 } });
    }
    return route.fulfill({
      json: {
        items: [
          activeBranch,
          otherBranch,
          inactiveBranch,
          branch('3f0d7d5a-9a2b-4a71-8f0e-000000000004', 'Central Westgate'),
        ],
      },
    });
  });

  await page.goto('/');
  await expect(page.getByTestId('home-branches-error')).toBeVisible();
  await expect(page.getByTestId('home-branches-loading')).toHaveCount(0);

  allowBranches = true;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByTestId('home-branch')).toHaveCount(3);
  await expect(page.getByTestId('home-branches-error')).toHaveCount(0);

  const toggle = page.getByRole('button', { name: 'View all' });
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await toggle.click();
  await expect(page.getByTestId('home-branch')).toHaveCount(4);
  await expect(
    page.getByRole('button', { name: 'Show fewer' }),
  ).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('button', { name: 'Show fewer' }).click();
  await expect(page.getByTestId('home-branch')).toHaveCount(3);
});

test('home carousel dot navigation moves the active slide', async ({
  page,
}) => {
  await mockClient(page);
  await mockBranches(page);

  await page.goto('/');
  await expect(page.getByTestId('hero-dot')).toHaveCount(3);
  await page.getByTestId('hero-dot').nth(1).click();
  await expect(page.getByTestId('hero-dot').nth(1)).toHaveAttribute(
    'aria-current',
    'true',
  );
  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(page.getByTestId('hero-dot').nth(1)).not.toHaveAttribute(
    'aria-current',
    'true',
  );
  await expect(
    page.locator('[data-testid="hero-dot"][aria-current="true"]'),
  ).toHaveCount(1);
});

test('branch directory filters with search, flags no matches, and selects branches', async ({
  page,
}) => {
  await mockClient(page);
  await mockBranches(page, [activeBranch, inactiveBranch]);

  await page.goto('/branches');
  await expect(
    page.getByRole('heading', { name: 'Branch directory' }),
  ).toBeVisible();
  await expect(page.locator('li button[aria-pressed]')).toHaveCount(2);

  await page.getByPlaceholder('Search branch name or address').fill('Central');
  await expect(page.locator('li button[aria-pressed]')).toHaveCount(1);
  await page
    .getByPlaceholder('Search branch name or address')
    .fill('nowhere matches this');
  await expect(page.getByText('No branches found')).toBeVisible();
  await page.getByPlaceholder('Search branch name or address').fill('');

  await page
    .locator('li button', { hasText: 'Central Rama II' })
    .first()
    .click();
  await expect(page.locator('li button[aria-pressed="true"]')).toHaveCount(1);
});

test('branch directory disables booking for an inactive branch', async ({
  page,
}) => {
  await mockClient(page);
  await mockBranches(page, [activeBranch, inactiveBranch]);

  await page.goto('/branches');
  await page
    .locator('li button', { hasText: 'Riverside Pier' })
    .first()
    .click();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'Booking is currently unavailable' }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', {
      name: 'Booking is currently unavailable for this branch.',
    }),
  ).toBeDisabled();
  await expect(
    page.getByRole('link', { name: 'Book at this branch' }),
  ).toHaveCount(0);

  await page
    .locator('li button', { hasText: 'Central Rama II' })
    .first()
    .click();
  await expect(
    page.getByRole('link', { name: 'Book at this branch' }),
  ).toHaveAttribute('href', /\/reservations\/new\?branch=Central/);
});

test('game catalogue filters by category and search and loads more pages', async ({
  page,
}) => {
  await mockClient(page);
  const seen: string[] = [];
  await page.route('**/api/v1/games?*', (route) => {
    const params = new URL(route.request().url()).searchParams;
    seen.push(
      `page=${params.get('page') ?? ''} category=${params.get('category') ?? ''} search=${params.get('search') ?? ''}`,
    );
    const pageNumber = Number(params.get('page') ?? '0');
    if (pageNumber === 0) {
      return route.fulfill({
        json: {
          items: [game('g-1', 'Sushi Go'), game('g-2', 'Love Letter')],
          page: { number: 0, size: 24, totalElements: 3, totalPages: 2 },
          stats: { titles: 3, availableNow: 2, inUse: 1 },
        },
      });
    }
    return route.fulfill({
      json: {
        items: [game('g-3', 'Hanabi')],
        page: { number: pageNumber, size: 24, totalElements: 3, totalPages: 2 },
        stats: { titles: 3, availableNow: 2, inUse: 1 },
      },
    });
  });

  await page.goto('/games');
  await expect(page.getByTestId('catalogue-card')).toHaveCount(2);
  await page.getByRole('button', { name: 'Card games' }).click();
  await expect(page.getByTestId('catalogue-card')).toHaveCount(2);
  await expect(seen.at(-1)).toContain('category=card');

  await page.locator('#catalogue-search').fill('hanabi');
  await expect.poll(() => seen.at(-1) ?? '').toContain('search=hanabi');

  // Reset category back to all so the unfiltered second page loads.
  await page.getByRole('button', { name: 'All', exact: true }).click();
  await page.getByTestId('catalogue-card').first().waitFor();
  await page.getByRole('button', { name: 'Show more games' }).click();
  await expect(page.getByTestId('catalogue-card')).toHaveCount(3);
});

test('game catalogue reports empty, error, and recovery states', async ({
  page,
}) => {
  await mockClient(page);
  let fail = true;
  await page.route('**/api/v1/games?*', (route) => {
    if (fail) {
      return route.fulfill({ status: 500, json: { status: 500 } });
    }
    return route.fulfill({
      json: {
        items: [],
        page: { number: 1, size: 24, totalElements: 0, totalPages: 0 },
        stats: { titles: 0, availableNow: 0, inUse: 0 },
      },
    });
  });

  await page.goto('/games');
  await expect(page.getByTestId('catalogue-error')).toBeVisible();

  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByTestId('catalogue-empty')).toBeVisible();
});

test('game catalogue detail pages through photos and reports missing and error states', async ({
  page,
}) => {
  await mockClient(page);
  const pixel =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  await page.route('**/cdn.example.test/**', (route) =>
    route.fulfill({
      contentType: 'image/png',
      body: Buffer.from(pixel, 'base64'),
    }),
  );
  const detail = {
    id: 'g-1',
    title: { en: 'Sushi Go', th: null },
    description: null,
    category: 'CARD',
    minPlayers: 2,
    maxPlayers: 4,
    playTimeMinutes: 30,
    difficulty: null,
    tags: [],
    imageUrls: [
      'https://cdn.example.test/a.png',
      'https://cdn.example.test/b.png',
    ],
    guide: { goal: null, players: null, equipment: null, steps: [] },
    lifecycle: 'ACTIVE',
    status: 'AVAILABLE',
    addedAt: '2026-10-01T00:00:00Z',
    lastPlayedAt: null,
    totalCopies: 3,
    branchCount: 1,
    stock: [
      {
        branchId: activeBranch.id,
        branchName: activeBranch.name,
        copies: 3,
        available: 2,
        inUse: 1,
        status: 'AVAILABLE',
      },
    ],
  };

  await page.route('**/api/v1/games/g-1', (route) =>
    route.fulfill({ json: detail }),
  );
  await page.route('**/api/v1/games/g-404', (route) =>
    route.fulfill({ status: 404, json: { status: 404 } }),
  );
  await mockBranches(page);

  await page.goto('/games/g-1');
  await expect(page.getByTestId('catalogue-photo')).toBeVisible();
  await expect(page.getByAltText('Photo 1 of 2 of Sushi Go')).toBeVisible();
  await page.getByRole('button', { name: 'Next photo' }).click();
  await expect(page.getByAltText('Photo 2 of 2 of Sushi Go')).toBeVisible();
  await page.getByRole('button', { name: 'Next photo' }).click();
  await expect(page.getByAltText('Photo 1 of 2 of Sushi Go')).toBeVisible();
  await page.getByRole('button', { name: 'Previous photo' }).click();
  await expect(page.getByAltText('Photo 2 of 2 of Sushi Go')).toBeVisible();

  await page.goto('/games/g-404');
  await expect(page.getByTestId('catalogue-game-not-found')).toBeVisible();
});

test('history filters with status tabs and pages through the list', async ({
  page,
}) => {
  await mockClient(page);
  const seen: string[] = [];
  const item = (n: number) => ({
    id: `r-${n}`,
    title: 'Table reservation',
    date: '16 Oct 2026',
    timeSlot: '13:00–15:00',
    partySize: 4,
    tableId: 12,
    tableName: 'Table 12',
    seats: 6,
    ratePerHour: 50,
    status: 'Reserved',
    customerName: 'Local Client',
    phoneNumber: '+66812345678',
    checkInTime: '',
    actualCheckOut: '',
    overtimeMinutes: 0,
    totalPrice: 0,
    canCancel: true,
  });
  await page.route('**/api/v1/reservations?*', (route) => {
    const params = new URL(route.request().url()).searchParams;
    seen.push(
      `status=${params.get('status') ?? ''} page=${params.get('page') ?? ''}`,
    );
    if (params.get('status') && params.get('status') !== 'Reserved') {
      return route.fulfill({
        json: { items: [], total: 0, page: 1, pageSize: 4, totalPages: 0 },
      });
    }
    const pageNumber = Number(params.get('page') ?? '1');
    if (pageNumber >= 2) {
      return route.fulfill({
        json: {
          items: [item(5)],
          total: 5,
          page: pageNumber,
          pageSize: 4,
          totalPages: 2,
        },
      });
    }
    return route.fulfill({
      json: {
        items: [item(1), item(2), item(3), item(4)],
        total: 5,
        page: 1,
        pageSize: 4,
        totalPages: 2,
      },
    });
  });

  await page.goto('/history');
  await expect(page.getByTestId('reservation-card')).toHaveCount(4);
  await expect(page.getByText('Showing 1–4 of 5')).toBeVisible();
  await page.getByRole('button', { name: 'Next page' }).click();
  await expect(page.getByTestId('reservation-card')).toHaveCount(1);
  await expect(page).toHaveURL(/tab=all&page=2/);
  await expect(page.getByRole('button', { name: 'Next page' })).toBeDisabled();
  await page.getByRole('button', { name: 'Previous page' }).click();
  await expect(page.getByTestId('reservation-card')).toHaveCount(4);
  await expect(page).toHaveURL(/tab=all&page=1/);

  await page.getByTestId('tab-reserved').click();
  await expect.poll(() => seen.at(-1) ?? '').toContain('status=Reserved');
  await expect(page).toHaveURL(/tab=reserved&page=1/);

  await page.getByTestId('tab-cancelled').click();
  await expect(page.getByTestId('empty-state')).toBeVisible();
});

test('history detail shows a not-found state for an unknown reservation', async ({
  page,
}) => {
  await mockClient(page);
  await page.route('**/api/v1/reservations/unknown', (route) =>
    route.fulfill({ status: 404, json: { status: 404 } }),
  );

  await page.goto('/history/unknown');
  await expect(page.getByTestId('detail-error')).toBeVisible();
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page).toHaveURL(/\/history/);
});

test('client profile keeps Keycloak identity read-only and saves only profile details', async ({
  page,
}) => {
  await mockClient(page);
  let savedPayload: Record<string, unknown> | null = null;
  await page.route('**/api/v1/me/client-profile', (route) => {
    savedPayload = route.request().postDataJSON() as Record<string, unknown>;
    return route.fulfill({ json: { phone: '+66812345678', completed: true } });
  });

  await page.goto('/profile');
  await page.getByRole('button', { name: 'Edit profile' }).click();
  await expect(page.getByRole('note')).toContainText('managed in Keycloak');
  const usernameReadonly = await page
    .locator('#profile-username')
    .evaluate((el) => el.hasAttribute('readonly'));
  expect(usernameReadonly).toBe(true);
  await expect(page.locator('#profile-confirm-password')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Confirm' })).toBeDisabled();

  // The identity fields are read-only, so they cannot accept input at all.
  await page.locator('#profile-phone').fill('0898765432');
  await expect(page.getByRole('button', { name: 'Confirm' })).toBeEnabled();
  await page.getByRole('button', { name: 'Confirm' }).click();
  await expect(
    page.getByText('Your phone number has been updated.'),
  ).toBeVisible();
  expect(savedPayload).toMatchObject({ phoneNumber: '0898765432' });

  await page.getByRole('button', { name: 'Edit profile' }).click();
  await page.locator('#profile-phone').fill('0811111111');
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.locator('#profile-phone')).toHaveValue('+66812345678');
  await expect(
    page.getByRole('button', { name: 'Edit profile' }),
  ).toBeVisible();
});

test('active session assistance failure shows an alert and end-playing can be cancelled', async ({
  page,
}) => {
  await mockClient(page);
  const activeSession = {
    reservationId: 'reservation-123',
    locationName: 'Central Rama II',
    tableName: 'Table 12',
    startedAt: '2026-10-15T08:00:00Z',
    partySize: 2,
    ratePerHour: 50,
    accruedAmount: 100,
    currency: 'THB',
  };
  const assistanceCalls: string[] = [];
  await page.route('**/api/v1/me/active-session', (route) =>
    route.fulfill({ json: activeSession }),
  );
  await page.route(
    '**/api/v1/reservations/reservation-123/assistance',
    (route) => {
      assistanceCalls.push(String(route.request().postDataJSON()));
      return route.fulfill({ status: 500, json: { status: 500 } });
    },
  );

  await page.goto('/sessions/active');
  await expect(
    page.getByRole('heading', { name: 'Current Session Details' }),
  ).toBeVisible();

  await page.getByRole('button', { name: 'End Playing' }).click();
  const dialog = page.getByRole('dialog', { name: 'End this session?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toHaveCount(0);
  expect(assistanceCalls).toEqual([]);

  await page.getByRole('button', { name: 'End Playing' }).click();
  await dialog.getByRole('button', { name: 'Request to end' }).click();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'That request could not be sent.' }),
  ).toBeVisible();
  expect(assistanceCalls).toHaveLength(1);
});

test('client checkout reports a settlement request failure', async ({
  page,
}) => {
  await mockClient(page);
  await page.route('**/api/v1/me/active-session', (route) =>
    route.fulfill({
      json: {
        reservationId: 'reservation-123',
        locationName: 'Central Rama II',
        tableName: 'Table 12',
        startedAt: '2026-10-15T08:00:00Z',
        partySize: 2,
        ratePerHour: 50,
        accruedAmount: 100,
        currency: 'THB',
      },
    }),
  );
  await page.route(
    '**/api/v1/reservations/reservation-123/assistance',
    (route) => route.fulfill({ status: 500, json: { status: 500 } }),
  );

  await page.goto('/sessions/checkout');
  await expect(
    page.getByRole('heading', { name: 'Settle at the counter' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Request settlement' }).click();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'That request could not be sent.' }),
  ).toBeVisible();
});

test('access denied screen returns to home', async ({ page }) => {
  await mockClient(page);
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({ json: { items: [] } }),
  );

  await page.goto('/tables');
  await expect(
    page.getByRole('heading', { name: 'You do not have access to this page' }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Back to home' }).click();
  await expect(page).toHaveURL('/');
});

test('onboarding surfaces a save failure and supports the country picker', async ({
  page,
}) => {
  await mockClient(page, onboardingUser);
  await page.route('**/api/v1/me/client-profile', (route) =>
    route.fulfill({ status: 422, json: { status: 422 } }),
  );

  await page.goto('/onboarding');
  await expect(page.locator('#firstName')).toBeVisible();
  await expect(page.locator('#lastName')).toBeVisible();
  await page.locator('#firstName').fill('Nok');
  await page.locator('#lastName').fill('Sukhum');
  await page.locator('#phoneNumber').fill('0812345678');

  await page.getByTestId('country-trigger').click();
  const listbox = page.getByRole('listbox', { name: 'Country code' });
  await expect(listbox).toBeVisible();
  await listbox
    .getByRole('option')
    .filter({ hasText: 'United States' })
    .click();
  await expect(page.getByTestId('country-trigger')).toContainText('+1');

  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'We could not save your details.' }),
  ).toBeVisible();
});

test('auth redirect neutralises unsafe returnTo targets', async ({ page }) => {
  // The mock-mode suite verifies the SPA's returnTo sanitisation against the
  // stubbed BFF endpoints; the real stack delegates /login straight to Keycloak.
  test.skip(
    Boolean(process.env['BGSTORE_FULL_STACK']),
    'the real BFF redirects /login to Keycloak instead of the SPA route',
  );
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/auth/sign-in?*', (route) =>
    route.fulfill({ contentType: 'text/html', body: '<html></html>' }),
  );

  await page.goto('/login?redirect=//evil.example.test');
  await page.waitForURL('**/auth/sign-in?*');
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/');

  await page.goto('/login?redirect=https%3A%2F%2Fevil.example.test');
  await page.waitForURL('**/auth/sign-in?*');
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/');
});

test('client surfaces hold WCAG 2 A/AA on the branch directory', async ({
  page,
}) => {
  await mockClient(page);
  await mockBranches(page, [activeBranch, inactiveBranch]);

  await page.goto('/branches');
  await expect(
    page.getByRole('heading', { name: 'Branch directory' }),
  ).toBeVisible();
  const audit = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  expect(audit.violations).toEqual([]);
});
