import { expect, test } from '@playwright/test';

// Run against the seeded API through the web proxy, not a catalog fixture.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    if (!localStorage.getItem('theparlor.locale'))
      localStorage.setItem('theparlor.locale', 'en-US');
    if (!localStorage.getItem('theparlor.store-context.v1'))
      localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
  await page.goto('/');
});

test('reads the seeded catalog and preserves filters through UUID product navigation and reload', async ({
  page,
}) => {
  const response = await page.request.get('/api/v1/games?limit=100');
  expect(response.ok()).toBeTruthy();
  const catalog = await response.json();
  const game = catalog.items.find(
    (item: { name: string }) => item.name === 'Blood on the Clocktower',
  );
  expect(game).toBeTruthy();
  await expect(page.locator('.game-card')).toHaveCount(catalog.items.length);
  await page
    .getByRole('searchbox', { name: 'Search games', exact: true })
    .fill('Blood on the Clocktower');
  await expect(page.locator('.game-card')).toHaveCount(1);
  await page
    .getByRole('link', { name: 'View Blood on the Clocktower', exact: true })
    .click();
  await expect(page).toHaveURL(new RegExp(`/games/${game.id}$`));
  await expect(
    page.getByRole('heading', { name: game.name, exact: true }),
  ).toBeVisible();
  await expect(page).toHaveTitle(`${game.name} | The Parlor`);
  await expect(page.locator('.product-media img')).toHaveAttribute(
    'src',
    '/images/games/blood-on-the-clocktower.jpg',
  );
  await expect
    .poll(() =>
      page
        .locator('.product-media img')
        .evaluate((image) => (image as HTMLImageElement).naturalWidth),
    )
    .toBeGreaterThan(0);
  await expect(
    page.locator('.purchase-form, .stock-line, .branch-availability'),
  ).toHaveCount(0);
  await page.goBack();
  await expect(
    page.getByRole('searchbox', { name: 'Search games', exact: true }),
  ).toHaveValue(game.name);
  await expect(page.locator('.game-card')).toHaveCount(1);
  await page.goForward();
  await page.reload();
  await expect(
    page.getByRole('heading', { name: game.name, exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Editions', exact: true }).click();
  await expect(page.locator('.product-accordions')).toContainText(
    'No editions are listed.',
  );
  await page.goto('/games/missing-game');
  await expect(
    page.getByRole('heading', { name: 'Game not found', exact: true }),
  ).toBeVisible();
  await page.goto('/games/00000000-0000-0000-0000-000000000099');
  await expect(
    page.getByRole('heading', { name: 'Game not found', exact: true }),
  ).toBeVisible();
});

for (const status of [401, 503]) {
  test(`catalog HTTP ${status} failures offer retry and recover from the real API`, async ({
    page,
  }) => {
    await page.route('**/api/v1/games?*', (route) =>
      route.fulfill({ status, json: { title: 'Unavailable' } }),
    );
    await page.reload();
    await expect(page.getByRole('alert')).toContainText(
      'Games could not be loaded.',
    );
    await expect(page.locator('.game-card')).toHaveCount(0);
    await page.unroute('**/api/v1/games?*');
    await page.getByRole('button', { name: 'Try again', exact: true }).click();
    await expect(
      page.getByRole('link', { name: 'View Wavelength', exact: true }),
    ).toBeVisible();
  });
}

test('remembers a browsing preference without claiming inventory', async ({
  page,
}) => {
  await page.locator('.context-trigger').click();
  const dialog = page.getByRole('dialog', { name: 'Choose your store' });
  await dialog.locator('input[value=branch]').check();
  await dialog.locator('select[name=store-branch]').selectOption('ari');
  await dialog.getByRole('button', { name: 'Save & continue' }).click();
  await expect(page.locator('.context-trigger')).toContainText('Ari');
  await expect(page.locator('.source-label')).toHaveText('Browsing preference');
  await page.reload();
  await expect(page.locator('.context-trigger')).toContainText('Ari');
  await expect(page.locator('.game-availability, .game-price')).toHaveCount(0);
});

test('keeps Thai and English catalog and product layouts usable from mobile to desktop', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await expect(
    page.getByRole('link', { name: 'View Wavelength', exact: true }),
  ).toBeVisible();
  for (const language of ['EN', 'ไทย']) {
    await page.getByRole('button', { name: language, exact: true }).click();
    await expect(
      page.getByRole('searchbox', {
        name: language === 'EN' ? 'Search games' : 'ค้นหาเกม',
        exact: true,
      }),
    ).toBeVisible();
    for (const width of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect
        .poll(() =>
          page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
          ),
        )
        .toBe(true);
    }
  }
  await page.reload();
  await expect(
    page.getByRole('searchbox', { name: 'ค้นหาเกม', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'EN', exact: true }).click();
  await page
    .getByRole('link', { name: 'View Wavelength', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Wavelength', exact: true }),
  ).toBeVisible();
  for (const language of ['EN', 'ไทย']) {
    await page.getByRole('button', { name: language, exact: true }).click();
    await expect(page.locator('.product-intro')).toContainText(
      language === 'EN' ? 'Give a clue along a spectrum' : 'ให้คำใบ้บนสเกล',
    );
    for (const width of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect
        .poll(() =>
          page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
          ),
        )
        .toBe(true);
    }
  }
  expect(errors).toEqual([]);
});

test('desktop keeps navigation, search and short-viewport filters reachable while the catalog scrolls', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 600 });
  await expect(page.locator('.game-card')).toHaveCount(16);
  await page.evaluate(() => window.scrollTo({ top: 500, behavior: 'instant' }));
  const header = page.locator('.parlor-header');
  await expect
    .poll(async () => Math.round((await header.boundingBox())?.y ?? -1))
    .toBe(0);
  const headerBox = await header.boundingBox();
  const search = await page.locator('.search-bar').boundingBox();
  expect(
    search && headerBox && Math.abs(search.y - headerBox.height) < 2,
  ).toBeTruthy();
  const sidebar = page.locator('.filters');
  const sidebarBox = await sidebar.boundingBox();
  expect(
    sidebarBox && search && sidebarBox.y >= search.y + search.height - 1,
  ).toBeTruthy();
  expect(sidebarBox && sidebarBox.y + sidebarBox.height <= 600).toBeTruthy();
  expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(300);
  expect((await page.locator('.store-context').boundingBox())?.y).toBeLessThan(
    0,
  );
  expect(
    (await page.locator('.results-toolbar').boundingBox())?.y,
  ).toBeLessThan(0);
  await sidebar
    .getByRole('combobox', { name: 'Experience level', exact: true })
    .scrollIntoViewIfNeeded();
  await expect(
    sidebar.getByRole('combobox', { name: 'Experience level', exact: true }),
  ).toBeInViewport();
  await page.getByRole('button', { name: 'Account', exact: true }).click();
  await expect(
    page.getByRole('link', { name: 'Sign in', exact: true }),
  ).toBeVisible();
});

