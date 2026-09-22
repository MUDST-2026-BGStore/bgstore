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
});
