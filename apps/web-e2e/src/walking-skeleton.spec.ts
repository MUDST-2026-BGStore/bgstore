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
  test.skip(
    !process.env['BGSTORE_FULL_STACK'],
    'requires the Docker Compose stack',
  );

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.getByLabel('Username').fill('client@example.test');
  await page.getByLabel('Password', { exact: true }).fill('client-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Thai mobile number').fill('0812345678');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByTestId('api-message')).toHaveText('Hello, BGStore!');
});
