import { expect, test, type Page } from '@playwright/test';

type Account = {
  username: string;
  password: string;
  client?: boolean;
};

const clientAccount: Account = {
  username: 'client@example.test',
  password: 'ClientLocalOnly9!',
  client: true,
};

const staffAccount: Account = {
  username: 'staff@example.test',
  password: 'StaffLocalOnly9!',
};

const managerAccount: Account = {
  username: 'manager@example.test',
  password: 'ManagerLocalOnly9!',
};

// The seeded client account's profile: the BFF pre-fills booking step 1 with
// it, and reservation records created by the client carry these values.
const clientBookingName = 'Local Client';

// Serial-suite state for the game-lifecycle tests below.
const retiredGame = { title: '', detailUrl: '' };

async function signIn(
  page: Page,
  account: Account,
  options: { direct?: boolean } = {},
) {
  await page.goto('/');
  if (options.direct) {
    await page.goto('/auth/sign-in?returnTo=%2F');
  } else {
    await page.getByRole('link', { name: 'Login' }).click();
  }
  await expect(page.locator('#username')).toBeVisible();
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  if (account.client) {
    const phoneInput = page.getByTestId('phone-input');
    const homeBranch = page.getByTestId('home-branch').first();
    await expect(phoneInput.or(homeBranch)).toBeVisible();

    if (await phoneInput.isVisible()) {
      await phoneInput.fill('0812345678');
      await page.getByRole('button', { name: 'Continue' }).click();
    }
  }

  if (account.client) {
    await expect(page.getByRole('heading', { name: 'BGStore' })).toBeAttached();
  } else {
    await expect(
      page.getByRole('heading', { name: 'Floor overview' }),
    ).toBeAttached();
  }
}

function requireFullStack() {
  test.skip(
    !process.env['BGSTORE_FULL_STACK'],
    'requires the Docker Compose stack',
  );
}

function firstItem<T>(items: T[]): T {
  const item = items[0];
  if (!item) throw new Error('The local stack returned no branches.');
  return item;
}