test('mobile filter drawer shares state, traps focus, restores focus and closes on desktop resize', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 700 });
  await expect(page.locator('.game-card')).toHaveCount(16);
  const trigger = page.getByRole('button', { name: 'Filters', exact: true });
  await expect(page.locator('.filters')).toBeHidden();
  await trigger.click();
  const drawer = page.getByRole('dialog', {
    name: 'Filter games',
    exact: true,
  });
  await expect(drawer).toBeVisible();
  await drawer
    .getByRole('combobox', { name: 'Players', exact: true })
    .selectOption('8');
  await expect(
    drawer.getByRole('button', { name: /Show [0-9]+ games/ }),
  ).toBeVisible();
  // The native modal makes the underlying document inert. Browsers may still
  // allow Tab to browser chrome, so do not impose a custom Tab-wrapping rule.
  const showGames = drawer.getByRole('button', { name: /Show [0-9]+ games/ });
  await showGames.focus();
  await page
    .locator('.search-field input')
    .evaluate((input) => (input as HTMLInputElement).focus());
  await expect(showGames).toBeFocused();
  await page.keyboard.press('Tab');
  expect(
    await drawer.evaluate(
      (dialog) =>
        document.activeElement === document.body ||
        dialog.contains(document.activeElement),
    ),
  ).toBeTruthy();
  await drawer
    .getByRole('button', { name: 'Close filters', exact: true })
    .focus();
  await page.keyboard.press('Escape');
  await expect(drawer).toBeHidden();
  await expect(
    page.getByRole('button', { name: 'Filters (1)', exact: true }),
  ).toBeFocused();
  await page.getByRole('button', { name: 'Filters (1)', exact: true }).click();
  await expect(
    drawer.getByRole('combobox', { name: 'Players', exact: true }),
  ).toHaveValue('8');
  await page.setViewportSize({ width: 701, height: 900 });
  await expect(drawer).toBeHidden();
  await expect(page.locator('.filters select[name=players]')).toBeVisible();
  await expect(page.locator('.filters select[name=players]')).toHaveValue('8');
  await page.setViewportSize({ width: 390, height: 700 });
  await page.evaluate(() => window.scrollTo({ top: 400, behavior: 'instant' }));
  await expect
    .poll(async () =>
      Math.round((await page.locator('.parlor-header').boundingBox())?.y ?? -1),
    )
    .toBe(0);
  const headerBox = await page.locator('.parlor-header').boundingBox();
  const searchBox = await page.locator('.search-bar').boundingBox();
  expect(
    headerBox && searchBox && searchBox.y >= headerBox.height - 1,
  ).toBeTruthy();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
});

test('seeded experience and total-time filters cover the complete catalog and localized product details', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect(page.locator('.game-card')).toHaveCount(16);
  await expect(page.locator('.game-grid')).not.toContainText(
    'Experience not specified',
  );
  const experience = page.locator('.filters select[name=difficulty]');
  const duration = page.locator('.filters select[name=time]');
  await experience.selectOption('Advanced');
  await expect(page.locator('.game-card')).toHaveCount(2);
  await expect(page.locator('.game-grid')).toContainText('Cosmic Encounter');
  await expect(page.locator('.game-grid')).toContainText(
    'Blood on the Clocktower',
  );
  await duration.selectOption('120');
  await expect(page.locator('.game-card')).toHaveCount(0);
  await duration.selectOption('180');
  await expect(page.locator('.game-card')).toHaveCount(2);
  await page.getByRole('button', { name: 'Reset all', exact: true }).click();
  await page
    .getByRole('link', { name: 'View Wavelength', exact: true })
    .click();
  await page.getByRole('button', { name: 'Game details', exact: true }).click();
  await expect(page.locator('.product-accordions dl')).toContainText(
    '5 / 2 min',
  );
  await page.getByRole('button', { name: 'ไทย', exact: true }).click();
  await expect(page.locator('.product-intro')).toContainText('ให้คำใบ้บนสเกล');
});
