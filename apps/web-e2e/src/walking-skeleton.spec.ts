import { expect, test, type Page } from '@playwright/test';

const branches = {
  items: [
    {
      id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
      name: 'Central Rama II',
      address: '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
      opensAt: '09:00',
      closesAt: '19:00',
    },
  ],
};

async function stubBranches(page: Page) {
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
}

async function finishClientOnboarding(page: Page) {
  const phoneInput = page.getByTestId('phone-input');
  const homeBranch = page.getByTestId('home-branch').first();
  await expect(phoneInput.or(homeBranch)).toBeVisible();

  if (await phoneInput.isVisible()) {
    await phoneInput.fill('0812345678');
    await page.getByRole('button', { name: 'Continue' }).click();
  }

  await expect(homeBranch).toBeVisible();
}

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
  await stubBranches(page);

  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'BGStore' })).toBeAttached();
  const card = page.getByTestId('home-branch');
  await expect(card.getByRole('heading')).toHaveText('Central Rama II');
  await expect(card).toContainText('09:00–19:00');
});

test('lets a guest browse the home page before signing in', async ({
  page,
}) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await stubBranches(page);

  await page.goto('/');
  await expect(page.getByTestId('home-branch')).toContainText(
    'Central Rama II',
  );
  await expect(page.getByRole('link', { name: 'Sign up' })).toHaveAttribute(
    'href',
    '/oauth2/authorization/keycloak?returnTo=%2F&signup',
  );
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
  await page.getByRole('link', { name: 'Login' }).click();
  await page.getByLabel('Username').fill('client@example.test');
  await page.getByLabel('Password', { exact: true }).fill('client-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await finishClientOnboarding(page);
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
  await page.getByRole('link', { name: 'Login' }).click();
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
  await expect(page.getByTestId('inventory-saved')).toContainText(
    'Browser Smoke Game',
  );
});
