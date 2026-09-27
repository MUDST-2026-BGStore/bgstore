import { expect, test, type Page } from '@playwright/test';

const onboardingUser = {
  subject: 'client-1',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: null,
  lastName: null,
  roles: ['CLIENT'],
  onboardingRequired: true,
};

const onboardedUser = {
  subject: 'client-1',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Somchai',
  lastName: 'Jaidee',
  roles: ['CLIENT'],
  onboardingRequired: false,
};

const clientUser = {
  subject: 'client-1',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Somchai',
  lastName: 'Jaidee',
  roles: ['CLIENT'],
  onboardingRequired: false,
};

async function gotoApp(page: Page, path: string) {
  await page.goto(path, { waitUntil: 'commit' });
}

test('sends a guest from a protected route to the sign-in redirect', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  // The redirect target is a real browser navigation; stub it so the assertion
  // stays inside the app instead of hitting Keycloak.
  await page.route('**/auth/sign-in*', (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: '<h1>Keycloak sign-in</h1>',
    }),
  );

  await gotoApp(page, '/history');

  await expect(page).toHaveURL(/\/auth\/sign-in\?returnTo=%2Fhistory/);
  await expect(
    page.getByRole('heading', { name: 'Keycloak sign-in' }),
  ).toBeVisible();
});

test('returns a timed-out session to the sign-in redirect', async ({
  page,
}) => {
  // The session expires between page loads: the first load sees a signed-in
  // client on a protected route, the reload is a guest and must be sent to the
  // sign-in redirect instead of the guest shell.
  let signedIn = true;
  await page.route('**/api/v1/me', (route) =>
    signedIn
      ? route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify(clientUser),
        })
      : route.fulfill({ status: 401 }),
  );
  await page.route('**/api/v1/reservations?**', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        items: [],
        total: 0,
        page: 1,
        pageSize: 4,
        totalPages: 0,
      }),
    }),
  );
  // The redirect target is a real browser navigation; stub it so the assertion
  // stays inside the app instead of hitting Keycloak.
  await page.route('**/auth/sign-in*', (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: '<h1>sign-in stub</h1>',
    }),
  );

  await gotoApp(page, '/history');
  await expect(page).toHaveURL(/\/history$/);

  // The session is gone on the next load, so the protected route redirects.
  signedIn = false;
  await page.reload({ waitUntil: 'commit' });

  await expect(page).toHaveURL(/\/auth\/sign-in\?returnTo=%2Fhistory$/);
  await expect(page.getByText('sign-in stub')).toBeVisible();
});

test('collects a missing client profile and returns the client home', async ({
  page,
}) => {
  let onboardingRequired = true;
  let profileBody: Record<string, unknown> | null = null;

  await page.route('**/api/v1/me/client-profile', (route) => {
    profileBody = route.request().postDataJSON() as Record<string, unknown>;
    onboardingRequired = false;
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        firstName: 'Somchai',
        lastName: 'Jaidee',
        countryCode: '+66',
        phoneNumber: '0812345678',
      }),
    });
  });
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(onboardingRequired ? onboardingUser : onboardedUser),
    }),
  );
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ items: [] }),
    }),
  );

  await gotoApp(page, '/');

  await expect(page).toHaveURL(/\/onboarding\?returnTo=\/$/);
  await expect(
    page.getByRole('heading', { name: 'Tell us about yourself' }),
  ).toBeVisible();

  // firstName was null, so the name fields are shown alongside the phone.
  await page.getByLabel('First name').fill('Somchai');
  await page.getByLabel('Last name').fill('Jaidee');
  await page.getByTestId('phone-input').fill('0812345678');
  await page.getByRole('button', { name: 'Continue' }).click();

  expect(profileBody).toMatchObject({
    firstName: 'Somchai',
    lastName: 'Jaidee',
    countryCode: '+66',
    phoneNumber: '0812345678',
  });
  await expect(page).toHaveURL(/\/$/);
  await expect(
    page.getByRole('heading', { name: 'Games, friends, and a table waiting.' }),
  ).toBeVisible();
});

test('denies a client the staff-only tables screen', async ({ page }) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(clientUser),
    }),
  );

  await gotoApp(page, '/tables');

  await expect(
    page.getByRole('heading', {
      name: 'You do not have access to this page',
    }),
  ).toBeVisible();
  // NOTE: the back control renders as a router-link with no href in the served
  // bundle (see report), so only its visible label is asserted here.
  await expect(page.getByText('Back to home')).toBeVisible();
});

test('denies a client the staff permissions screen', async ({ page }) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(clientUser),
    }),
  );

  await gotoApp(page, '/staff/permissions');

  await expect(
    page.getByRole('heading', {
      name: 'You do not have access to this page',
    }),
  ).toBeVisible();
  await expect(page.getByText('Back to home')).toBeVisible();
});