test.describe('real full-stack browser flows', () => {
  test.describe.configure({ mode: 'serial' });

  test('client signs in through Keycloak, browses real branches, and logs out', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);

    await expect(page.getByTestId('home-branch').first()).toBeVisible();
    await expect(
      page.locator('nav[aria-label="Primary navigation"]'),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: 'History' })).toBeVisible();

    await page.goto('/branches');
    await expect(
      page.getByRole('heading', { name: 'Branch directory' }),
    ).toBeVisible();
    await expect(page.getByText('Central Rama II')).toBeVisible();
    await expect(
      page.locator('nav[aria-label="Staff quick navigation"]'),
    ).toHaveCount(0);

    await page.goto('/');
    await page.getByRole('button', { name: 'Profile' }).click();
    await expect(page.getByRole('menuitem', { name: 'Profile' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: 'Log out' })).toBeVisible();
    await page.getByRole('menuitem', { name: 'Log out' }).click();

    await expect(page.getByRole('link', { name: 'Login' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Profile' })).toHaveCount(0);

    await page.getByRole('link', { name: 'Login' }).click();
    await expect(page.locator('#username')).toBeVisible();
  });

  test('client session is rejected by the staff API and staff UI route', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);

    const floorResponse = await page.request.get('/api/v1/floor-overview');
    expect(floorResponse.status()).toBe(403);

    await page.goto('/tables');
    await expect(
      page.getByRole('heading', {
        name: 'You do not have access to this page',
      }),
    ).toBeVisible();
    await expect(
      page.locator('nav[aria-label="Primary navigation"]'),
    ).toBeVisible();
    await expect(page.locator('.owner-sidebar')).toHaveCount(0);
  });

  test('manager assigns a branch before staff operational flows run', async ({
    page,
    browser,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    // A newly imported local staff account is intentionally unassigned. Sign
    // in once so the BFF synchronizes its immutable subject into the database.
    await signIn(page, staffAccount);
    const staffUser = (await (await page.request.get('/api/v1/me')).json()) as {
      subject: string;
    };

    // Keep the manager's identity-provider cookies separate from the staff
    // session in the test page. Keycloak otherwise silently reuses the staff
    // SSO session and never renders the manager login form.
    const managerContext = await browser.newContext({
      baseURL: process.env['BASE_URL'] || 'http://localhost:4300',
    });
    const managerPage = await managerContext.newPage();
    try {
      await signIn(managerPage, managerAccount, { direct: true });
      const branchesResponse =
        await managerPage.request.get('/api/v1/branches');
      const branches = (await branchesResponse.json()) as {
        items: Array<{ id: string; name: string }>;
      };
      const branch = firstItem(
        branches.items.filter((item) => item.name === 'Central Rama II'),
      );
      const branchId = branch.id;

      const assignment = await managerPage.evaluate(
        async ({ subject, branchId }) => {
          const csrfToken = document.cookie
            .split('; ')
            .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
            ?.split('=')[1];
          const response = await fetch(
            `/api/v1/staff/branch-assignments/${encodeURIComponent(subject)}`,
            {
              method: 'PUT',
              credentials: 'include',
              headers: {
                'Content-Type': 'application/json',
                ...(csrfToken
                  ? { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken) }
                  : {}),
              },
              body: JSON.stringify({ branchIds: [branchId] }),
            },
          );
          return { status: response.status };
        },
        { subject: staffUser.subject, branchId },
      );

      expect(assignment.status).toBe(200);
    } finally {
      await managerContext.close();
    }
  });

  test('staff session reaches the real floor API and workspace', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);

    const floorResponse = await page.request.get('/api/v1/floor-overview');
    expect(floorResponse.status()).toBe(200);
    expect((await floorResponse.json()).items).toBeInstanceOf(Array);

    await page.goto('/');
    await expect(
      page.getByRole('heading', { name: 'Floor overview' }),
    ).toBeVisible();
    await expect(
      page.locator('nav[aria-label="Staff workspace navigation"]'),
    ).toBeVisible();
    await expect(page.locator('.client-nav-pill')).toHaveCount(0);
  });

  test('staff can open tables without a load error and the sidebar follows the route', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    const branchResponse = await page.request.get('/api/v1/branches');
    expect(branchResponse.status()).toBe(200);
    const branch = (await branchResponse.json()).items[0] as { name: string };
    const tableResponse = await page.request.get(
      `/api/v1/tables?branch=${encodeURIComponent(branch.name)}&page=1&pageSize=100`,
    );
    expect(tableResponse.status()).toBe(200);
    await page.getByRole('link', { name: 'Tables' }).click();

    await expect(page).toHaveURL(/\/tables$/);
    await expect(
      page.locator('.owner-sidebar [aria-current="page"]'),
    ).toHaveText('Tables');
    await expect(
      page.getByRole('heading', { name: 'Table management' }),
    ).toBeVisible();
    await expect(page.getByText('We could not load the tables.')).toHaveCount(
      0,
    );
  });

  test('manager can create a branch through the real API and workspace', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, managerAccount);
    await page.goto('/branches');

    const branchName = `E2E branch ${Date.now()}`;
    await expect(
      page.getByRole('heading', { name: 'Branch workspace' }),
    ).toBeVisible();
    await page.getByLabel('Branch name').fill(branchName);
    await page.getByRole('button', { name: 'Create branch' }).click();

    await expect(page.getByRole('status')).toContainText('Branch created');
    await expect(page.getByText(branchName)).toBeVisible();
  });

  test('normal staff cannot create a branch through the real API', async ({
    page,
  }) => {
    requireFullStack();

    await signIn(page, staffAccount);
    const response = await page.evaluate(async () => {
      const csrfToken = document.cookie
        .split('; ')
        .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];
      const result = await fetch('/api/v1/branches', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(csrfToken
            ? { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken) }
            : {}),
        },
        body: JSON.stringify({ name: 'Staff must not create this branch' }),
      });
      return { status: result.status };
    });

    expect(response.status).toBe(403);
  });

  test('staff can complete the reservation workflow in the real app shell', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/reservations/new');

    await expect(
      page.getByRole('heading', { name: 'Create reservation' }),
    ).toBeAttached();
    await page.locator('#reservation-first-name').fill('Jane');
    await page.locator('#reservation-last-name').fill('Doe');
    await page.locator('#reservation-nickname').fill('Janie');
    await page.locator('#reservation-phone').fill('088-888-8888');
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Next' }).click();

    const availableTable = page
      .locator('button.table-option:not([disabled])')
      .first();
    await expect(availableTable).toBeVisible();
    await availableTable.click();
    await page.getByRole('button', { name: 'Next' }).click();
    await expect(page.getByText('Jane Doe (Janie)')).toBeVisible();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(
      page.getByRole('heading', { name: 'Reservation details ready' }),
    ).toBeVisible();
  });

  test('client sees no session to settle until staff check them in', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);

    await page.goto('/sessions/active');
    await expect(
      page.getByRole('heading', { name: 'No active session' }),
    ).toBeVisible();

    await page.goto('/pay-session');
    await expect(
      page.getByRole('heading', { name: 'Settle your session' }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'No active session' }),
    ).toBeVisible();
  });

  test('staff check a reserved party in from the session console', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/staff/sessions');

    await expect(
      page.getByRole('heading', { name: 'Session console' }),
    ).toBeVisible();

    // The reservation workflow above left a party waiting to start.
    const checkIn = page.getByRole('button', { name: 'Check in' }).first();
    await expect(checkIn).toBeVisible();
    await checkIn.click();

    await expect(
      page.getByRole('button', { name: 'Check out' }).first(),
    ).toBeVisible();
  });

  test('staff check out through the bogus gateway and read the settlement', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/staff/sessions');

    await page.getByRole('button', { name: 'Check out' }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.locator('#checkout-amount').fill('240');
    await dialog.locator('#checkout-method').selectOption('PromptPay');
    await dialog.getByRole('button', { name: 'Close session' }).click();

    // The dialog stays open and confirms the gateway settlement.
    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await expect(dialog.getByText('Total settled')).toBeVisible();
    // Intl formats THB with a non-breaking space before the amount.
    await expect(dialog.getByText(/240\.00/)).toBeVisible();
    await expect(dialog.getByText('Gateway')).toBeVisible();
    await expect(dialog.getByText(/^bogus-/)).toBeVisible();

    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(dialog).toBeHidden();
    // The completed session left the queue, so nothing is in play anymore.
    await expect(page.getByRole('button', { name: 'Check out' })).toHaveCount(
      0,
    );
  });

  test('staff settle by card through the bogus gateway, decline first', async ({
    page,
  }) => {
    test.setTimeout(90_000);
    requireFullStack();

    // This test owns its reservation: the serial flow above consumed the
    // shared one, so card charges get a fresh party to settle.
    await signIn(page, staffAccount);
    await page.goto('/reservations/new');
    await expect(
      page.getByRole('heading', { name: 'Create reservation' }),
    ).toBeAttached();
    await page.locator('#reservation-first-name').fill('Card');
    await page.locator('#reservation-last-name').fill('Tester');
    await page.locator('#reservation-nickname').fill('Cardy');
    await page.locator('#reservation-phone').fill('087-777-7777');
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    const cardTable = page
      .locator('button.table-option:not([disabled])')
      .first();
    await expect(cardTable).toBeVisible();
    await cardTable.click();
    await page.getByRole('button', { name: 'Next' }).click();
    await expect(page.getByText('Card Tester (Cardy)')).toBeVisible();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(
      page.getByRole('heading', { name: 'Reservation details ready' }),
    ).toBeVisible();

    await page.goto('/staff/sessions');
    const checkIn = page.getByRole('button', { name: 'Check in' }).first();
    await expect(checkIn).toBeVisible();
    await checkIn.click();

    const checkOut = page.getByRole('button', { name: 'Check out' }).first();
    await expect(checkOut).toBeVisible();
    await checkOut.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.locator('#checkout-amount').fill('180');
    await dialog.locator('#checkout-method').selectOption('Card');
    await expect(dialog.locator('img[src*="unknown"]')).toBeVisible();
    await dialog.locator('#checkout-card-number').fill('4242424242424242');
    // The network logo follows the typed leading digits.
    await expect(dialog.locator('img[src*="visa"]')).toBeVisible();
    await dialog.locator('#checkout-card-expiry').fill('12/29');
    // CVV 111 is the demo knob for an insufficient-funds decline.
    await dialog.locator('#checkout-card-cvv').fill('111');
    await dialog.getByRole('button', { name: 'Close session' }).click();

    // The decline names the reason and the dialog stays open for a retry.
    // Assert on the alert itself: the CVV knob hint also mentions the reasons.
    const decline = dialog.getByText(/The card network declined the charge/);
    await expect(decline).toBeVisible();
    await expect(decline).toContainText(/insufficient funds/i);
    await expect(
      dialog.getByRole('button', { name: 'Close session' }),
    ).toBeVisible();

    // Any other CVV approves the charge.
    await dialog.locator('#checkout-card-cvv').fill('424');
    await dialog.getByRole('button', { name: 'Close session' }).click();

    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await expect(dialog.getByText('Total settled')).toBeVisible();
    await expect(dialog.getByText(/180\.00/)).toBeVisible();
    // The receipt prints the network and the masked number only.
    await expect(dialog.getByText(/•••• 4242/)).toBeVisible();
    await expect(dialog.locator('img[src*="visa"]')).toBeVisible();
  });

  test('client books at a branch from the directory and finds it in history', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);
    await expect(page.getByRole('link', { name: 'Reserve' })).toBeVisible();

    // The branch directory is the client's real entry point into booking.
    await page.goto('/branches');
    await page.getByRole('button', { name: /Central Rama II/ }).click();
    const bookLink = page.getByRole('link', { name: 'Book at this branch' });
    await expect(bookLink).toBeVisible();
    await bookLink.click();
    await expect(page).toHaveURL(/\/reservations\/new/);

    // Client mode pre-fills the party from the signed-in account.
    await expect(page.locator('#reservation-first-name')).toHaveValue('Local');
    await expect(page.locator('#reservation-last-name')).toHaveValue('Client');
    await expect(page.locator('#reservation-phone')).toHaveValue(
      '+66812345678',
    );
    await page.locator('#reservation-nickname').fill('Booker');
    await page.getByRole('button', { name: 'Next' }).click();
    // The walk-in tests above book the default 09:00 window; booking a later
    // slot keeps this suite re-runnable against a database that already has
    // yesterday's bookings.
    await page.getByLabel('Start time').selectOption('13:00');
    await page.getByLabel('End time').selectOption('15:00');
    await page.getByRole('button', { name: 'Next' }).click();

    const table = page.locator('button.table-option:not([disabled])').first();
    await expect(table).toBeVisible();
    await table.click();
    await page.getByRole('button', { name: 'Next' }).click();
    await expect(page.getByText('Local Client (Booker)')).toBeVisible();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(
      page.getByRole('heading', { name: 'Reservation details ready' }),
    ).toBeVisible();

    await page.goto('/history');
    // Scope by the status badge so reruns against a dirty database still
    // resolve to this test's fresh booking.
    const card = page
      .getByTestId('reservation-card')
      .filter({ hasText: 'Reserved' })
      .first();
    await expect(card).toBeVisible();
    await expect(card.getByTestId('status-badge')).toHaveText('Reserved');
  });

  test('staff check the client booking in at the session console', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/staff/sessions');

    const row = page
      .getByRole('row')
      .filter({ hasText: clientBookingName })
      .first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Check in' }).click();
    await expect(row.getByRole('button', { name: 'Check out' })).toBeVisible();
  });

  test('client requests staff assistance and an early end on the live session', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);
    await page.goto('/sessions/active');

    await expect(
      page.getByRole('button', { name: 'Call Staff' }),
    ).toBeVisible();

    await page.getByRole('button', { name: 'Call Staff' }).click();
    await expect(
      page.getByText('A staff member has been asked to come to your table.'),
    ).toBeVisible();

    await page.getByRole('button', { name: 'End Playing' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Request to end' }).click();
    await expect(
      page.getByText('Your request to end the session was sent.'),
    ).toBeVisible();
  });

  test('staff settle the assisted session through the gateway', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/staff/sessions');

    const row = page
      .getByRole('row')
      .filter({ hasText: clientBookingName })
      .first();
    await row.getByRole('button', { name: 'Check out' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.locator('#checkout-amount').fill('300');
    await dialog.locator('#checkout-method').selectOption('PromptPay');
    await dialog.getByRole('button', { name: 'Close session' }).click();

    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await expect(dialog.getByText('Total settled')).toBeVisible();
    await expect(dialog.getByText(/300\.00/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Done' }).click();
    await expect(dialog).toBeHidden();
  });

  test('client history shows the completed, settled visit', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);
    await page.goto('/history');
    await page
      .getByTestId('reservation-card')
      .filter({ hasText: 'Completed' })
      .first()
      .getByTestId('view-button')
      .click();

    await expect(page.getByTestId('field-name')).toHaveValue(clientBookingName);
    await expect(page.getByTestId('field-checkin')).not.toHaveValue('-');
    await expect(page.getByTestId('field-checkout')).not.toHaveValue('-');
  });

  test('client books again and cancels it from history', async ({ page }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, clientAccount);
    await page.goto('/reservations/new?branch=Central%20Rama%20II');
    await page.locator('#reservation-nickname').fill('Canceller');
    await page.getByRole('button', { name: 'Next' }).click();
    // Same later slot as the first client booking above: reruns must not
    // compete with the walk-in tests for the default 09:00 window.
    await page.getByLabel('Start time').selectOption('13:00');
    await page.getByLabel('End time').selectOption('15:00');
    await page.getByRole('button', { name: 'Next' }).click();
    const table = page.locator('button.table-option:not([disabled])').first();
    await expect(table).toBeVisible();
    await table.click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(
      page.getByRole('heading', { name: 'Reservation details ready' }),
    ).toBeVisible();

    await page.goto('/history');
    await page.getByTestId('tab-reserved').click();
    const card = page.getByTestId('reservation-card').first();
    await expect(card.getByTestId('status-badge')).toHaveText('Reserved');
    await card.getByTestId('view-button').click();

    const cancelButton = page.getByTestId('cancel-button');
    await expect(cancelButton).toBeVisible();
    await cancelButton.click();

    const modal = page.getByTestId('cancel-modal');
    await expect(modal).toBeVisible();
    // Keeping the reservation leaves everything unchanged.
    await modal.getByRole('button', { name: 'Keep Reservation' }).click();
    await expect(modal).toBeHidden();
    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Reserved',
    );

    await cancelButton.click();
    await expect(modal).toBeVisible();
    await modal.getByRole('button', { name: 'Confirm Cancellation' }).click();
    await expect(page.getByTestId('detail-status-badge')).toHaveText(
      'Cancelled',
    );
    await expect(cancelButton).toHaveCount(0);
  });

  test('staff add a game to the real inventory', async ({ page }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/games/new');

    const title = `Retire E2E ${Date.now()}`;
    retiredGame.title = title;
    await page.getByLabel('Game title (English)').fill(title);
    await page.getByLabel('Category').selectOption('family');
    await page.getByLabel('Min players').fill('2');
    await page.getByLabel('Max players').fill('4');
    await page.getByRole('button', { name: 'Add game', exact: true }).click();

    await expect(page).toHaveURL(/\/games\?saved=/);
    const row = page.getByRole('row').filter({ hasText: title }).first();
    await expect(row).toBeVisible();
    // Remember the detail path for the client-side assertions below.
    const editHref = await row.locator('a').first().getAttribute('href');
    retiredGame.detailUrl = (editHref ?? '').replace(/\/edit$/, '');
    expect(retiredGame.detailUrl).toMatch(/^\/games\//);
  });

  test('client finds the new game in the catalogue', async ({ page }) => {
    test.setTimeout(60_000);
    requireFullStack();
    test.skip(
      !retiredGame.title,
      'runs after the inventory test in this serial suite',
    );

    await signIn(page, clientAccount);
    await page.goto('/games');

    const card = page
      .getByTestId('catalogue-card')
      .filter({ hasText: retiredGame.title });
    await expect(card).toBeVisible();
    await card.click();
    expect(page.url()).toContain(retiredGame.detailUrl);

    // The game was created without branch stock.
    await expect(page.getByTestId('catalogue-availability')).toHaveText(
      'Not in store yet',
    );
  });

  test('staff retire the game and it leaves the client catalogue', async ({
    page,
    browser,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();
    test.skip(
      !retiredGame.title,
      'runs after the inventory test in this serial suite',
    );

    await signIn(page, staffAccount);
    await page.goto('/games');
    const row = page
      .getByRole('row')
      .filter({ hasText: retiredGame.title })
      .first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Delete' }).click();

    // Retired games stay in the inventory with the retired status badge.
    await expect(row.getByText('Retired')).toBeVisible();

    // The staff session cannot browse the client catalogue, so check the
    // client's view in its own signed-in context.
    const clientContext = await browser.newContext({
      baseURL: process.env['BASE_URL'] || 'http://localhost:4200',
    });
    const clientPage = await clientContext.newPage();
    try {
      await signIn(clientPage, clientAccount);
      await clientPage.goto('/games');
      await expect(
        clientPage
          .getByTestId('catalogue-card')
          .filter({ hasText: retiredGame.title }),
      ).toHaveCount(0);

      // A direct link still resolves, marked as no longer offered.
      await clientPage.goto(retiredGame.detailUrl);
      await expect(clientPage.getByTestId('catalogue-availability')).toHaveText(
        'No longer offered',
      );
    } finally {
      await clientContext.close();
    }
  });

  test('staff cannot open the unlinked permissions console', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, staffAccount);
    await page.goto('/staff/permissions');
    await expect(
      page.getByRole('heading', {
        name: 'You do not have access to this page',
      }),
    ).toBeVisible();
  });

  test('manager manages branch assignments from the permissions console', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    await signIn(page, managerAccount);
    await page.goto('/staff/permissions');
    await expect(
      page.getByRole('heading', { name: 'Staff branch access' }),
    ).toBeVisible();

    const staffCard = page
      .locator('button.staff-branch-card')
      .filter({ hasText: 'staff@example.test' });
    await expect(staffCard).toBeVisible();
    await staffCard.click();

    // The earlier assignment left Central Rama II checked for this staff
    // member; add a second branch, save, then restore the original state.
    // `exact` matters: 'Big C Rama I' is a prefix of 'Big C Rama IX'.
    const central = page.getByRole('checkbox', {
      name: 'Central Rama II',
      exact: true,
    });
    const extra = page.getByRole('checkbox', {
      name: 'Big C Rama I',
      exact: true,
    });
    await expect(central).toBeChecked();
    await extra.check();
    await page.getByRole('button', { name: 'Save assignments' }).click();
    await expect(page.getByText('Branch assignments saved.')).toBeVisible();

    await extra.uncheck();
    await page.getByRole('button', { name: 'Save assignments' }).click();
    await expect(page.getByText('Branch assignments saved.')).toBeVisible();
    await expect(extra).not.toBeChecked();
    await expect(central).toBeChecked();
  });

  test('the client UI renders Thai for a Thai browser locale', async ({
    browser,
  }) => {
    test.setTimeout(60_000);
    requireFullStack();

    const context = await browser.newContext({
      baseURL: process.env['BASE_URL'] || 'http://localhost:4200',
      locale: 'th-TH',
    });
    const page = await context.newPage();
    try {
      await page.goto('/');
      await page.goto('/auth/sign-in?returnTo=%2F');
      // The Keycloak form may render its labels in Thai too, so address the
      // fields by id instead of their visible label text.
      await page.locator('#username').fill(clientAccount.username);
      await page.locator('#password').fill(clientAccount.password);
      await page.getByRole('button', { name: /Sign in|เข้าสู่ระบบ/ }).click();

      const phoneInput = page.getByTestId('phone-input');
      const homeHeading = page.getByRole('heading', { name: 'BGStore' });
      await expect(phoneInput.or(homeHeading)).toBeVisible();
      if (await phoneInput.isVisible().catch(() => false)) {
        await phoneInput.fill('0812345678');
        await page.getByRole('button', { name: 'Continue' }).click();
      }

      // The SPA picked the locale from the browser, not from an account
      // setting: the client navigation renders its Thai labels.
      await expect(homeHeading).toBeAttached();
      await expect(page.getByRole('link', { name: 'ประวัติ' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'การจอง' })).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
