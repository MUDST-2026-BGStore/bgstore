import { expect, test, type Page } from './coverage-fixture';

const branchId = '3f0d7d5a-9a2b-4a71-8f0e-000000000001';

const branch = {
  id: branchId,
  name: 'Central Rama II',
  address: '160 Rama II Road, Samae Dam, Bangkok 10150',
  opensAt: '09:00',
  closesAt: '19:00',
  phone: '+66 2 123 4567',
  status: 'ACTIVE',
};

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

async function mockClient(page: Page) {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(clientUser),
    }),
  );
}

async function mockBranches(page: Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ items: [branch] }),
    }),
  );
}

test('opens a branch detail directly, returns to the directory, and books from the directory', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await mockBranches(page);

  await page.goto(`/branches/${branchId}`);

  await expect(
    page.getByRole('heading', { level: 1, name: 'Central Rama II' }),
  ).toBeVisible();
  await expect(page.getByText(branch.address)).toBeVisible();
  await expect(page.getByText('09:00–19:00')).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Book at this branch' }),
  ).toHaveCount(0);

  await page.getByRole('button', { name: 'Back to branches' }).click();
  await expect(page).toHaveURL('/branches');
  await expect(
    page.getByRole('link', { name: 'Book at this branch' }),
  ).toHaveAttribute('href', /^\/reservations\/new\?branch=Central/);
});

test('shows a not-found state for an unknown direct branch route', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await mockBranches(page);

  await page.goto('/branches/unknown-branch');

  await expect(
    page.getByRole('heading', { name: 'Branch not found' }),
  ).toBeVisible();
  await expect(
    page.getByText(
      'The branch you are looking for does not exist or may have been removed.',
    ),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Back to branches', exact: true })
    .click();
  await expect(page).toHaveURL('/branches');
});

test('saves a client phone number and keeps the Keycloak email after reload', async ({
  page,
}) => {
  let persistedUser = structuredClone(clientUser);
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(persistedUser),
    }),
  );
  await page.route('**/api/v1/me/client-profile', async (route) => {
    const body = route.request().postDataJSON() as {
      countryCode: string;
      phoneNumber: string;
    };
    persistedUser = {
      ...persistedUser,
      clientProfile: {
        phone: `${body.countryCode}${body.phoneNumber
          .replaceAll(' ', '')
          .replace(/^0/, '')}`,
        completed: true,
      },
    };
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(persistedUser.clientProfile),
    });
  });

  await page.goto('/profile');
  await expect(page.getByLabel('Email')).toHaveValue('client@example.test');
  await expect(page.getByLabel('Phone')).toHaveValue('+66812345678');

  await page.getByRole('button', { name: 'Edit profile' }).click();
  await page.getByLabel('Phone').fill('089 111 2222');
  await page.getByRole('button', { name: 'Confirm' }).click();

  await expect(
    page.getByText('Your phone number has been updated.'),
  ).toBeVisible();
  await expect(page.getByLabel('Phone')).toHaveValue('+66891112222');
  await expect(page.getByLabel('Email')).toHaveValue('client@example.test');

  await page.reload();
  await expect(page.getByLabel('Phone')).toHaveValue('+66891112222');
  await expect(page.getByLabel('Email')).toHaveValue('client@example.test');
});

test('redirects an unknown client URL to the home route', async ({ page }) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await mockBranches(page);

  await page.goto('/not-a-real-client-route');

  await expect(page).toHaveURL('/');
  await expect(
    page.getByRole('heading', {
      name: 'Games, friends, and a table waiting.',
    }),
  ).toBeVisible();
});

for (const routePath of ['/sessions/active', '/sessions/checkout']) {
  test(`${routePath} shows API errors and recovers after retry`, async ({
    page,
  }) => {
    await mockClient(page);
    let requestCount = 0;
    await page.route('**/api/v1/me/active-session', (route) => {
      requestCount += 1;
      if (requestCount === 1) {
        return route.fulfill({ status: 401 });
      }
      if (requestCount === 2) {
        return route.fulfill({ status: 503 });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(activeSession),
      });
    });

    await page.goto(routePath);

    const errorMessage =
      routePath === '/sessions/active'
        ? 'Your session could not be loaded.'
        : 'Your bill could not be loaded.';
    await expect(page.getByRole('alert')).toContainText(errorMessage);

    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByRole('alert')).toContainText(errorMessage);

    await page.getByRole('button', { name: 'Try again' }).click();
    if (routePath === '/sessions/active') {
      await expect(
        page.getByRole('heading', { name: 'Current Session Details' }),
      ).toBeVisible();
      await expect(page.getByText('Table 12')).toBeVisible();
    } else {
      await expect(
        page.getByRole('heading', { name: 'Settle your session' }),
      ).toBeVisible();
      await expect(page.getByText('Session Bill')).toBeVisible();
      await expect(
        page.getByRole('button', { name: 'Request settlement' }),
      ).toBeVisible();
    }
  });
}
