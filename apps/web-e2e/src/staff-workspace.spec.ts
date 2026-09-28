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

const managerSession = {
  subject: 'manager-subject',
  username: 'manager@example.test',
  email: 'manager@example.test',
  firstName: 'Local',
  lastName: 'Manager',
  roles: ['MANAGER'],
  onboardingRequired: false,
};

const branchItems = [
  {
    id: 'branch-1',
    name: 'Central Rama II',
    address: 'Rama II',
    opensAt: '09:00',
    closesAt: '19:00',
  },
];

async function stubUser(page: import('@playwright/test').Page, user: unknown) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(user),
    }),
  );
}

test.describe('branch workspace states', () => {
  test('reports a failed branch load and recovers on retry', async ({
    page,
  }) => {
    let failing = true;
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) => {
      if (failing) {
        return route.fulfill({ status: 500 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      });
    });

    await page.goto('/branches');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText(
      'We could not load the branch workspace.',
    );
    failing = false;
    await alert.getByRole('button', { name: 'Retry' }).click();

    await expect(
      page.getByRole('heading', { name: 'Central Rama II' }),
    ).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('shows the empty copy when no branches exist', async ({ page }) => {
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    );

    await page.goto('/branches');

    await expect(
      page.getByText('Try adjusting your search query.'),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'Central Rama II' }),
    ).toHaveCount(0);
  });

  test('a failed creation clears the earlier success banner', async ({
    page,
  }) => {
    let failCreate = false;
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) => {
      if (route.request().method() === 'POST') {
        if (failCreate) {
          return route.fulfill({ status: 400 });
        }
        const body = route.request().postDataJSON() as { name: string };
        return route.fulfill({
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
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      });
    });

    await page.goto('/branches');

    await page.getByLabel('Branch name').fill('First branch');
    await page.getByRole('button', { name: 'Create branch' }).click();
    await expect(page.getByRole('status')).toContainText('Branch created');

    failCreate = true;
    await page.getByLabel('Branch name').fill('Second branch');
    await page.getByRole('button', { name: 'Create branch' }).click();

    await expect(page.getByRole('alert')).toContainText(
      'The branch could not be created',
    );
    await expect(page.getByRole('status')).toHaveCount(0);
  });

  test('enforces the name and address length limits', async ({ page }) => {
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      }),
    );

    await page.goto('/branches');

    await expect(page.locator('#branch-name')).toHaveAttribute(
      'maxlength',
      '120',
    );
    await expect(page.locator('#branch-address')).toHaveAttribute(
      'maxlength',
      '300',
    );
  });
});

test.describe('staff permissions states', () => {
  const assignments = {
    items: [
      {
        staffSubject: 'staff-subject',
        displayName: 'Local Staff',
        email: 'staff@example.test',
        branchIds: [],
      },
    ],
  };

  test('reports a failed assignment load and recovers on retry', async ({
    page,
  }) => {
    let failing = true;
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      }),
    );
    await page.route('**/api/v1/staff/branch-assignments', (route) => {
      if (failing) {
        return route.fulfill({ status: 500 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(assignments),
      });
    });

    await page.goto('/staff/permissions');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText(
      'Staff accounts could not be loaded. Try again.',
    );
    failing = false;
    await alert.getByRole('button', { name: 'Try again' }).click();

    await expect(
      page.getByRole('button', { name: /Local Staff/ }),
    ).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('shows the empty staff copy when nobody has signed in', async ({
    page,
  }) => {
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      }),
    );
    await page.route('**/api/v1/staff/branch-assignments', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      }),
    );

    await page.goto('/staff/permissions');

    await expect(
      page.getByText('No staff accounts have signed in yet.'),
    ).toBeVisible();
  });

  test('reports a failed save and keeps the form open', async ({ page }) => {
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      }),
    );
    await page.route('**/api/v1/staff/branch-assignments/*', (route) =>
      route.fulfill({ status: 500 }),
    );
    await page.route('**/api/v1/staff/branch-assignments', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(assignments),
      }),
    );

    await page.goto('/staff/permissions');
    await page.getByRole('button', { name: /Local Staff/ }).click();
    await page.getByRole('checkbox', { name: 'Central Rama II' }).check();
    await page.getByRole('button', { name: 'Save assignments' }).click();

    await expect(page.getByRole('alert')).toContainText(
      'Assignments could not be saved. Try again.',
    );
    await expect(page.getByRole('status')).toHaveCount(0);
  });

  test('sends exactly one PUT while the save request is pending', async ({
    page,
  }) => {
    const puts: string[] = [];
    await stubUser(page, managerSession);
    await page.route('**/api/v1/branches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: branchItems }),
      }),
    );
    await page.route('**/api/v1/staff/branch-assignments/*', async (route) => {
      puts.push(route.request().url());
      await new Promise((resolve) => setTimeout(resolve, 500));
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      });
    });
    await page.route('**/api/v1/staff/branch-assignments', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(assignments),
      }),
    );

    await page.goto('/staff/permissions');
    await page.getByRole('button', { name: /Local Staff/ }).click();
    await page.getByRole('checkbox', { name: 'Central Rama II' }).check();

    // The label flips to "Saving…" while the mutation runs, so hold the
    // element rather than re-resolving by its changing accessible name.
    const save = page.locator('form button[type="submit"]');
    await save.click();
    // While the mutation runs the button is disabled, so an impatient second
    // click must not issue a second PUT.
    await expect(save).toBeDisabled();
    await expect(save).toBeEnabled();
    await expect(page.getByRole('status')).toContainText(
      'Branch assignments saved.',
    );
    expect(puts).toHaveLength(1);
  });
});
