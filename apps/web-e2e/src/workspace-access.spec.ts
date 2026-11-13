import { expect, test, type Page } from '@playwright/test';

/** Keycloak's default roles for a newly registered customer. */
const CLIENT_ROLES = [
  'default-roles-theparlor',
  'uma_authorization',
  'offline_access',
];
const SAMPLE_CUSTOMER = 'Khun Parlor Member';
const SAMPLE_STORAGE = 'theparlor.account:workspace-sample-reservations';

interface Session {
  subject: string;
  roles: string[];
}

/**
 * Serves the session from `current` and answers branch reservations with
 * `reservations` (a status code or an empty page), as the API would.
 */
async function mockApi(
  page: Page,
  current: { session: Session | null; reservations: number },
) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.fallback();
    const session = current.session;
    if (path === '/api/auth/session')
      return route.fulfill({
        json: session
          ? {
              authenticated: true,
              profile: {
                subject: session.subject,
                username: session.subject,
                roles: session.roles,
                emailVerified: true,
              },
            }
          : { authenticated: false },
      });
    if (path === '/api/auth/login/keycloak')
      return route.fulfill({ body: 'login', contentType: 'text/plain' });
    if (path.startsWith('/api/v1/') && !session && path !== '/api/v1/branches')
      return route.fulfill({ status: 401, json: { status: 401 } });
    if (path === '/api/v1/me')
      return route.fulfill({
        json: {
          id: session?.subject,
          preferences: { preferredLocale: 'en-US' },
        },
      });
    if (path === '/api/v1/branches')
      return route.fulfill({ json: { items: [{ id: 'siam', name: 'Siam' }] } });
    if (path === '/api/v1/branches/siam/reservations')
      return current.reservations === 200
        ? route.fulfill({ json: { items: [] } })
        : route.fulfill({
            status: current.reservations,
            json: { status: current.reservations, code: 'FORBIDDEN' },
          });
    return route.fulfill({ json: { items: [] } });
  });
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
});

test('sends a guest who opens the workspace URL to sign in', async ({
  page,
}) => {
  await mockApi(page, { session: null, reservations: 401 });
  const login = page.waitForRequest('**/api/auth/login/keycloak');
  await page.goto('/workspace/management');
  await login;
  await expect(page.getByText(SAMPLE_CUSTOMER)).toHaveCount(0);
});

test('keeps a newly registered client out of the staff workspace', async ({
  page,
}) => {
  await mockApi(page, {
    session: { subject: 'client-1', roles: CLIENT_ROLES },
    reservations: 403,
  });
  await page.goto('/workspace');
  await expect(
    page.getByRole('link', { name: /^Your profile\b/ }),
  ).toBeVisible();
  await expect(
    page.getByRole('link', { name: /Branch Management/ }),
  ).toHaveCount(0);

  const reservationRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/reservations'))
      reservationRequests.push(request.url());
  });
  for (const path of [
    '/workspace/management',
    '/management',
    '/staff/reservations',
  ]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByText(SAMPLE_CUSTOMER)).toHaveCount(0);
    await expect(page.getByText('Floor Staff')).toHaveCount(0);
  }
  expect(reservationRequests).toEqual([]);
});

test('shows no records or actions when the API refuses the branch', async ({
  page,
}) => {
  await mockApi(page, {
    session: { subject: 'staff-unassigned', roles: ['staff'] },
    reservations: 403,
  });
  await page.goto('/workspace/management');
  await expect(
    page.getByText('You do not have access to this branch.'),
  ).toBeVisible();
  await expect(page.getByText(SAMPLE_CUSTOMER)).toHaveCount(0);
  await expect(
    page.getByRole('region', { name: 'Floor status', exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('button', { name: /check in/i })).toHaveCount(0);
});

test('keeps staff workflows and drops their data when another account signs in', async ({
  page,
}) => {
  const current = {
    session: { subject: 'staff-1', roles: ['staff'] } as Session | null,
    reservations: 200,
  };
  await mockApi(page, current);
  await page.goto('/workspace/management');
  await expect(
    page.getByRole('region', { name: 'Floor status', exact: true }),
  ).toContainText(SAMPLE_CUSTOMER);
  await expect(page.getByText('Floor Staff')).toBeVisible();
  expect(
    await page.evaluate((key) => localStorage.getItem(key), SAMPLE_STORAGE),
  ).not.toBeNull();

  // Another person signs in on this browser (sign-out and sign-in reload the app).
  current.session = { subject: 'client-1', roles: CLIENT_ROLES };
  current.reservations = 403;
  await page.goto('/workspace');
  await expect(
    page.getByRole('link', { name: /^Your profile\b/ }),
  ).toBeVisible();
  expect(
    await page.evaluate((key) => localStorage.getItem(key), SAMPLE_STORAGE),
  ).toBeNull();
  await page.goto('/workspace/management');
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByText(SAMPLE_CUSTOMER)).toHaveCount(0);
});
