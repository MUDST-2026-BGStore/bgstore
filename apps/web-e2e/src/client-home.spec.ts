import { expect, test } from '@playwright/test';

const branches = {
  items: [
    {
      id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
      name: 'Central Rama II',
      address: '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
      opensAt: '09:00',
      closesAt: '19:00',
    },
    {
      id: '3f0d7d5a-9a2b-4a71-8f0e-000000000002',
      name: 'Big C Ratchadamri',
      address: 'ราชดำริ ปทุมวัน กรุงเทพฯ 10330',
      opensAt: '10:00',
      closesAt: '20:00',
    },
  ],
};

function branchItem(index: number) {
  const padded = String(index).padStart(2, '0');
  return {
    id: `3f0d7d5a-9a2b-4a71-8f0e-0000000000${padded}`,
    name: `Branch ${index}`,
    address: `${index} Example Road, Bangkok 10${padded}0`,
    opensAt: '09:00',
    closesAt: '19:00',
  };
}

// Four branches trip the "View all branches" toggle (featuredCount is 3).
const manyBranches = {
  items: [branchItem(1), branchItem(2), branchItem(3), branchItem(4)],
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(branches),
    }),
  );
});

test('renders the editorial client home with real branch data', async ({
  page,
}) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'BGStore' })).toBeAttached();
  await expect(
    page.getByRole('heading', {
      name: 'Games, friends, and a table waiting.',
    }),
  ).toBeVisible();
  await expect(
    page.locator('nav[aria-label="Primary navigation"]'),
  ).toBeVisible();
  await expect(page.locator('.client-home-hero')).toBeVisible();
  await expect(page.getByTestId('home-branch')).toHaveCount(2);
  await expect(page.getByTestId('home-branch').first()).toContainText(
    '09:00–19:00',
  );

  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(page.getByTestId('hero-dot').nth(1)).toHaveAttribute(
    'aria-current',
    'true',
  );
});

test('keeps the client composition usable on a narrow viewport', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  await expect(
    page.getByRole('heading', {
      name: 'Games, friends, and a table waiting.',
    }),
  ).toBeVisible();
  await expect(page.getByTestId('home-branch').first()).toBeVisible();
  await expect(page.locator('.client-nav-pill')).toBeHidden();
});

test('keeps the client branch directory out of the staff table workflow', async ({
  page,
}) => {
  await page.goto('/branches');

  await expect(
    page.locator('nav[aria-label="Primary navigation"]'),
  ).toBeVisible();
  await expect(
    page.locator('nav[aria-label="Staff quick navigation"]'),
  ).toHaveCount(0);
  await expect(page.locator('a[href="/tables"]')).toHaveCount(0);
  await expect(
    page.getByRole('link', { name: 'Book at this branch' }),
  ).toHaveAttribute('href', /^\/reservations\/new\?branch=/);
});

test('walks the hero carousel forward to the last slide and wraps to the first', async ({
  page,
}) => {
  await page.goto('/');

  const dots = page.getByTestId('hero-dot');
  await expect(dots).toHaveCount(3);
  await expect(dots.nth(0)).toHaveAttribute('aria-current', 'true');

  const cards = page.locator('.client-game-card');
  await expect(cards).toHaveCount(3);
  await expect(cards.nth(0)).toHaveClass(/client-game-card--active/);

  // First step into the carousel.
  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(dots.nth(1)).toHaveAttribute('aria-current', 'true');
  await expect(dots.nth(0)).not.toHaveAttribute('aria-current', 'true');
  await expect(cards.nth(1)).toHaveClass(/client-game-card--active/);
  await expect(cards.nth(0)).not.toHaveClass(/client-game-card--active/);

  // Moving again reaches the last slide.
  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(dots.nth(2)).toHaveAttribute('aria-current', 'true');
  await expect(cards.nth(2)).toHaveClass(/client-game-card--active/);

  // Stepping past the last slide wraps back to the first.
  await page.getByRole('button', { name: 'Next slide' }).click();
  await expect(dots.nth(0)).toHaveAttribute('aria-current', 'true');
  await expect(dots.nth(2)).not.toHaveAttribute('aria-current', 'true');
  await expect(cards.nth(0)).toHaveClass(/client-game-card--active/);
});

test('reveals the fourth branch through the view-all toggle when four branches exist', async ({
  page,
}) => {
  await page.unroute('**/api/v1/branches');
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(manyBranches),
    }),
  );

  await page.goto('/');

  // Four branches exist, so the fourth starts hidden behind the toggle.
  await expect(page.getByTestId('home-branch')).toHaveCount(3);
  await expect(page.getByRole('heading', { name: 'Branch 4' })).toBeHidden();

  const toggle = page.getByRole('button', { name: 'View all' });
  await expect(toggle).toBeVisible();
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');

  await toggle.click();

  await expect(page.getByTestId('home-branch')).toHaveCount(4);
  await expect(page.getByRole('heading', { name: 'Branch 4' })).toBeVisible();

  const collapse = page.getByRole('button', { name: 'Show fewer' });
  await expect(collapse).toHaveAttribute('aria-expanded', 'true');
  await collapse.click();

  await expect(page.getByTestId('home-branch')).toHaveCount(3);
  await expect(page.getByRole('heading', { name: 'Branch 4' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'View all' })).toHaveAttribute(
    'aria-expanded',
    'false',
  );
});

test('shows the empty branch state when the API returns no branches', async ({
  page,
}) => {
  await page.unroute('**/api/v1/branches');
  await page.route('**/api/v1/branches', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ items: [] }),
    }),
  );

  await page.goto('/');

  // No branches exist, so the toggle must stay out of the way.
  await expect(page.getByRole('button', { name: 'View all' })).toHaveCount(0);
  await expect(page.getByTestId('home-branch')).toHaveCount(0);
  await expect(page.getByTestId('home-branches-empty')).toBeVisible();
  await expect(page.getByTestId('home-branches-empty')).toHaveText(
    'There are no branches to show yet.',
  );
});

test('offers a retry after a branch load failure and recovers when the API heals', async ({
  page,
}) => {
  await page.unroute('**/api/v1/branches');
  let failing = true;
  let requests = 0;
  await page.route('**/api/v1/branches', (route) => {
    requests += 1;
    return failing
      ? route.fulfill({ status: 500 })
      : route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify(branches),
        });
  });

  await page.goto('/');

  await expect(page.getByTestId('home-branches-error')).toBeVisible();
  await expect(page.getByTestId('home-branches-error')).toHaveAttribute(
    'role',
    'alert',
  );
  await expect(page.getByTestId('home-branches-error')).toHaveText(
    'We could not load the branches.',
  );

  // The API recovers; the retry control refetches and renders the cards.
  failing = false;
  await page.getByRole('button', { name: 'Try again' }).click();

  await expect(page.getByTestId('home-branches-error')).toHaveCount(0);
  await expect(page.getByTestId('home-branch')).toHaveCount(2);
  expect(requests).toBeGreaterThanOrEqual(1);
});

test('shows a guest the login and sign-up links pointing at the app auth start', async ({
  page,
}) => {
  await page.goto('/');

  const login = page.getByRole('link', { name: 'Login' });
  await expect(login).toBeVisible();
  await expect(login).toHaveAttribute('href', '/auth/sign-in?returnTo=%2F');

  const signUp = page.getByRole('link', { name: 'Sign up' });
  await expect(signUp).toBeVisible();
  await expect(signUp).toHaveAttribute('href', '/auth/sign-up?returnTo=%2F');

  // A guest never sees a profile menu.
  await expect(page.getByRole('button', { name: 'Profile' })).toHaveCount(0);
});
