import { expect, test } from '@playwright/test';

/**
 * Client-facing pages: branch directory, game catalogue, reservation history,
 * live session screens and the client profile.
 *
 * Every scenario mocks the BFF at the network edge, so the specs describe the
 * behaviour of the shipped SPA rather than of a running backend. The `me`
 * endpoint is stubbed once per test as a signed-in CLIENT, because every page
 * in this file sits behind `requiresAuth`.
 */

const BRANCH_ID = '3f0d7d5a-9a2b-4a71-8f0e-000000000001';
const OTHER_BRANCH_ID = '3f0d7d5a-9a2b-4a71-8f0e-000000000002';
const GAME_ID = '1a1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e12';
const OTHER_GAME_ID = '2a1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e13';
const RESERVATION_ID = 'resv-0001';
const OTHER_RESERVATION_ID = 'resv-0002';

const clientUser = {
  subject: 'client-subject',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Local',
  lastName: 'Client',
  roles: ['CLIENT'],
  onboardingRequired: false,
};

const branches = {
  items: [
    {
      id: BRANCH_ID,
      name: 'Central Rama II',
      address: 'Rama II',
      opensAt: '09:00',
      closesAt: '19:00',
      status: 'ACTIVE',
    },
    {
      id: OTHER_BRANCH_ID,
      name: 'Sukhumvit',
      address: 'Sukhumvit Road',
      opensAt: '10:00',
      closesAt: '20:00',
      status: 'ACTIVE',
    },
  ],
};

function gameSummary(
  id: string,
  title: string,
  options: Record<string, unknown> = {},
) {
  return {
    id,
    title: { en: title, th: null },
    category: 'card',
    minPlayers: 2,
    maxPlayers: 5,
    playTimeMinutes: 15,
    coverImageUrl: null,
    branchCount: 1,
    copies: 3,
    available: 3,
    status: 'available',
    ...options,
  };
}

function gamesPage(items: unknown[], overrides: Record<string, unknown> = {}) {
  return {
    items,
    page: { number: 0, size: 24, totalElements: items.length, totalPages: 1 },
    stats: { titles: items.length, availableNow: 46, inUse: 12 },
    ...overrides,
  };
}

function reservation(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: 'Exploding Kittens table',
    date: '2026-09-20',
    timeSlot: '18:00–19:00',
    partySize: 4,
    tableId: 1,
    tableName: 'Map Room',
    seats: 6,
    ratePerHour: 120,
    status: 'Reserved',
    customerName: 'Local Client',
    phoneNumber: '+66812345678',
    checkInTime: '18:00',
    actualCheckOut: '19:05',
    overtimeMinutes: 5,
    totalPrice: 240,
    canCancel: true,
    ...overrides,
  };
}

const activeSession = {
  reservationId: RESERVATION_ID,
  locationName: 'Central Rama II',
  tableName: 'Map Room',
  startedAt: '2026-09-26T10:00:00Z',
  partySize: 4,
  ratePerHour: 120,
  accruedAmount: 240,
  currency: 'THB',
};

async function stubClient(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(clientUser),
    }),
  );
}

async function stubBranches(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
}

/** The catalogue loads images from a CDN; keep the run offline. */
async function stubImages(page: import('@playwright/test').Page) {
  await page.route('https://cdn.example.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: '' }),
  );
}

