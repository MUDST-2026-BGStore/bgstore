import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Automated WCAG checks on the customer pages. Serious and critical findings fail.
async function audit(page: Page) {
  // Measure the settled page: entrance fades lower contrast while they run.
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .every((animation) => animation.playState !== 'running'),
  );
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const blocking = results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => ({
      rule: v.id,
      impact: v.impact,
      help: v.help,
      targets: v.nodes.slice(0, 5).map((node) => node.target.join(' ')),
    }));
  expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([]);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
});

for (const locale of ['en-US', 'th-TH'] as const) {
  test(`catalog has no serious accessibility violations (${locale})`, async ({
    page,
  }) => {
    await page.addInitScript((value) => {
      localStorage.setItem('theparlor.locale', value);
    }, locale);
    await page.goto('/');
    await expect(page.locator('.game-card').first()).toBeVisible();
    await audit(page);
  });
}

test('product page has no serious accessibility violations', async ({
  page,
}) => {
  await page.goto('/');
  await page.locator('.game-card a').first().click();
  await expect(page.locator('.product-details h1')).toBeVisible();
  await audit(page);
});

test('branch locations have no serious accessibility violations', async ({
  page,
}) => {
  await page.goto('/branches');
  await expect(page.getByRole('list', { name: 'Branches' })).toBeVisible();
  await audit(page);
});

test('cart has no serious accessibility violations', async ({ page }) => {
  await page.goto('/cart');
  await expect(page.locator('.shopee-cart-page')).toBeVisible();
  // One main landmark per page: the layout owns it.
  await expect(page.getByRole('main')).toHaveCount(1);
  await audit(page);
});

test('sign-in page has no serious accessibility violations', async ({
  page,
}) => {
  await page.goto('/login');
  await expect(page.locator('#username')).toBeVisible();
  await audit(page);
});
