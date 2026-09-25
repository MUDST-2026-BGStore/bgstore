import { expect, test } from '@playwright/test';

test('renders the staff workspace with a readable operational rail', async ({
  page,
}) => {
  let loggedOut = false;
  await page.route('**/api/v1/me', (route) =>
    route.fulfill(
      loggedOut
        ? { status: 401 }
        : {
            contentType: 'application/json',
            body: JSON.stringify({
              subject: 'staff-subject',
              username: 'staff@example.test',
              email: 'staff@example.test',
              firstName: 'Local',
              lastName: 'Staff',
              roles: ['STAFF'],
              onboardingRequired: false,
            }),
          },
    ),
  );
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

  const sidebar = page.locator('.owner-sidebar');
  await expect(sidebar).toBeVisible();
  await expect(
    sidebar.locator('nav[aria-label="Staff workspace navigation"]'),
  ).toBeVisible();
  await expect(sidebar.locator('nav [aria-current="page"]')).toHaveText(
    'Dashboard',
  );
  const activeItem = sidebar.locator('nav [aria-current="page"]');
  const activeBackground = await activeItem.evaluate(
    (element) => getComputedStyle(element).backgroundColor,
  );
  await activeItem.hover();
  await expect
    .poll(() =>
      activeItem.evaluate(
        (element) => getComputedStyle(element).backgroundColor,
      ),
    )
    .toBe(activeBackground);
  await expect(
    sidebar.getByRole('link', { name: 'Reservations' }),
  ).toHaveAttribute('href', '/staff/reservations');
  await expect(
    page.getByRole('heading', { name: 'Floor overview' }),
  ).toBeVisible();
  await expect(page.locator('.client-nav-pill')).toHaveCount(0);

  await page.route('**/logout', async (route) => {
    loggedOut = true;
    await route.fulfill({
      contentType: 'text/plain',
      body: '/',
    });
  });
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page.getByRole('link', { name: 'Login' })).toBeVisible();
  await expect(page.locator('.owner-sidebar')).toHaveCount(0);
});

test('gives managers a real branch creation form inside the staff workspace', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        subject: 'manager-subject',
        username: 'manager@example.test',
        email: 'manager@example.test',
        firstName: 'Local',
        lastName: 'Manager',
        roles: ['MANAGER'],
        onboardingRequired: false,
      }),
    }),
  );
  await page.route('**/api/v1/branches', async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { name: string };
      await route.fulfill({
        contentType: 'application/json',
        status: 201,
        body: JSON.stringify({
          id: 'branch-new',
          name: body.name,
          address: 'Sukhumvit',
          opensAt: '09:00',
          closesAt: '19:00',
        }),
      });
      return;
    }

    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 'branch-1',
            name: 'Central Rama II',
            address: 'Rama II',
            opensAt: '09:00',
            closesAt: '19:00',
          },
        ],
      }),
    });
  });

  await page.goto('/branches');

  await expect(
    page.getByRole('heading', { name: 'Branch workspace' }),
  ).toBeVisible();
  await expect(page.locator('.owner-sidebar')).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Add a branch' }),
  ).toBeVisible();
  const branchPanel = page.locator('[aria-labelledby="branch-create-title"]');
  await expect(branchPanel).toHaveCSS('align-self', 'start');
  await expect(page.getByText('Opening hours')).toBeVisible();
  await page.getByLabel('Branch name').fill('New Riverside branch');
  await page.getByRole('button', { name: 'Create branch' }).click();
  await expect(page.getByRole('status')).toContainText('Branch created');
  await expect(page.locator('.client-nav-pill')).toHaveCount(0);
});

test('renders a compact staff profile with identity and role context', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        subject: 'manager-subject',
        username: 'manager@example.test',
        email: 'manager@example.test',
        firstName: 'Local',
        lastName: 'Manager',
        roles: ['MANAGER'],
        onboardingRequired: false,
      }),
    }),
  );

  await page.goto('/profile');

  await expect(
    page.getByRole('heading', { name: 'User profile' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Local Manager' }),
  ).toBeVisible();
  await expect(page.getByText('MANAGER', { exact: true })).toHaveCount(2);
  await expect(page.getByText('Managed in Keycloak')).toBeVisible();
  await expect(page.getByText('manager@example.test')).toHaveCount(3);
  await expect(page.locator('.client-nav-pill')).toHaveCount(0);
});

test('keeps branch creation hidden from normal staff', async ({ page }) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        subject: 'staff-subject',
        username: 'staff@example.test',
        email: 'staff@example.test',
        firstName: 'Local',
        lastName: 'Staff',
        roles: ['STAFF'],
        onboardingRequired: false,
      }),
    }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 'branch-1',
            name: 'Central Rama II',
            address: 'Rama II',
            opensAt: '09:00',
            closesAt: '19:00',
          },
        ],
      }),
    }),
  );

  await page.goto('/branches');

  await expect(
    page.getByRole('heading', { name: 'Branch workspace' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Manager access required' }),
  ).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Add a branch' })).toHaveCount(
    0,
  );
  await expect(page.getByRole('button', { name: 'Create branch' })).toHaveCount(
    0,
  );
});