test.describe('branch directory', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
    await stubBranches(page);
  });

  test('lists branches, keeps the selection and links into the reservation flow', async ({
    page,
  }) => {
    await page.goto('/branches');

    await expect(
      page.getByRole('heading', { name: 'Branch directory' }),
    ).toBeVisible();
    const items = page.locator('[aria-selected]');
    await expect(items).toHaveCount(2);
    await expect(items.first()).toHaveAttribute('aria-selected', 'true');
    await expect(
      page.getByRole('link', { name: 'Book at this branch' }),
    ).toHaveAttribute('href', /\/reservations\/new\?branch=/);

    await items.nth(1).click();
    await expect(items.nth(1)).toHaveAttribute('aria-selected', 'true');
    await expect(items.first()).toHaveAttribute('aria-selected', 'false');
  });

  test('filters the directory by name and shows the empty state when nothing matches', async ({
    page,
  }) => {
    await page.goto('/branches');

    await page
      .getByPlaceholder('Search branch name or address')
      .fill('sukhumvit');

    await expect(page.locator('[aria-selected]')).toHaveCount(1);
    await expect(page.locator('[aria-selected]')).toContainText('Sukhumvit');

    await page
      .getByPlaceholder('Search branch name or address')
      .fill('no such branch');

    await expect(page.getByText('No branches found')).toBeVisible();
    await expect(page.locator('[aria-selected]')).toHaveCount(0);
  });

  test('reuses the shared branch list to describe one branch', async ({
    page,
  }) => {
    await page.goto(`/branches/${BRANCH_ID}`);

    const details = page.locator('dl');
    await expect(details).toContainText(BRANCH_ID);
    await expect(details).toContainText('Rama II');
    await expect(details).toContainText('Opening hours');
    await expect(details).toContainText('09:00–19:00');
    await expect(
      page.getByRole('button', { name: 'Back to branches' }),
    ).toBeVisible();
  });

  test('explains an unknown branch id instead of rendering a blank page', async ({
    page,
  }) => {
    await page.goto('/branches/not-a-real-branch');

    await expect(
      page.getByRole('heading', { name: 'Branch not found' }),
    ).toBeVisible();
  });
});

