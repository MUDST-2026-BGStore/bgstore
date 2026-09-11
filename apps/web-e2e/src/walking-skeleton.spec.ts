import { expect, test } from '@playwright/test';

test('renders data from the API contract', async ({ page }) => {
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        subject: 'client-subject',
        username: 'client@example.test',
        email: 'client@example.test',
        firstName: 'Local',
        lastName: 'Client',
        roles: ['CLIENT'],
        clientProfile: { phone: '+66812345678', completed: true },
        onboardingRequired: false,
      }),
    }),
  );
  await page.route('**/api/v1/hello', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        message: 'Hello, BGStore!',
        service: 'bgstore-api',
        database: 'connected',
      }),
    }),
  );

  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'BGStore' })).toBeVisible();
  await expect(page.getByTestId('api-message')).toHaveText('Hello, BGStore!');
});

test('authenticates through the BFF and reaches the real API', async ({
  page,
}) => {
  // A cold Compose stack needs more than the 30s default for the Keycloak
  // round trip plus the BFF session exchange.
  test.setTimeout(60_000);

  test.skip(
    !process.env['BGSTORE_FULL_STACK'],
    'requires the Docker Compose stack',
  );

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.getByLabel('Username').fill('client@example.test');
  await page.getByLabel('Password', { exact: true }).fill('client-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();
  const phoneInput = page.getByTestId('phone-input');
  await expect(phoneInput).toBeVisible();
  await phoneInput.fill('0812345678');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByTestId('api-message')).toHaveText('Hello, BGStore!');
});

test('staff can create a game through the authenticated browser flow', async ({
  page,
}) => {
  test.setTimeout(60_000);

  test.skip(
    !process.env['BGSTORE_FULL_STACK'],
    'requires the Docker Compose stack',
  );

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.getByLabel('Username').fill('staff@example.test');
  await page.getByLabel('Password', { exact: true }).fill('staff-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await page.goto('/games/new');
  await page.getByLabel('Game title (English)').fill('Browser Smoke Game');
  await page.getByLabel('Category').selectOption('family');
  await page.getByLabel('Min players').fill('2');
  await page.getByLabel('Max players').fill('4');
  await page.getByRole('button', { name: 'Add game', exact: true }).click();

  await expect(page).toHaveURL(
    /\/games\?saved=Browser(?:%20|\+)Smoke(?:%20|\+)Game/,
  );
  await expect(page.getByText('Browser Smoke Game')).toBeVisible();
});
