import { expect, test } from '@playwright/test';

test('renders data from the API contract', async ({ page }) => {
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
  await page.getByLabel('Username or email').fill('client@example.test');
  await page.getByLabel('Password', { exact: true }).fill('client-local-only');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page.getByTestId('api-message')).toHaveText('Hello, BGStore!');
});