test.describe('game catalogue', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
    await stubImages(page);
  });

  test('renders a card per game with the catalogue count', async ({ page }) => {
    await page.route('**/api/v1/games**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          gamesPage([
            gameSummary(GAME_ID, 'Exploding Kittens'),
            gameSummary(OTHER_GAME_ID, 'Splendor', { category: 'strategy' }),
          ]),
        ),
      }),
    );

    await page.goto('/games');

    await expect(
      page.getByRole('heading', { name: 'All games' }),
    ).toBeVisible();
    const cards = page.getByTestId('catalogue-card');
    await expect(cards).toHaveCount(2);
    await expect(cards.first()).toContainText('Exploding Kittens');
    await expect(page.getByTestId('catalogue-count')).toHaveText('2 games');
    await expect(
      page.getByRole('group', { name: 'Filter by category' }),
    ).toBeVisible();
  });

  test('refetches the catalogue with the debounced search term', async ({
    page,
  }) => {
    const searchQueries: (string | null)[] = [];
    await page.route('**/api/v1/games**', (route) => {
      searchQueries.push(
        new URL(route.request().url()).searchParams.get('search'),
      );
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          gamesPage([gameSummary(GAME_ID, 'Exploding Kittens')]),
        ),
      });
    });

    await page.goto('/games');
    await expect(page.getByTestId('catalogue-card')).toHaveCount(1);

    await page.getByPlaceholder('Search games').fill('kittens');

    await expect.poll(() => searchQueries.includes('kittens')).toBe(true);
    await expect(page.getByTestId('catalogue-card')).toHaveCount(1);
  });

  test('shows the game guide, availability and the branch list for one game', async ({
    page,
  }) => {
    await page.route(`**/api/v1/games/${GAME_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          id: GAME_ID,
          title: { en: 'Exploding Kittens', th: 'เหมียวระเบิด' },
          description: null,
          category: 'card',
          minPlayers: 2,
          maxPlayers: 5,
          playTimeMinutes: 15,
          difficulty: 'Easy',
          tags: ['Popular'],
          imageUrls: [
            'https://cdn.example.com/ek-box.jpg',
            'https://cdn.example.com/ek-cards.jpg',
          ],
          guide: {
            goal: { en: 'Be the last player left standing.', th: null },
            players: { en: '2–5 (up to 10 with the Party Pack)', th: null },
            equipment: null,
            steps: [
              {
                title: { en: 'Deal the cards', th: null },
                body: { en: 'Everyone gets a Defuse card.', th: null },
              },
            ],
          },
          lifecycle: 'active',
          status: 'available',
          addedAt: '2025-01-12T09:00:00Z',
          lastPlayedAt: null,
          totalCopies: 4,
          branchCount: 2,
          stock: [
            {
              branchId: BRANCH_ID,
              branchName: 'Central Rama II',
              copies: 3,
              available: 3,
              inUse: 0,
              status: 'available',
            },
            {
              branchId: OTHER_BRANCH_ID,
              branchName: 'Sukhumvit',
              copies: 0,
              available: 0,
              inUse: 0,
              status: 'notStocked',
            },
          ],
        }),
      }),
    );

    await page.goto(`/games/${GAME_ID}`);

    await expect(
      page.getByRole('heading', { name: 'Exploding Kittens' }),
    ).toBeVisible();
    await expect(page.getByTestId('catalogue-availability')).toContainText(
      '3 sets free',
    );
    await expect(page.getByTestId('catalogue-goal')).toHaveCount(2);
    await expect(page.getByTestId('catalogue-goal').first()).toContainText(
      'Be the last player left standing.',
    );
    await expect(page.getByTestId('catalogue-step')).toHaveCount(1);
    await expect(page.getByTestId('catalogue-step')).toContainText(
      'Deal the cards',
    );

    const photo = page.getByTestId('catalogue-photo');
    const firstSrc = await photo.getAttribute('src');
    await page.getByRole('button', { name: 'Next photo' }).click();
    await expect(photo).not.toHaveAttribute('src', firstSrc ?? '');

    const branchesToggle = page.getByRole('button', {
      name: 'Branches with this game',
    });
    await expect(branchesToggle).toHaveAttribute('aria-expanded', 'false');
    await branchesToggle.click();
    await expect(branchesToggle).toHaveAttribute('aria-expanded', 'true');
    // Only branches holding a copy are listed; Sukhumvit stocks none.
    await expect(page.getByTestId('catalogue-branches')).toHaveCount(1);
    await expect(page.getByTestId('catalogue-branches')).toContainText(
      'Central Rama II',
    );
  });

  test('answers an unknown game with the not-found state', async ({ page }) => {
    await page.route(`**/api/v1/games/${GAME_ID}`, (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 404, title: 'Not Found' }),
      }),
    );

    await page.goto(`/games/${GAME_ID}`);

    await expect(page.getByTestId('catalogue-game-not-found')).toBeVisible();
  });
});

test.describe('reservation history', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
  });

  test('renders each reservation with its status badge', async ({ page }) => {
    await page.route('**/api/v1/reservations?**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            reservation(RESERVATION_ID),
            reservation(OTHER_RESERVATION_ID, {
              title: 'Catan night',
              status: 'Completed',
              canCancel: false,
            }),
          ],
          total: 2,
          page: 1,
          pageSize: 4,
          totalPages: 1,
        }),
      }),
    );

    await page.goto('/history');

    await expect(
      page.getByRole('heading', { name: 'Reservation History' }),
    ).toBeVisible();
    await expect(page.getByTestId('reservation-card')).toHaveCount(2);
    const badges = page.getByTestId('status-badge');
    await expect(badges.nth(0)).toHaveText('Reserved');
    await expect(badges.nth(1)).toHaveText('Completed');
    await expect(page.getByTestId('view-button')).toHaveCount(2);
  });

  test('refetches with the status filter when a tab is chosen', async ({
    page,
  }) => {
    const statuses: (string | null)[] = [];
    await page.route('**/api/v1/reservations?**', (route) => {
      statuses.push(new URL(route.request().url()).searchParams.get('status'));
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [reservation(RESERVATION_ID, { status: 'Reserved' })],
          total: 1,
          page: 1,
          pageSize: 4,
          totalPages: 1,
        }),
      });
    });

    await page.goto('/history');
    await expect(page.getByTestId('reservation-card')).toHaveCount(1);

    await page.getByTestId('tab-reserved').click();

    await expect.poll(() => statuses.includes('Reserved')).toBe(true);
    await expect(page.getByTestId('tab-reserved')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  test('loads the next page of reservations', async ({ page }) => {
    const pages: (string | null)[] = [];
    await page.route('**/api/v1/reservations?**', (route) => {
      const url = new URL(route.request().url());
      pages.push(url.searchParams.get('page'));
      const pageNumber = url.searchParams.get('page') === '2' ? 2 : 1;
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            reservation(RESERVATION_ID, {
              title:
                pageNumber === 2 ? 'Second page table' : 'First page table',
            }),
          ],
          total: 5,
          page: pageNumber,
          pageSize: 4,
          totalPages: 2,
        }),
      });
    });

    await page.goto('/history');
    await expect(page.getByTestId('reservation-card')).toContainText(
      'First page table',
    );

    await page.getByTestId('page-2').click();

    await expect.poll(() => pages.includes('2')).toBe(true);
    await expect(page.getByTestId('reservation-card')).toContainText(
      'Second page table',
    );
  });

  test('shows the empty state when a filter has no reservations', async ({
    page,
  }) => {
    await page.route('**/api/v1/reservations?**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [],
          total: 0,
          page: 1,
          pageSize: 4,
          totalPages: 1,
        }),
      }),
    );

    await page.goto('/history');

    await expect(page.getByTestId('empty-state')).toContainText(
      'No reservations found under this category',
    );
  });

  test('reports a failed history load and recovers on retry', async ({
    page,
  }) => {
    // The first load fails at the network edge; a failure must not be read as
    // "this client has no reservations".
    let failing = true;
    await page.route('**/api/v1/reservations?**', (route) =>
      failing
        ? route.fulfill({
            status: 500,
            contentType: 'application/problem+json',
            body: JSON.stringify({
              status: 500,
              title: 'Internal Server Error',
            }),
          })
        : route.fulfill({
            contentType: 'application/json',
            body: JSON.stringify({
              items: [reservation(RESERVATION_ID)],
              total: 1,
              page: 1,
              pageSize: 4,
              totalPages: 1,
            }),
          }),
    );

    await page.goto('/history');

    const error = page.getByTestId('history-error');
    await expect(error).toBeVisible();
    await expect(error).toContainText('Reservations could not be loaded.');
    await expect(page.getByTestId('empty-state')).toHaveCount(0);

    failing = false;
    await page.getByTestId('history-retry').click();

    await expect(page.getByTestId('reservation-card')).toHaveCount(1);
    await expect(page.getByTestId('history-error')).toHaveCount(0);
  });

  test('shows every recorded field on the reservation detail', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservation(RESERVATION_ID)),
      }),
    );

    await page.goto(`/history/${RESERVATION_ID}`);

    await expect(page.getByTestId('reservation-detail-card')).toBeVisible();
    await expect(page.getByTestId('field-name')).toHaveValue('Local Client');
    await expect(page.getByTestId('field-phone')).toHaveValue('+66812345678');
    await expect(page.getByTestId('field-date')).toHaveValue('2026-09-20');
    await expect(page.getByTestId('field-time')).toHaveValue('18:00–19:00');
    await expect(page.getByTestId('field-checkin')).toHaveValue('18:00');
    await expect(page.getByTestId('field-checkout')).toHaveValue('19:05');
    await expect(page.getByTestId('field-overtime')).toContainText('5 Minutes');
    await expect(page.getByTestId('field-price')).toHaveValue('240 ฿');
  });

  test('cancels a reserved table through the confirmation modal', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservation(RESERVATION_ID)),
      }),
    );
    let cancelCalls = 0;
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/cancel`,
      async (route) => {
        cancelCalls += 1;
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify(
            reservation(RESERVATION_ID, {
              status: 'Cancelled',
              canCancel: false,
            }),
          ),
        });
      },
    );

    await page.goto(`/history/${RESERVATION_ID}`);

    const cancelButton = page.getByTestId('cancel-button');
    await expect(cancelButton).toBeVisible();
    await cancelButton.click();

    const modal = page.getByTestId('cancel-modal');
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(modal).toContainText('Cancel Reservation');

    // Backing out keeps the reservation.
    await page.getByTestId('modal-keep-button').click();
    await expect(modal).toHaveCount(0);
    await expect(cancelButton).toBeVisible();

    await cancelButton.click();
    await page.getByTestId('modal-confirm-cancel-button').click();

    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Cancelled',
    );
    await expect(page.getByTestId('cancel-button')).toHaveCount(0);
    expect(cancelCalls).toBe(1);
  });

  test('keeps the modal open and explains a failed cancel without retrying', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservation(RESERVATION_ID)),
      }),
    );
    let cancelCalls = 0;
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/cancel`,
      async (route) => {
        cancelCalls += 1;
        await route.fulfill({
          status: 500,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            status: 500,
            title: 'Internal Server Error',
          }),
        });
      },
    );

    await page.goto(`/history/${RESERVATION_ID}`);
    await page.getByTestId('cancel-button').click();
    await page.getByTestId('modal-confirm-cancel-button').click();

    await expect(page.getByTestId('detail-error')).toContainText(
      'The reservation could not be cancelled. Please try again.',
    );
    // The confirmation modal stays open so the client can try again by hand.
    await expect(page.getByTestId('cancel-modal')).toBeVisible();
    expect(cancelCalls).toBe(1);
  });

  test('cancels successfully once the retry after a failure succeeds', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservation(RESERVATION_ID)),
      }),
    );
    let failing = true;
    let cancelCalls = 0;
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/cancel`,
      async (route) => {
        cancelCalls += 1;
        if (failing) {
          await route.fulfill({
            status: 500,
            contentType: 'application/problem+json',
            body: JSON.stringify({
              status: 500,
              title: 'Internal Server Error',
            }),
          });
          return;
        }
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify(
            reservation(RESERVATION_ID, {
              status: 'Cancelled',
              canCancel: false,
            }),
          ),
        });
      },
    );

    await page.goto(`/history/${RESERVATION_ID}`);
    await page.getByTestId('cancel-button').click();
    await page.getByTestId('modal-confirm-cancel-button').click();
    await expect(page.getByTestId('detail-error')).toBeVisible();
    await expect(page.getByTestId('cancel-modal')).toBeVisible();

    // The server recovers and the next attempt closes the modal.
    failing = false;
    await page.getByTestId('modal-confirm-cancel-button').click();

    await expect(page.getByTestId('cancel-modal')).toHaveCount(0);
    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Cancelled',
    );
    expect(cancelCalls).toBe(2);
  });

  test('answers an unknown reservation id with the not-found state and a back control', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 404, title: 'Not Found' }),
      }),
    );

    await page.goto(`/history/${RESERVATION_ID}`);

    const error = page.getByTestId('detail-error');
    await expect(error).toBeVisible();
    await expect(error).toContainText('We could not find that reservation.');
    await expect(page.getByTestId('reservation-detail-card')).toHaveCount(0);
    await expect(error.getByRole('button', { name: 'Back' })).toBeVisible();
  });

  test('hides the cancel action for a completed reservation', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          reservation(RESERVATION_ID, {
            status: 'Completed',
            canCancel: false,
          }),
        ),
      }),
    );

    await page.goto(`/history/${RESERVATION_ID}`);

    await expect(page.getByTestId('reservation-detail-card')).toBeVisible();
    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Completed',
    );
    await expect(page.getByTestId('cancel-button')).toHaveCount(0);
    await expect(page.getByTestId('back-button')).toBeVisible();
  });

  test('hides the cancel action for an already cancelled reservation', async ({
    page,
  }) => {
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(
          reservation(RESERVATION_ID, {
            status: 'Cancelled',
            canCancel: false,
          }),
        ),
      }),
    );

    await page.goto(`/history/${RESERVATION_ID}`);

    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Cancelled',
    );
    await expect(page.getByTestId('cancel-button')).toHaveCount(0);
  });

  test('carries the page back to the list when leaving a detail page', async ({
    page,
  }) => {
    const pages: (string | null)[] = [];
    await page.route('**/api/v1/reservations?**', (route) => {
      const url = new URL(route.request().url());
      pages.push(url.searchParams.get('page'));
      const pageNumber = url.searchParams.get('page') === '2' ? 2 : 1;
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            reservation(RESERVATION_ID, {
              title:
                pageNumber === 2 ? 'Second page table' : 'First page table',
            }),
          ],
          total: 5,
          page: pageNumber,
          pageSize: 4,
          totalPages: 2,
        }),
      });
    });
    await page.route(`**/api/v1/reservations/${RESERVATION_ID}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(reservation(RESERVATION_ID)),
      }),
    );

    await page.goto('/history');
    await page.getByTestId('page-2').click();
    await expect(page.getByTestId('reservation-card')).toContainText(
      'Second page table',
    );

    // Opening a card from page 2 links to the detail with the page in the query.
    await page.getByTestId('view-button').click();
    await expect(page).toHaveURL(/\/history\/resv-0001\?.*page=2/);
    await expect(page.getByTestId('reservation-detail-card')).toBeVisible();

    await page.getByTestId('back-button').click();

    await expect(page).toHaveURL(/\/history\?.*page=2/);
    await expect(page.getByTestId('reservation-card')).toContainText(
      'Second page table',
    );
  });
});

test.describe('active session', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
  });

  test('invites a client without a session to get checked in', async ({
    page,
  }) => {
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 404, title: 'Not Found' }),
      }),
    );

    await page.goto('/sessions/active');

    await expect(
      page.getByRole('heading', { name: 'No active session' }),
    ).toBeVisible();
    await expect(
      page.getByText('You are not checked in right now.'),
    ).toBeVisible();
  });

  test('records a call-staff request and shows its notice', async ({
    page,
  }) => {
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      }),
    );
    const bodies: unknown[] = [];
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/assistance`,
      async (route) => {
        const body = route.request().postDataJSON() as {
          kind: string;
          requestId: string;
        };
        bodies.push(body);
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: body.requestId,
            kind: body.kind,
            status: 'Recorded',
          }),
        });
      },
    );

    await page.goto('/sessions/active');

    await expect(
      page.getByRole('heading', { name: 'Current Session Details' }),
    ).toBeVisible();
    await expect(page.getByText('Central Rama II')).toBeVisible();
    await expect(page.getByText('Map Room')).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'End Playing' }),
    ).toBeVisible();

    await page.getByRole('button', { name: 'Call Staff' }).click();

    await expect(page.getByRole('status')).toContainText(
      'A staff member has been asked to come to your table.',
    );
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toMatchObject({ kind: 'CallStaff' });
    expect((bodies[0] as { requestId: string }).requestId).toBeTruthy();
  });

  test('asks for the session to end only after confirmation', async ({
    page,
  }) => {
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      }),
    );
    const kinds: string[] = [];
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/assistance`,
      async (route) => {
        const body = route.request().postDataJSON() as {
          kind: string;
          requestId: string;
        };
        kinds.push(body.kind);
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: body.requestId,
            kind: body.kind,
            status: 'Recorded',
          }),
        });
      },
    );

    await page.goto('/sessions/active');

    await page.getByRole('button', { name: 'End Playing' }).click();
    const dialog = page.getByRole('dialog', { name: 'End this session?' });
    await expect(dialog).toBeVisible();
    expect(kinds).toHaveLength(0);

    await dialog.getByRole('button', { name: 'Request to end' }).click();

    await expect(page.getByRole('status')).toContainText(
      'Your request to end the session was sent.',
    );
    expect(kinds).toEqual(['EndPlaying']);
  });

  test('reports a failed session load and recovers on retry', async ({
    page,
  }) => {
    let failing = true;
    await page.route('**/api/v1/me/active-session', (route) =>
      failing
        ? route.fulfill({
            status: 500,
            contentType: 'application/problem+json',
            body: JSON.stringify({
              status: 500,
              title: 'Internal Server Error',
            }),
          })
        : route.fulfill({
            contentType: 'application/json',
            body: JSON.stringify(activeSession),
          }),
    );

    await page.goto('/sessions/active');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('Your session could not be loaded.');

    failing = false;
    await alert.getByRole('button', { name: 'Try again' }).click();

    await expect(
      page.getByRole('heading', { name: 'Current Session Details' }),
    ).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('surfaces a failed call-staff request as an alert', async ({ page }) => {
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      }),
    );
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/assistance`,
      (route) =>
        route.fulfill({
          status: 500,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            status: 500,
            title: 'Internal Server Error',
          }),
        }),
    );

    await page.goto('/sessions/active');
    await page.getByRole('button', { name: 'Call Staff' }).click();

    await expect(page.getByRole('alert')).toContainText(
      'That request could not be sent. Please try again.',
    );
  });

  test('sends a single assistance request while the first is in flight', async ({
    page,
  }) => {
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      }),
    );
    let assistanceCalls = 0;
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/assistance`,
      async (route) => {
        assistanceCalls += 1;
        // Hold the response so the request is visibly in flight.
        await new Promise((resolve) => setTimeout(resolve, 1500));
        const body = route.request().postDataJSON() as {
          kind: string;
          requestId: string;
        };
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: body.requestId,
            kind: body.kind,
            status: 'Recorded',
          }),
        });
      },
    );

    await page.goto('/sessions/active');

    const callStaff = page.getByRole('button', { name: 'Call Staff' });
    await callStaff.click();
    // The busy state is bound to the button: a second click while the first
    // request is in flight must be ignored instead of double-calling staff.
    await expect(callStaff).toBeDisabled();
    await callStaff.click({ force: true });

    await expect(page.getByRole('status')).toContainText(
      'A staff member has been asked to come to your table.',
    );
    expect(assistanceCalls).toBe(1);
  });
});

test.describe('session checkout', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
    await page.route('**/api/v1/me/active-session', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      }),
    );
  });

  test('shows the bill preview and completes a settlement request', async ({
    page,
  }) => {
    await page.route(
      `**/api/v1/reservations/${RESERVATION_ID}/assistance`,
      async (route) => {
        const body = route.request().postDataJSON() as {
          kind: string;
          requestId: string;
        };
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: body.requestId,
            kind: body.kind,
            status: 'Recorded',
          }),
        });
      },
    );

    await page.goto('/sessions/checkout');

    await expect(
      page.getByRole('heading', { name: 'Settle your session' }),
    ).toBeVisible();
    await expect(page.getByText('Session Bill')).toBeVisible();
    await expect(
      page.getByText(
        'Staff confirm the final amount when they close your session.',
      ),
    ).toBeVisible();

    await page.getByRole('button', { name: 'Request settlement' }).click();

    await expect(
      page.getByRole('heading', { name: 'Settlement requested' }),
    ).toBeVisible();
    await expect(page.getByRole('status')).toContainText(
      'A staff member will confirm the final amount and close your session.',
    );
  });
});

test.describe('client profile', () => {
  test.beforeEach(async ({ page }) => {
    await stubClient(page);
  });

  test('shows readonly identity fields until editing starts', async ({
    page,
  }) => {
    await page.goto('/profile');

    await expect(page.getByLabel('Username')).toHaveValue(
      'client@example.test',
    );
    await expect(page.getByLabel('Email')).toHaveValue('client@example.test');
    await expect(page.getByLabel('First name')).toHaveValue('Local');
    await expect(page.getByLabel('Last name')).toHaveValue('Client');
    await expect(page.getByLabel('Username')).toHaveAttribute('readonly', '');

    await page.getByRole('button', { name: 'Edit profile' }).click();

    await expect(page.getByLabel('Username')).not.toHaveAttribute(
      'readonly',
      '',
    );
    await expect(page.getByRole('button', { name: 'Confirm' })).toBeVisible();
  });

  test('saves a new name and phone number for the client', async ({ page }) => {
    const saved: unknown[] = [];
    await page.route('**/api/v1/me/client-profile', async (route) => {
      const body = route.request().postDataJSON() as {
        firstName: string;
        lastName: string;
        countryCode: string;
        phoneNumber: string;
      };
      saved.push(body);
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ phone: body.phoneNumber, completed: true }),
      });
    });

    await page.goto('/profile');
    await page.getByRole('button', { name: 'Edit profile' }).click();

    await page.getByLabel('First name').fill('Somchai');
    await page.getByLabel('Last name').fill('Jaidee');
    await page.getByLabel('Phone').fill('+66899999999');
    await page.getByRole('button', { name: 'Confirm' }).click();

    await expect(
      page.getByText('Your phone number has been updated.'),
    ).toBeVisible();
    expect(saved).toHaveLength(1);
    expect(saved[0]).toEqual({
      firstName: 'Somchai',
      lastName: 'Jaidee',
      countryCode: '+66',
      phoneNumber: '+66899999999',
    });
    await expect(page.getByLabel('First name')).toHaveValue('Somchai');
  });

  test('explains a failed profile save and stays in edit mode', async ({
    page,
  }) => {
    await page.route('**/api/v1/me/client-profile', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          status: 500,
          title: 'Internal Server Error',
        }),
      }),
    );

    await page.goto('/profile');
    await page.getByRole('button', { name: 'Edit profile' }).click();
    await page.getByLabel('Phone').fill('+66899999999');
    await page.getByRole('button', { name: 'Confirm' }).click();

    await expect(page.getByRole('alert')).toContainText(
      'We could not update your profile. Check the phone number and try again.',
    );
    // The form is still editable so the client can correct the value.
    await expect(page.getByRole('button', { name: 'Confirm' })).toBeVisible();
    await expect(page.getByLabel('Phone')).not.toHaveAttribute('readonly', '');
  });

  test('sends only one profile save while the first is still pending', async ({
    page,
  }) => {
    let saveCalls = 0;
    await page.route('**/api/v1/me/client-profile', async (route) => {
      saveCalls += 1;
      // Hold a failing response so the save is in flight for a moment.
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          status: 500,
          title: 'Internal Server Error',
        }),
      });
    });

    await page.goto('/profile');
    await page.getByRole('button', { name: 'Edit profile' }).click();
    await page.getByLabel('Phone').fill('+66899999999');

    const confirm = page.getByRole('button', { name: /Confirm|Saving/ });
    await confirm.click();
    // A second submit while the save is pending must be ignored.
    await confirm.click({ force: true });

    await expect(page.getByRole('alert')).toContainText(
      'We could not update your profile. Check the phone number and try again.',
    );
    expect(saveCalls).toBe(1);
  });

  test('restores the saved values when editing is cancelled', async ({
    page,
  }) => {
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Edit profile' }).click();
    await page.getByLabel('First name').fill('Changed');
    await page.getByLabel('Last name').fill('Name');
    await page.getByLabel('Phone').fill('+66999999999');

    await page.getByRole('button', { name: 'Cancel' }).click();

    await expect(page.getByLabel('First name')).toHaveValue('Local');
    await expect(page.getByLabel('Last name')).toHaveValue('Client');
    await expect(
      page.getByRole('button', { name: 'Edit profile' }),
    ).toBeVisible();
  });
});
