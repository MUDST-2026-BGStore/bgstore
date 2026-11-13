import { expect, test, type Locator } from '@playwright/test';

test.use({ viewport: { width: 1440, height: 1000 } });

test.beforeEach(async ({ page }) => {
  // Keep the dashboard's existing records while isolating UI checks from backend writes.
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    // Vite serves source modules under features/*/api/ too.
    if (!path.startsWith('/api/')) return route.fallback();
    if (path === '/api/auth/session') {
      await route.fulfill({
        json: {
          authenticated: true,
          profile: {
            subject: 'operator',
            username: 'Operator',
            roles: ['admin'],
            emailVerified: true,
          },
        },
      });
    } else if (path === '/api/v1/me') {
      await route.fulfill({
        json: { id: 'operator', preferences: { preferredLocale: 'en-US' } },
      });
    } else if (path === '/api/v1/branches') {
      await route.fulfill({ json: { items: [{ id: 'siam', name: 'Siam' }] } });
    } else {
      await route.fulfill({ json: { items: [] } });
    }
  });
  await page.goto('/workspace/management');
  await expect(
    page.getByRole('combobox', { name: 'Branch', exact: true }),
  ).toHaveValue('siam');
});

test('keeps the complete dashboard information visible together on desktop', async ({
  page,
}) => {
  const summary = page.getByRole('region', { name: 'Branch summary' });
  await expect(summary.getByRole('button')).toHaveCount(4);
  for (const [label, value] of [
    ['Bookings', /^4$/],
    ['Tables in play', /^2\s*\/\s*6$/],
    ['Awaiting arrival', /^1$/],
    ['Active sessions', /^2$/],
  ] as const) {
    const metric = summary.getByRole('button', {
      name: new RegExp(`^${label}`),
    });
    await expect(metric.getByText(value)).toBeVisible();
    await expect(metric).toBeInViewport({ ratio: 1 });
  }
  await expect(summary).toContainText('3 free · 1 reserved');
  await expect(summary).toContainText('Next');

  const floor = page.getByRole('region', { name: 'Floor status', exact: true });
  const rows = floor.locator('tbody tr');
  await expect(rows).toHaveCount(6);
  for (const [index, fields] of [
    [0, ['In play', 'Khun Parlor Member', '4 guests', 'Azul', 'min', '฿']],
    [1, ['In play', 'Walk-in Guests', '3 guests', 'Wingspan', 'min', '฿']],
    [2, ['Reserved', 'Khun Parlor Member', '6 guests', 'Due']],
    [3, ['Free', '4 seats', '฿200/hr']],
    [4, ['Free', '6 seats', '฿300/hr']],
    [5, ['Free', '6 seats', '฿300/hr']],
  ] as const) {
    for (const field of fields)
      await expect(rows.nth(index)).toContainText(field);
    await expect(rows.nth(index).getByRole('button')).toBeEnabled();
  }
  await expect(rows.last()).toBeInViewport({ ratio: 1 });

  const arrivals = page.getByRole('region', {
    name: 'Upcoming arrivals',
    exact: true,
  });
  await expect(arrivals.locator('time')).toContainText('PM');
  await expect(arrivals).toContainText('Khun Parlor Member');
  await expect(arrivals).toContainText('Table 3');
  await expect(arrivals).toContainText('6 guests');
  await expect(
    arrivals.getByRole('button', { name: 'Check in', exact: true }),
  ).toBeEnabled();
  await expect(
    arrivals.getByRole('button', { name: 'Cancel booking', exact: true }),
  ).toBeEnabled();
  await expect(arrivals).toBeInViewport({ ratio: 1 });

  const pantry = page.getByRole('region', {
    name: 'Pantry watch',
    exact: true,
  });
  await expect(pantry.locator('dt')).toHaveText(['Low stock', 'Out of stock']);
  await expect(pantry.locator('dd')).toHaveText(['4', '1']);
  await expect(pantry).toBeInViewport({ ratio: 1 });

  const queue = page.getByRole('region', {
    name: 'Service queue',
    exact: true,
  });
  const requests = queue.locator('tbody tr');
  await expect(requests).toHaveCount(2);
  for (const [index, fields] of [
    [0, ['Table 1', 'In play', 'Khun Parlor Member', '4 guests', 'min', '฿']],
    [1, ['Table 2', 'In play', 'Walk-in Guests', '3 guests', 'min', '฿']],
  ] as const) {
    for (const field of fields)
      await expect(requests.nth(index)).toContainText(field);
    await expect(
      requests
        .nth(index)
        .getByRole('button', { name: 'Refreshments', exact: true }),
    ).toBeEnabled();
    await expect(
      requests
        .nth(index)
        .getByRole('button', { name: 'Check bill', exact: true }),
    ).toBeEnabled();
  }
  await expect(requests.last()).toBeInViewport({ ratio: 1 });
  await expect(page.locator('main')).not.toContainText(
    /\bsample\b|\bdemo\b|backend|scaffold/i,
  );
});

test('keeps metric navigation and section commands connected to their views', async ({
  page,
}) => {
  for (const [metric, heading] of [
    ['Bookings', 'Bookings'],
    ['Tables in play', 'Tables & floor'],
    ['Awaiting arrival', 'Bookings'],
    ['Active sessions', 'Live sessions'],
  ]) {
    await page
      .getByRole('region', { name: 'Branch summary' })
      .getByRole('button', { name: new RegExp(`^${metric}`) })
      .click();
    await expect(
      page.getByRole('heading', { name: heading, exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Overview', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: 'Overview', exact: true }),
    ).toBeVisible();
  }
  for (const [command, heading] of [
    ['Open floor', 'Tables & floor'],
    ['All bookings', 'Bookings'],
    ['Review inventory', 'Inventory & fulfillment'],
    ['View sessions', 'Live sessions'],
  ]) {
    await page.getByRole('button', { name: command, exact: true }).click();
    await expect(
      page.getByRole('heading', { name: heading, exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Overview', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: 'Overview', exact: true }),
    ).toBeVisible();
  }
});

test('opens direct dashboard actions and restores focus after dismissal', async ({
  page,
}) => {
  const arrivals = page.getByRole('region', {
    name: 'Upcoming arrivals',
    exact: true,
  });
  const queue = page.getByRole('region', {
    name: 'Service queue',
    exact: true,
  });
  for (const [trigger, title] of [
    [
      page.getByRole('button', { name: 'Seat walk-in', exact: true }),
      'Seat walk-in',
    ],
    [
      arrivals.getByRole('button', { name: 'Cancel booking', exact: true }),
      'Cancel booking',
    ],
    [
      queue.getByRole('button', { name: 'Refreshments', exact: true }).first(),
      'Add refreshments',
    ],
    [
      queue.getByRole('button', { name: 'Check bill', exact: true }).first(),
      'Table Session Bill',
    ],
  ] as const) {
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: title, exact: true });
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate((element) => element.matches(':modal'))).toBe(
      true,
    );
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
  }
});

test('keeps every section and row accessible on a phone without page overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Collapse navigation' }).click();
  await expect(
    page.getByRole('complementary', { name: 'Workspace navigation' }),
  ).toHaveCSS('width', '68px');
  const metrics = page
    .getByRole('region', { name: 'Branch summary' })
    .getByRole('button');
  await expect(metrics).toHaveCount(4);
  for (let index = 0; index < 4; index++) {
    await expect(metrics.nth(index)).toBeInViewport({ ratio: 1 });
  }
  await expect(
    page.getByRole('button', { name: 'Seat walk-in', exact: true }),
  ).toBeInViewport({ ratio: 1 });
  for (const name of [
    'Floor status',
    'Upcoming arrivals',
    'Pantry watch',
    'Service queue',
  ]) {
    await expect(page.getByRole('region', { name, exact: true })).toBeVisible();
  }
  await expect(page.locator('.overview-floor-table tbody tr')).toHaveCount(6);
  await expect(page.locator('.dashboard-queue-table tbody tr')).toHaveCount(2);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
  const seat = page.getByRole('button', {
    name: 'Seat walk-in at Table 4',
    exact: true,
  });
  await seat.click();
  await expect(
    page.getByRole('dialog', { name: 'Seat walk-in', exact: true }),
  ).toBeInViewport({ ratio: 1 });
  await page.keyboard.press('Escape');
  await expect(seat).toBeFocused();
});

test('supports visible-row selection and batch game returns without opening inspectors', async ({
  page,
}) => {
  await page.getByRole('button', { name: 'Game library', exact: true }).click();
  await page
    .getByRole('checkbox', { name: 'Select Azul', exact: true })
    .check();
  await expect(
    page.getByRole('complementary', { name: 'Azul', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole('checkbox', {
      name: 'Select all visible games',
      exact: true,
    }),
  ).toHaveJSProperty('indeterminate', true);
  await expect(
    page.getByRole('region', { name: 'Selected row actions' }),
  ).toContainText('1 selected');
  await page.getByRole('button', { name: /^On shelf/ }).click();
  await expect(
    page.getByRole('region', { name: 'Selected row actions' }),
  ).toHaveCount(0);
  await page.getByRole('button', { name: /^In use/ }).click();
  await page
    .getByRole('checkbox', { name: 'Select all visible games', exact: true })
    .check();
  await expect(
    page.getByRole('region', { name: 'Selected row actions' }),
  ).toContainText('2 selected');
  await page
    .getByRole('button', { name: 'Return to shelf (2)', exact: true })
    .click();
  await expect(
    page.getByRole('heading', {
      name: 'No games match these filters',
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'Game library', exact: true }),
  ).toBeFocused();
  await page
    .getByRole('button', { name: 'Live sessions', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: 'Assign game to Table 1', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Assign game to Table 2', exact: true }),
  ).toBeVisible();
});

test('receives selected pantry items with separate quantities and clears selection', async ({
  page,
}) => {
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Food & drinks', exact: true })
    .click();
  const first = 'Coca-Cola Original (Can 325ml)';
  const second = 'Coca-Cola Zero Sugar (Can 325ml)';
  await page
    .getByRole('checkbox', { name: `Select ${first}`, exact: true })
    .check();
  await page
    .getByRole('checkbox', { name: `Select ${second}`, exact: true })
    .check();
  const selection = page.getByRole('region', { name: 'Selected row actions' });
  await expect(selection).toContainText('2 selected');
  await selection
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  const dialog = page.getByRole('dialog', {
    name: 'Receive stock',
    exact: true,
  });
  await dialog
    .getByRole('spinbutton', { name: `Quantity for ${first}`, exact: true })
    .fill('6');
  await dialog
    .getByRole('spinbutton', { name: `Quantity for ${first}`, exact: true })
    .press('Tab');
  await dialog
    .getByRole('spinbutton', { name: `Quantity for ${second}`, exact: true })
    .fill('3');
  await dialog
    .getByRole('spinbutton', { name: `Quantity for ${second}`, exact: true })
    .press('Tab');
  await expect(dialog.getByText('54', { exact: true })).toBeVisible();
  await expect(dialog.getByText('39', { exact: true })).toBeVisible();
  await dialog
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(selection).toHaveCount(0);
  const stock = page.getByRole('region', {
    name: 'Stock for sale table',
    exact: true,
  });
  await expect(
    stock
      .getByRole('row')
      .filter({ hasText: first })
      .locator('td.numeric')
      .first(),
  ).toContainText('54');
  await expect(
    stock
      .getByRole('row')
      .filter({ hasText: second })
      .locator('td.numeric')
      .first(),
  ).toContainText('39');
  await expect(stock).toBeFocused();
  const detailsTrigger = stock.getByRole('button', {
    name: 'View record BEV-COCA-COLA',
    exact: true,
  });
  await detailsTrigger.click();
  const details = page.getByRole('complementary', { name: first, exact: true });
  const quantity = details.getByRole('spinbutton', {
    name: `Stock for ${first}`,
    exact: true,
  });
  await expect(quantity).toHaveValue('54');
  await quantity.fill('55');
  await quantity.press('Tab');
  await expect(details.locator('dl').first().locator('dd')).toHaveText([
    'BEV-COCA-COLA',
    'Beverages',
    'cans',
    '55 cans',
    '0 cans',
    '55 cans',
    '12 cans',
    'In stock',
  ]);
  await details.getByRole('button', { name: 'Close details' }).click();
  await expect(detailsTrigger).toBeFocused();
  await expect(
    stock
      .getByRole('row')
      .filter({ hasText: first })
      .locator('td.numeric')
      .first(),
  ).toContainText('55');
});

test('confirms destructive row and batch actions before removing catalog items', async ({
  page,
}) => {
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Food & drinks', exact: true })
    .click();
  const first = 'Coca-Cola Original (Can 325ml)';
  const second = 'Coca-Cola Zero Sugar (Can 325ml)';
  const stock = page.getByRole('region', {
    name: 'Stock for sale table',
    exact: true,
  });
  await expect(stock.locator('tbody tr')).toHaveCount(15);
  const firstRow = stock.getByRole('row').filter({ hasText: first });
  await firstRow
    .getByRole('button', { name: 'View record BEV-COCA-COLA', exact: true })
    .click();
  const details = page.getByRole('complementary', { name: first, exact: true });
  await expect(details.getByText('Cost', { exact: true })).toBeVisible();
  await expect(details.getByText('฿18', { exact: true })).toBeVisible();
  await expect(details.getByText('49%', { exact: true })).toBeVisible();
  const remove = details.getByRole('button', { name: 'Remove', exact: true });
  await expect(remove).toHaveCSS('color', 'rgb(161, 55, 44)');
  await remove.scrollIntoViewIfNeeded();
  await expect(remove).toBeInViewport({ ratio: 1 });
  await remove.click();
  const single = page.getByRole('dialog', { name: 'Remove item', exact: true });
  await expect(single.getByText(first, { exact: true })).toBeVisible();
  await single.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(stock.locator('tbody tr')).toHaveCount(15);
  await expect(remove).toBeFocused();
  await details.getByRole('button', { name: 'Close details' }).click();
  await page
    .getByRole('checkbox', { name: `Select ${first}`, exact: true })
    .check();
  await page
    .getByRole('checkbox', { name: `Select ${second}`, exact: true })
    .check();
  await page
    .getByRole('region', { name: 'Selected row actions' })
    .getByRole('button', { name: 'Remove items', exact: true })
    .click();
  const batch = page.getByRole('dialog', { name: 'Remove items', exact: true });
  await expect(batch.getByRole('listitem')).toHaveCount(2);
  const confirm = batch.getByRole('button', {
    name: 'Remove items',
    exact: true,
  });
  await expect(confirm).toHaveCSS('background-color', 'rgb(161, 55, 44)');
  await confirm.click();
  await expect(batch).toHaveCount(0);
  await expect(stock.locator('tbody tr')).toHaveCount(13);
  await expect(stock.getByText(first, { exact: true })).toHaveCount(0);
  await expect(stock.getByText(second, { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole('region', { name: 'Selected row actions' }),
  ).toHaveCount(0);
  await expect(stock).toBeFocused();
});

test('uses the same tables and visually blank action headers throughout management', async ({
  page,
}) => {
  for (const view of [
    'Overview',
    'Live sessions',
    'Bookings',
    'Game library',
    'Inventory & fulfillment',
    'Staff & shifts',
    'Revenue & analytics',
    'Venue policies',
  ]) {
    await page.getByRole('button', { name: view, exact: true }).click();
    await expect(
      page.getByRole('heading', { name: view, exact: true }),
    ).toBeVisible();
    await expect(
      page.locator('.workspace-view-enter-active,.workspace-view-leave-active'),
    ).toHaveCount(0);
    expect(await page.locator('table').count()).toBeGreaterThan(0);
    expect(await page.locator('table:not(.app-data-table)').count()).toBe(0);
    for (const header of await page
      .locator('.app-data-table .action-heading > span')
      .all()) {
      await expect(header).toHaveCSS('clip-path', 'inset(50%)');
    }
  }
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Check bill for Table 1', exact: true }),
  ).toHaveCSS('background-color', 'rgb(40, 84, 59)');
  await expect(
    page.getByRole('button', { name: 'Seat walk-in at Table 4', exact: true }),
  ).toHaveCSS('background-color', 'rgb(255, 255, 255)');
});

test('keeps shared inspectors aligned and restores their record trigger on desktop and phone', async ({
  page,
}) => {
  await page.getByRole('button', { name: 'Game library', exact: true }).click();
  const trigger = page.getByRole('button', { name: 'Azul', exact: true });
  await page
    .getByRole('row')
    .filter({ has: trigger })
    .getByRole('cell')
    .nth(3)
    .click();
  const inspector = page.getByRole('complementary', {
    name: 'Azul',
    exact: true,
  });
  await expect(inspector).toBeFocused();
  await expect(
    inspector.getByText('Michael Kiesling', { exact: true }),
  ).toBeVisible();
  expect(
    await page
      .locator('.gl-content-scroll')
      .evaluate((element) => element.scrollTop),
  ).toBe(0);
  expect(
    await page
      .locator('.app-master-detail')
      .evaluate((element) =>
        Math.abs(
          element.getBoundingClientRect().bottom -
            (element.querySelector('aside')?.getBoundingClientRect().bottom ??
              0),
        ),
      ),
  ).toBeLessThan(1);
  await page.keyboard.press('Escape');
  await expect(inspector).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Collapse navigation' }).click();
  await expect(
    page.getByRole('complementary', { name: 'Workspace navigation' }),
  ).toHaveCSS('width', '68px');
  await trigger.click();
  await expect(inspector).toBeFocused();
  await expect(
    inspector.getByRole('button', { name: 'Close details', exact: true }),
  ).toBeInViewport({ ratio: 1 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
});

test('keeps manager-only cost and removal actions out of the staff inventory', async ({
  page,
}) => {
  await page.route('**/api/auth/session', async (route) =>
    route.fulfill({
      json: {
        authenticated: true,
        profile: {
          subject: 'operator',
          username: 'Operator',
          roles: ['staff'],
          emailVerified: true,
        },
      },
    }),
  );
  await page.reload();
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Food & drinks', exact: true })
    .click();
  await expect(
    page.getByRole('checkbox', {
      name: 'Select all receivable items',
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Add food or drink', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole('columnheader', { name: 'Price / cost', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: /^Remove .* from catalog$/ }),
  ).toHaveCount(0);
  await page
    .getByRole('button', { name: 'View record BEV-COCA-COLA', exact: true })
    .click();
  const details = page.getByRole('complementary', {
    name: 'Coca-Cola Original (Can 325ml)',
    exact: true,
  });
  await expect(details.getByText('Cost', { exact: true })).toHaveCount(0);
  await expect(details.getByText('Margin', { exact: true })).toHaveCount(0);
  await expect(
    details.getByRole('button', { name: 'Remove', exact: true }),
  ).toHaveCount(0);
  await expect(
    details.getByRole('button', { name: 'Receive stock', exact: true }),
  ).toBeVisible();
  await details.getByRole('button', { name: 'Close details' }).click();
  await page
    .getByRole('checkbox', {
      name: 'Select all receivable items',
      exact: true,
    })
    .check();
  const selection = page.getByRole('region', { name: 'Selected row actions' });
  await expect(
    selection.getByRole('button', { name: 'Receive stock', exact: true }),
  ).toBeVisible();
  await expect(
    selection.getByRole('button', { name: 'Remove items', exact: true }),
  ).toHaveCount(0);
});

for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 390, height: 844 },
  { width: 812, height: 390 },
]) {
  test(`contains all seven dialogs at ${viewport.width}×${viewport.height}`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    const cases: {
      view: string;
      button: string | RegExp;
      title: string;
      width: number;
      prepare?: () => Promise<void>;
      check?: (dialog: Locator) => Promise<void>;
    }[] = [
      {
        view: 'Overview',
        button: 'Seat walk-in',
        title: 'Seat walk-in',
        width: 600,
      },
      {
        view: 'Overview',
        button: 'Cancel booking',
        title: 'Cancel booking',
        width: 460,
      },
      {
        view: 'Overview',
        button: 'Refreshments',
        title: 'Add refreshments',
        width: 460,
      },
      {
        view: 'Overview',
        button: 'Check bill for Table 1',
        title: 'Table Session Bill',
        width: 600,
        check: async (dialog) => {
          await dialog.locator('.settle-btn').scrollIntoViewIfNeeded();
          await expect(dialog.locator('.settle-btn')).toBeInViewport({
            ratio: 1,
          });
        },
      },
      {
        view: 'Game library',
        button: 'Assign Azul to table',
        title: 'Assign game',
        width: 740,
        prepare: async () => {
          const returned = page.getByRole('button', {
            name: 'Return Azul to shelf',
            exact: true,
          });
          await returned.click();
        },
      },
      {
        view: 'Inventory & fulfillment',
        button: 'Receive stock',
        title: 'Receive stock',
        width: 740,
        prepare: async () => {
          await page
            .getByRole('button', { name: 'Food & drinks', exact: true })
            .click();
          await page
            .getByRole('button', {
              name: 'View record BEV-COCA-COLA',
              exact: true,
            })
            .click();
        },
      },
      {
        view: 'Inventory & fulfillment',
        button: 'Add food or drink',
        title: 'Add menu item',
        width: 600,
        prepare: async () => {
          await page
            .getByRole('button', { name: 'Food & drinks', exact: true })
            .click();
        },
      },
    ];
    await page.setViewportSize(viewport);
    await page.getByRole('button', { name: 'Collapse navigation' }).click();
    await expect(
      page.getByRole('complementary', { name: 'Workspace navigation' }),
    ).toHaveCSS('width', '68px');
    for (const scenario of cases) {
      await page
        .getByRole('button', { name: scenario.view, exact: true })
        .click();
      await expect(
        page.getByRole('heading', { name: scenario.view, exact: true }),
      ).toBeVisible();
      await scenario.prepare?.();
      const trigger = page
        .getByRole('button', { name: scenario.button, exact: true })
        .first();
      await trigger.click();
      const dialog = page.getByRole('dialog', {
        name: scenario.title,
        exact: true,
      });
      await expect(dialog).toBeVisible();
      expect(
        await dialog.evaluate((element) => element.matches(':modal')),
      ).toBe(true);
      const box = await dialog.evaluate((element) => {
        const { x, y, width, height } = element.getBoundingClientRect();
        return { x, y, width, height };
      });
      expect(
        Math.abs(box.width - Math.min(scenario.width, viewport.width - 32)),
      ).toBeLessThan(2);
      expect(Math.abs(box.x - (viewport.width - box.width) / 2)).toBeLessThan(
        2,
      );
      expect(Math.abs(box.y - (viewport.height - box.height) / 2)).toBeLessThan(
        2,
      );
      expect(box.y).toBeGreaterThanOrEqual(15);
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height - 15);
      expect(
        await dialog.evaluate(
          (element) => element.scrollWidth > element.clientWidth,
        ),
      ).toBe(false);
      await scenario.check?.(dialog);
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
      await expect(trigger).toBeFocused();
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
    ).toBe(false);
    expect(errors).toEqual([]);
  });
}

test('uses the same selection, receipt and adjustment actions for games and food', async ({
  page,
}) => {
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  const stock = page.getByRole('region', {
    name: 'Stock for sale table',
    exact: true,
  });
  await expect(stock.getByRole('checkbox')).toHaveCount(21);
  await page
    .getByRole('checkbox', { name: 'Select all receivable items', exact: true })
    .check();
  const selection = page.getByRole('region', { name: 'Selected row actions' });
  await expect(selection).toContainText('20 selected');
  await selection
    .getByRole('button', { name: 'Clear selection', exact: true })
    .click();
  await stock
    .getByRole('checkbox', { name: 'Select Azul', exact: true })
    .check();
  await stock
    .getByRole('checkbox', {
      name: 'Select Coca-Cola Original (Can 325ml)',
      exact: true,
    })
    .check();
  await expect(selection).toContainText('2 selected');
  await expect(
    selection.getByRole('button', { name: 'Remove items', exact: true }),
  ).toHaveCount(0);
  await selection
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  const receipt = page.getByRole('dialog', {
    name: 'Receive stock',
    exact: true,
  });
  await receipt
    .getByRole('spinbutton', { name: 'Quantity for Azul', exact: true })
    .fill('3');
  await receipt
    .getByRole('spinbutton', { name: 'Quantity for Azul', exact: true })
    .press('Tab');
  await receipt
    .getByRole('spinbutton', {
      name: 'Quantity for Coca-Cola Original (Can 325ml)',
      exact: true,
    })
    .fill('6');
  await receipt
    .getByRole('spinbutton', {
      name: 'Quantity for Coca-Cola Original (Can 325ml)',
      exact: true,
    })
    .press('Tab');
  await receipt
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  const azul = stock.getByRole('row').filter({ hasText: 'AZUL-EN-B' });
  await expect(azul.locator('td.numeric')).toHaveText(['15copies', '5', '10']);
  await expect(
    stock
      .getByRole('row')
      .filter({ hasText: 'BEV-COCA-COLA' })
      .locator('td.numeric'),
  ).toHaveText(['54cans', '0', '54']);
  await expect(
    page.getByRole('complementary', { name: 'Azul', exact: true }),
  ).toHaveCount(0);
  const adjust = azul.getByRole('button', {
    name: 'Adjust quantity for Azul, AZUL-EN-B',
    exact: true,
  });
  await adjust.click();
  const adjustment = page.getByRole('dialog', {
    name: 'Adjust quantity',
    exact: true,
  });
  const counted = adjustment.getByRole('spinbutton', {
    name: 'Counted quantity',
    exact: true,
  });
  await expect(counted).toHaveValue('15');
  await expect(counted).toHaveAttribute('min', '5');
  await counted.fill('4');
  await counted.press('Tab');
  await expect(counted).toHaveValue('5');
  await adjustment
    .getByRole('button', { name: 'Save adjustment', exact: true })
    .click();
  await expect(adjust).toBeFocused();
  await expect(azul.locator('td.numeric')).toHaveText(['5copies', '5', '0']);
  await expect(azul.getByText('Fully reserved', { exact: true })).toBeVisible();
  const receive = azul.getByRole('button', {
    name: 'Receive stock for Azul, AZUL-EN-B',
    exact: true,
  });
  await receive.click();
  await receipt
    .getByRole('spinbutton', { name: 'Quantity for Azul', exact: true })
    .fill('3');
  await receipt
    .getByRole('spinbutton', { name: 'Quantity for Azul', exact: true })
    .press('Tab');
  await receipt
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  await expect(receive).toBeFocused();
  await expect(azul.locator('td.numeric')).toHaveText(['8copies', '5', '3']);
  await azul.getByText('Azul', { exact: true }).click();
  const details = page.getByRole('complementary', {
    name: 'Azul',
    exact: true,
  });
  await expect(
    details.getByRole('spinbutton', { name: 'Stock for Azul', exact: true }),
  ).toHaveValue('8');
  await details
    .getByRole('button', { name: 'Close details', exact: true })
    .click();
  await expect(
    azul.getByRole('button', { name: 'View record AZUL-EN-B', exact: true }),
  ).toBeFocused();
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  await expect(azul.locator('td.numeric')).toHaveText(['8copies', '5', '3']);
});

test('combines Fabric-style checkbox filters and clears them without closing the menu', async ({
  page,
}) => {
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  const table = page.getByRole('region', {
    name: 'Stock for sale table',
    exact: true,
  });
  const trigger = page.getByRole('button', { name: /^Filter/ });
  await trigger.click();
  const menu = page.getByRole('dialog', { name: 'Filters', exact: true });
  await expect(
    menu.getByRole('button', { name: 'Status', exact: true }),
  ).toHaveAttribute('aria-expanded', 'true');
  const category = menu.getByRole('button', { name: 'Category', exact: true });
  await expect(category).toHaveAttribute('aria-expanded', 'false');
  await expect(menu.getByText('Low stock', { exact: true })).toBeVisible();
  await menu.getByText('Low stock', { exact: true }).click();
  const lowStock = menu.getByRole('checkbox', {
    name: 'Low stock',
    exact: true,
  });
  await expect(lowStock).toBeChecked();
  await expect(lowStock).toHaveCSS('width', '16px');
  await expect(lowStock).toHaveCSS('height', '16px');
  for (const row of await menu
    .getByRole('group', { name: 'Status', exact: true })
    .locator('label')
    .all()) {
    await expect(row).toHaveCSS('height', '36px');
  }
  await expect(table.locator('tbody > tr')).toHaveCount(5);
  const lowRow = menu.getByText('Low stock', { exact: true }).locator('..');
  const nextRow = menu
    .getByText('Fully reserved', { exact: true })
    .locator('..');
  const lowBounds = await lowRow.boundingBox();
  const nextBounds = await nextRow.boundingBox();
  const menuBounds = await menu.boundingBox();
  expect(lowBounds).not.toBeNull();
  expect(nextBounds).not.toBeNull();
  expect(menuBounds).not.toBeNull();
  expect(lowBounds!.x).toBeCloseTo(menuBounds!.x + 1, 0);
  expect(lowBounds!.width).toBeCloseTo(menuBounds!.width - 2, 0);
  expect(lowBounds!.y + lowBounds!.height).toBeCloseTo(nextBounds!.y, 0);
  const paddedEdge = { x: lowBounds!.width - 2, y: lowBounds!.height - 1 };
  await lowRow.hover({ position: paddedEdge });
  await expect(lowRow).toHaveCSS('background-color', 'rgb(237, 243, 236)');
  await lowRow.click({ position: paddedEdge });
  await expect(lowStock).not.toBeChecked();
  await lowRow.click({ position: paddedEdge });
  await expect(lowStock).toBeChecked();
  await category.hover();
  await expect(lowRow).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  await expect(lowStock).toBeChecked();
  await menu
    .getByRole('checkbox', { name: 'Out of stock', exact: true })
    .check();
  await expect(table.locator('tbody > tr')).toHaveCount(7);
  await category.click();
  await menu
    .getByRole('checkbox', { name: 'Retail games', exact: true })
    .check();
  await expect(table.locator('tbody > tr')).toHaveCount(2);
  await expect(trigger).toHaveAccessibleName('Filter (3)');
  await menu.getByRole('button', { name: 'Clear all', exact: true }).click();
  await expect(table.locator('tbody > tr')).toHaveCount(20);
  await expect(menu).toBeVisible();
  await expect(trigger).toHaveAccessibleName('Filter');
  await category.click();
  await expect(
    menu.getByRole('group', { name: 'Category', exact: true }),
  ).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(menu).toBeHidden();
  await expect(trigger).toBeFocused();
  await trigger.press('Enter');
  await trigger.press('Tab');
  await expect(
    menu.getByRole('button', { name: 'Clear all', exact: true }),
  ).toBeFocused();
  await page
    .getByRole('searchbox', { name: 'Search stock', exact: true })
    .click();
  await expect(menu).toBeHidden();
});

test('separates open fulfillment work from completed order history', async ({
  page,
}) => {
  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  await page
    .getByRole('navigation', { name: 'Inventory views' })
    .getByRole('button', { name: 'Fulfillment', exact: true })
    .click();
  const table = page.getByRole('region', {
    name: 'Fulfillment orders table',
    exact: true,
  });
  const queue = page.getByRole('button', { name: /^Queue:/ });
  await expect(queue).toHaveText('Open orders');
  await expect(table.locator('tbody > tr')).toHaveCount(2);
  await expect(table.getByText('PO-1044', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Filter', exact: true }).click();
  const filters = page.getByRole('dialog', { name: 'Filters', exact: true });
  await filters.getByRole('button', { name: 'Method', exact: true }).click();
  await filters
    .getByRole('checkbox', { name: 'Store pickup', exact: true })
    .check();
  await page.keyboard.press('Escape');
  await expect(table.locator('tbody > tr')).toHaveCount(1);
  await expect(table.getByText('PO-1043', { exact: true })).toBeVisible();
  await queue.click();
  await page
    .getByRole('listbox', { name: 'Queue', exact: true })
    .getByRole('option', { name: 'Completed orders', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: 'Filter', exact: true }),
  ).toBeVisible();
  await expect(table.locator('tbody > tr')).toHaveCount(1);
  await expect(table.getByText('PO-1044', { exact: true })).toBeVisible();
  await expect(table.getByText('Completed', { exact: true })).toBeVisible();
  await table
    .getByRole('button', { name: 'View details for PO-1044', exact: true })
    .click();
  const details = page.getByRole('complementary', {
    name: 'PO-1044',
    exact: true,
  });
  await expect(
    details.getByText('In-store sale', { exact: true }),
  ).toBeVisible();
  await expect(details.getByText('Siam', { exact: true })).toBeVisible();
  await expect(details.getByText('PAND-EN-B', { exact: true })).toBeVisible();
  await details.getByRole('button', { name: 'Close details' }).click();
  await queue.focus();
  await queue.press('ArrowDown');
  await expect(
    page.getByRole('option', { name: 'Completed orders', exact: true }),
  ).toBeFocused();
  await page.keyboard.press('End');
  await page.keyboard.press('Enter');
  await expect(queue).toHaveText('All orders');
  await expect(queue).toBeFocused();
  await expect(table.locator('tbody > tr')).toHaveCount(3);
});

for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 390, height: 844 },
]) {
  test(`uses consistent inventory controls and record actions at ${viewport.width}×${viewport.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page
      .getByRole('button', { name: 'Collapse navigation', exact: true })
      .click();
    await page
      .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
      .click();
    const navigation = page.getByRole('navigation', {
      name: 'Inventory views',
    });
    await expect(navigation.getByRole('button')).toHaveText([
      'Stock for sale',
      'Rental assets',
      'Fulfillment',
    ]);
    await expect(
      page
        .getByRole('region', { name: 'Stock for sale table' })
        .locator('tbody > tr'),
    ).toHaveCount(20);
    const views = [
      {
        name: 'Stock for sale',
        type: 'Retail games',
        table: 'Stock for sale table',
        search: 'Search stock',
        rows: 5,
        status: 'Low stock',
        filtered: 1,
        details: 'View record AZUL-EN-B',
        detailTitle: 'Azul',
      },
      {
        name: 'Stock for sale',
        type: 'Food & drinks',
        table: 'Stock for sale table',
        search: 'Search stock',
        rows: 15,
        status: 'NEED_REORDER',
        filtered: 5,
        details: 'View record BEV-COCA-COLA',
        detailTitle: 'Coca-Cola Original (Can 325ml)',
      },
      {
        name: 'Rental assets',
        type: '',
        table: 'Rental assets table',
        search: 'Search rental assets',
        rows: 5,
        status: 'Maintenance',
        filtered: 1,
        details: 'View details for Azul, AZ-C01',
        detailTitle: 'Azul · AZ-C01',
      },
      {
        name: 'Fulfillment',
        type: '',
        table: 'Fulfillment orders table',
        search: 'Search orders',
        rows: 2,
        status: 'Awaiting dispatch',
        filtered: 1,
        details: 'View details for PO-1042',
        detailTitle: 'PO-1042',
      },
    ];
    for (const view of views) {
      await navigation
        .getByRole('button', { name: view.name, exact: true })
        .click();
      if (view.type)
        await page
          .getByRole('button', { name: view.type, exact: true })
          .click();
      await expect(
        page.getByRole('heading', { name: view.name, exact: true }),
      ).toBeVisible();
      await expect(
        page.locator(
          '.workspace-panel-enter-active, .workspace-panel-leave-active, .workspace-view-enter-active, .workspace-view-leave-active',
        ),
      ).toHaveCount(0);
      const summary = page.getByRole('group', {
        name: `${view.name} summary`,
        exact: true,
      });
      await expect(summary.locator('strong')).toHaveCount(4);
      const controls = page.getByRole('group', {
        name: 'Table controls',
        exact: true,
      });
      const table = page.getByRole('region', { name: view.table, exact: true });
      if (view.type) {
        const itemTypes = page.getByRole('group', {
          name: 'Item type',
          exact: true,
        });
        await expect(itemTypes.locator('..')).toHaveCSS(
          'border-bottom-width',
          '1px',
        );
        await expect(
          itemTypes.getByRole('button', { name: view.type, exact: true }),
        ).toHaveCSS('border-bottom-color', 'rgb(40, 84, 59)');
        const typeBounds = await itemTypes.boundingBox();
        const controlBounds = await controls.boundingBox();
        expect(typeBounds).not.toBeNull();
        expect(controlBounds).not.toBeNull();
        expect(typeBounds!.y + typeBounds!.height).toBeLessThanOrEqual(
          controlBounds!.y,
        );
      }
      await expect(table.locator('tbody > tr')).toHaveCount(view.rows);
      await expect(
        table.getByRole('columnheader', { name: 'Actions', exact: true }),
      ).toHaveCount(1);
      const filterTrigger = controls.getByRole('button', { name: /^Filter/ });
      await filterTrigger.click();
      const filterMenu = page.getByRole('dialog', {
        name: 'Filters',
        exact: true,
      });
      const filterBounds = await filterMenu.boundingBox();
      expect(filterBounds).not.toBeNull();
      expect(filterBounds!.x).toBeGreaterThanOrEqual(11);
      expect(filterBounds!.y).toBeGreaterThanOrEqual(11);
      expect(filterBounds!.x + filterBounds!.width).toBeLessThanOrEqual(
        viewport.width - 11,
      );
      expect(filterBounds!.y + filterBounds!.height).toBeLessThanOrEqual(
        viewport.height - 11,
      );
      const statusOption = filterMenu.getByRole('checkbox', {
        name: view.status === 'NEED_REORDER' ? 'Needs attention' : view.status,
        exact: true,
      });
      await statusOption.check();
      await expect(table.locator('tbody > tr')).toHaveCount(view.filtered);
      await statusOption.uncheck();
      await page.keyboard.press('Escape');
      await expect(filterTrigger).toBeFocused();
      const search = controls.getByRole('searchbox', {
        name: view.search,
        exact: true,
      });
      await search.fill('--no-match--');
      await expect(controls.getByRole('status')).toContainText(/^0\b/);
      await search.clear();
      await expect(table.locator('tbody > tr')).toHaveCount(view.rows);
      if (view.name !== 'Fulfillment') {
        const headers = await table.getByRole('columnheader').allTextContents();
        expect(
          headers.some(
            (header) =>
              header.trim() ===
              (view.name === 'Rental assets'
                ? 'Item / asset code'
                : 'Item / SKU'),
          ),
        ).toBe(true);
        if (view.type)
          expect(headers.slice(1).map((header) => header.trim())).toEqual([
            'Item / SKU',
            'On hand',
            'Reserved',
            'Available',
            'Status',
            'Actions',
          ]);
        expect(headers.slice(-2).map((header) => header.trim())).toEqual([
          'Status',
          'Actions',
        ]);
        await expect(
          table.getByRole('button', {
            name: view.type ? /^Receive stock for/ : /^View details for/,
          }),
        ).toHaveCount(view.rows);
        if (view.type) {
          await expect(
            table.getByRole('button', { name: /^Adjust quantity for/ }),
          ).toHaveCount(view.rows);
          await expect(
            table.locator('tbody tr .app-button--primary'),
          ).toHaveCount(view.rows);
          await expect(filterTrigger).toHaveAttribute(
            'aria-haspopup',
            'dialog',
          );
          await expect(filterTrigger).toHaveAttribute('aria-expanded', 'false');
          await expect(controls.getByRole('combobox')).toHaveCount(0);
          await expect(
            table.getByRole('button', { name: /^View details for/ }),
          ).toHaveCount(0);
          await expect(table.getByRole('checkbox')).toHaveCount(view.rows + 1);
        }
        await expect(table.getByRole('spinbutton')).toHaveCount(0);
      }
      if (view.details) {
        const trigger = table.getByRole('button', {
          name: view.details,
          exact: true,
        });
        await trigger.click();
        const details = page.getByRole('complementary', {
          name: view.detailTitle,
          exact: true,
        });
        if (view.type) {
          await expect(details.locator('.stock-adjustment')).toHaveCSS(
            'margin-top',
            '20px',
          );
          await expect(details.locator('dl').first().locator('dt')).toHaveText([
            'SKU',
            'Category',
            'Unit',
            'On hand',
            'Reserved',
            'Available',
            'Reorder point',
            'Status',
          ]);
          await expect(
            details.locator('dl').first().locator('dd').nth(5),
          ).toHaveText(view.type === 'Retail games' ? '7 copies' : '48 cans');
        }
        await details
          .getByRole('button', { name: 'Close details', exact: true })
          .click();
        await expect(trigger).toBeFocused();
      }
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
      ).toBe(false);
    }
    await navigation
      .getByRole('button', { name: 'Stock for sale', exact: true })
      .click();
    await page
      .getByRole('button', { name: 'Food & drinks', exact: true })
      .click();
    await page
      .getByRole('checkbox', {
        name: 'Select Coca-Cola Original (Can 325ml)',
        exact: true,
      })
      .check();
    await page.getByRole('searchbox', { name: 'Search stock' }).fill('Zero');
    await expect(
      page.getByRole('region', { name: 'Selected row actions' }),
    ).toHaveCount(0);
    await expect(
      page.getByRole('checkbox', {
        name: 'Select Coca-Cola Zero Sugar (Can 325ml)',
        exact: true,
      }),
    ).not.toBeChecked();
  });

  test(`keeps tables stationary when selection changes at ${viewport.width}×${viewport.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.getByRole('button', { name: 'Collapse navigation' }).click();
    await expect(
      page.getByRole('complementary', { name: 'Workspace navigation' }),
    ).toHaveCSS('width', '68px');
    const views = [
      {
        name: 'Game library',
        column: 'Game',
        row: 'Select Azul',
        all: 'Select all visible games',
        prepare: () => Promise.resolve(),
      },
      {
        name: 'Live sessions',
        column: 'Table',
        row: 'Select session at Table 1',
        all: 'Select all visible sessions',
        prepare: () => Promise.resolve(),
      },
      {
        name: 'Inventory & fulfillment',
        column: 'Item / SKU',
        row: 'Select Coca-Cola Original (Can 325ml)',
        all: 'Select all receivable items',
        prepare: async () => {
          await page
            .getByRole('button', { name: 'Food & drinks', exact: true })
            .click();
        },
      },
    ];
    for (const view of views) {
      await page.getByRole('button', { name: view.name, exact: true }).click();
      await expect(
        page.getByRole('heading', { name: view.name, exact: true }),
      ).toBeVisible();
      await view.prepare();
      const header = page.getByRole('columnheader', {
        name: view.column,
        exact: true,
      });
      await expect(header).toBeVisible();
      await expect(
        page.locator(
          '.workspace-view-enter-active,.workspace-view-leave-active,.workspace-panel-enter-active,.workspace-panel-leave-active',
        ),
      ).toHaveCount(0);
      await header.evaluate((element) =>
        element.scrollIntoView({ block: 'center', behavior: 'instant' }),
      );
      const initialTop = await header.evaluate(
        (element) => element.getBoundingClientRect().top,
      );
      const selection = page.getByRole('region', {
        name: 'Selected row actions',
      });
      await expect(selection).toHaveCount(0);
      await expect(
        page.getByRole('button', { name: 'Clear selection', exact: true }),
      ).toHaveCount(0);
      await page.getByRole('checkbox', { name: view.row, exact: true }).check();
      await expect(selection).toContainText('1 selected');
      await expect(
        page.getByRole('group', { name: 'Table controls', exact: true }),
      ).toContainText('1 selected');
      const placement = await selection.evaluate((element) => {
        const toolbar = element.closest('[aria-label="Table controls"]');
        if (!toolbar)
          throw new Error('Selection actions must belong to the table toolbar');
        const outer = toolbar.getBoundingClientRect();
        const inner = element.getBoundingClientRect();
        return {
          contained: inner.top >= outer.top && inner.bottom <= outer.bottom,
          rightGap: outer.right - inner.right,
        };
      });
      expect(placement.contained).toBe(true);
      expect(placement.rightGap).toBeCloseTo(viewport.width > 700 ? 16 : 12, 0);
      expect(
        Math.abs(
          (await header.evaluate(
            (element) => element.getBoundingClientRect().top,
          )) - initialTop,
        ),
      ).toBeLessThan(1);
      await page.getByRole('checkbox', { name: view.all, exact: true }).check();
      expect(
        Math.abs(
          (await header.evaluate(
            (element) => element.getBoundingClientRect().top,
          )) - initialTop,
        ),
      ).toBeLessThan(1);
      await selection
        .getByRole('button', { name: 'Clear selection', exact: true })
        .click();
      await expect(selection).toHaveCount(0);
      expect(
        Math.abs(
          (await header.evaluate(
            (element) => element.getBoundingClientRect().top,
          )) - initialTop,
        ),
      ).toBeLessThan(1);
    }
  });
}

test('wires branch games, pantry menu, rental copies, stock adjustments and order fulfillment to live API endpoints', async ({
  page,
}) => {
  const saleAdjustments: { delta: number; reason: string }[] = [];
  const menuAdjustments: { delta: number; reason: string }[] = [];
  const fulfilledOrderIds: string[] = [];

  await page.route('**/api/v1/branches/siam/**', async (route) => {
    const url = new URL(route.request().url());
    const { pathname } = url;
    const method = route.request().method();

    if (method === 'GET' && pathname === '/api/v1/branches/siam/games') {
      await route.fulfill({
        json: {
          items: [
            {
              branchId: 'siam',
              game: {
                id: 'game-azul',
                name: 'Azul',
                designer: 'Michael Kiesling',
                difficulty: 'EASY',
                categories: ['Strategy', 'Quick & Light'],
                mechanics: ['Tile placement'],
                minPlayers: 2,
                maxPlayers: 4,
                playMinutesMin: 30,
                playMinutesMax: 45,
                teachMinutes: 5,
                setupMinutes: 5,
                descriptions: { 'en-US': 'Draft tiles from shared supplies.' },
                active: true,
              },
              saleEnabled: true,
              rentalEnabled: true,
              staffCanTeach: true,
              rentalHourlyRate: { amountMinor: 2000 },
              editions: [
                {
                  editionId: 'ed-azul-en',
                  editionKind: 'BASE_GAME',
                  languageCode: 'en',
                  active: true,
                  salePrice: { amountMinor: 149000 },
                  saleOnHand: 9,
                  saleHeld: 2,
                  saleAvailable: 7,
                },
              ],
              rentalTotal: 1,
              rentalUsable: 1,
              rentalAvailableNow: 1,
            },
          ],
        },
      });
      return;
    }

    if (
      method === 'POST' &&
      pathname === '/api/v1/branches/siam/editions/ed-azul-en/stock-adjustments'
    ) {
      const body = route.request().postDataJSON() as {
        delta: number;
        reason: string;
      };
      saleAdjustments.push(body);
      await route.fulfill({
        json: {
          id: 'adj-1',
          branchId: 'siam',
          editionId: 'ed-azul-en',
          delta: body.delta,
          reason: body.reason,
          saleOnHand: 9 + body.delta,
          saleAvailable: 7 + body.delta,
          createdAt: '2026-11-13T10:00:00Z',
          operatorSubject: 'operator',
        },
      });
      return;
    }

    if (method === 'GET' && pathname === '/api/v1/branches/siam/menu') {
      await route.fulfill({
        json: {
          branchId: 'siam',
          items: [
            {
              item: {
                id: 'menu-coke',
                sku: 'bev-coca-cola',
                category: 'BEVERAGES',
                names: {
                  'en-US': 'Coca-Cola Original (Can 325ml)',
                  'th-TH': 'โคคา-โคล่า รสชาติต้นตำรับ (กระป๋อง)',
                },
                price: { amountMinor: 3500 },
                popular: true,
              },
              available: 40,
            },
          ],
        },
      });
      return;
    }

    if (
      method === 'POST' &&
      pathname === '/api/v1/branches/siam/menu/menu-coke/stock-adjustments'
    ) {
      const body = route.request().postDataJSON() as {
        delta: number;
        reason: string;
      };
      menuAdjustments.push(body);
      await route.fulfill({
        json: {
          branchId: 'siam',
          menuItemId: 'menu-coke',
          onHand: 40 + body.delta,
          held: 0,
          available: 40 + body.delta,
        },
      });
      return;
    }

    if (
      method === 'GET' &&
      pathname === '/api/v1/branches/siam/games/game-azul/rental-copies'
    ) {
      await route.fulfill({
        json: {
          items: [
            {
              id: 'copy-azul-1',
              branchId: 'siam',
              gameId: 'game-azul',
              assetCode: 'AZ-LIVE-01',
              operationalStatus: 'USABLE',
              state: 'AVAILABLE',
            },
          ],
        },
      });
      return;
    }

    if (method === 'GET' && pathname === '/api/v1/branches/siam/orders') {
      await route.fulfill({
        json: {
          items: [
            {
              id: '1042abcd-0000-0000-0000-000000000000',
              branchId: 'siam',
              customer: { kind: 'GUEST', displayName: 'Guest' },
              source: 'CUSTOMER',
              status: fulfilledOrderIds.includes(
                '1042abcd-0000-0000-0000-000000000000',
              )
                ? 'FULFILLED'
                : 'PLACED',
              items: [
                {
                  editionId: 'ed-azul-en',
                  gameName: 'Azul',
                  editionLanguage: 'en',
                  quantity: 2,
                  unitPrice: { amountMinor: 149000 },
                  lineTotal: { amountMinor: 298000 },
                },
              ],
              total: { amountMinor: 298000 },
              contact: { name: 'Guest', phone: '0812345678' },
              fulfillmentMethod: 'PICKUP',
              payment: {
                method: 'CARD',
                status: 'CAPTURED',
                amount: { amountMinor: 298000 },
              },
              createdAt: '2026-11-13T10:00:00Z',
              createdBySubject: 'guest',
            },
          ],
        },
      });
      return;
    }

    if (
      method === 'POST' &&
      pathname ===
        '/api/v1/branches/siam/orders/1042abcd-0000-0000-0000-000000000000/fulfill'
    ) {
      fulfilledOrderIds.push('1042abcd-0000-0000-0000-000000000000');
      await route.fulfill({
        json: {
          id: '1042abcd-0000-0000-0000-000000000000',
          branchId: 'siam',
          customer: { kind: 'GUEST', displayName: 'Guest' },
          source: 'CUSTOMER',
          status: 'FULFILLED',
          items: [
            {
              editionId: 'ed-azul-en',
              gameName: 'Azul',
              editionLanguage: 'en',
              quantity: 2,
              unitPrice: { amountMinor: 149000 },
              lineTotal: { amountMinor: 298000 },
            },
          ],
          total: { amountMinor: 298000 },
          contact: { name: 'Guest', phone: '0812345678' },
          fulfillmentMethod: 'PICKUP',
          payment: {
            method: 'CARD',
            status: 'CAPTURED',
            amount: { amountMinor: 298000 },
          },
          createdAt: '2026-11-13T10:00:00Z',
          createdBySubject: 'guest',
          fulfilledAt: '2026-11-13T10:05:00Z',
          fulfilledBySubject: 'operator',
        },
      });
      return;
    }

    await route.fulfill({ json: { items: [] } });
  });

  await page.reload();
  await page.getByRole('button', { name: 'Game library', exact: true }).click();
  await expect(page.locator('.workspace-records tbody tr')).toHaveCount(1);
  await expect(
    page.getByRole('button', { name: 'Azul', exact: true }),
  ).toBeVisible();

  await page
    .getByRole('button', { name: 'Inventory & fulfillment', exact: true })
    .click();
  const stock = page.getByRole('region', {
    name: 'Stock for sale table',
    exact: true,
  });
  await expect(stock.locator('tbody tr')).toHaveCount(2);
  await stock
    .getByRole('button', {
      name: 'Receive stock for Azul, AZUL-EN-B',
      exact: true,
    })
    .click();
  const receiveDialog = page.getByRole('dialog', {
    name: 'Receive stock',
    exact: true,
  });
  await receiveDialog
    .getByRole('spinbutton', { name: 'Quantity for Azul', exact: true })
    .fill('4');
  await receiveDialog
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  await expect
    .poll(() => saleAdjustments)
    .toEqual([{ delta: 4, reason: 'Stock receipt' }]);

  await stock
    .getByRole('button', {
      name: 'Receive stock for Coca-Cola Original (Can 325ml), BEV-COCA-COLA',
      exact: true,
    })
    .click();
  await receiveDialog
    .getByRole('spinbutton', {
      name: 'Quantity for Coca-Cola Original (Can 325ml)',
      exact: true,
    })
    .fill('5');
  await receiveDialog
    .getByRole('button', { name: 'Receive stock', exact: true })
    .click();
  await expect
    .poll(() => menuAdjustments)
    .toEqual([{ delta: 5, reason: 'Stock receipt' }]);

  await page
    .getByRole('navigation', { name: 'Inventory views' })
    .getByRole('button', { name: 'Rental assets', exact: true })
    .click();
  const rentalTable = page.getByRole('region', {
    name: 'Rental assets table',
    exact: true,
  });
  await expect(rentalTable.locator('tbody tr')).toHaveCount(1);
  await expect(
    rentalTable.getByText('AZ-LIVE-01', { exact: true }),
  ).toBeVisible();

  await page
    .getByRole('navigation', { name: 'Inventory views' })
    .getByRole('button', { name: 'Fulfillment', exact: true })
    .click();
  const fulfillmentTable = page.getByRole('region', {
    name: 'Fulfillment orders table',
    exact: true,
  });
  await expect(fulfillmentTable.locator('tbody tr')).toHaveCount(1);
  await fulfillmentTable
    .getByRole('button', { name: 'View details for PO-1042ABCD', exact: true })
    .click();
  const orderDetails = page.getByRole('complementary', {
    name: 'PO-1042ABCD',
    exact: true,
  });
  await orderDetails
    .getByRole('button', { name: 'Fulfill order', exact: true })
    .click();
  await expect
    .poll(() => fulfilledOrderIds)
    .toEqual(['1042abcd-0000-0000-0000-000000000000']);
});

test('loads live branch tables, reservations and rentals and checks out the rental on bill settlement', async ({
  page,
}) => {
  const branchId = '00000000-0000-0000-0000-000000000001';
  const tableId = '00000000-0000-4000-8000-000000000101';
  const reservationId = '00000000-0000-4000-8000-000000000201';
  const rentalId = '00000000-0000-4000-8000-000000000301';
  const startedAt = new Date(Date.now() - 60 * 60_000).toISOString();
  const endsAt = new Date(Date.now() + 60 * 60_000).toISOString();
  const checkedOutRentals: string[] = [];

  await page.route(`**/api/v1/branches/${branchId}/tables*`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: tableId,
            branchId,
            label: 'The Emerald Salon (Table 1)',
            capacity: 4,
            hourlyRate: { amountMinor: 30000 },
            operationalStatus: 'IN_SERVICE',
            state: 'OCCUPIED',
          },
        ],
      }),
    }),
  );

  await page.route(`**/api/v1/branches/${branchId}/reservations*`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: reservationId,
            branchId,
            tableId,
            customer: {
              kind: 'GUEST',
              displayName: 'Khun Ananda',
              phone: '0811112222',
            },
            source: 'ONLINE',
            status: 'CHECKED_IN',
            startsAt: startedAt,
            endsAt,
            partySize: 4,
            games: [],
            tableHourlyRate: { amountMinor: 30000 },
            createdAt: startedAt,
            createdBySubject: 'cust-1',
            checkedInAt: startedAt,
            rentalId,
          },
        ],
      }),
    }),
  );

  await page.route(`**/api/v1/branches/${branchId}/rentals**`, async (route) => {
    const url = new URL(route.request().url());
    if (
      route.request().method() === 'POST' &&
      url.pathname.endsWith(`/rentals/${rentalId}/check-out`)
    ) {
      checkedOutRentals.push(rentalId);
      const endedAt = new Date().toISOString();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          reservation: {
            id: reservationId,
            branchId,
            tableId,
            customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
            source: 'ONLINE',
            status: 'COMPLETED',
            startsAt: startedAt,
            endsAt,
            partySize: 4,
            games: [],
            tableHourlyRate: { amountMinor: 30000 },
            createdAt: startedAt,
            createdBySubject: 'cust-1',
            checkedInAt: startedAt,
            completedAt: endedAt,
            rentalId,
          },
          rental: {
            id: rentalId,
            branchId,
            reservationId,
            tableId,
            customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
            status: 'COMPLETED',
            startedAt,
            plannedEndsAt: endsAt,
            endedAt,
            checkedInBySubject: 'staff-1',
            checkedOutBySubject: 'staff-1',
            billableHours: 1,
            charges: [
              {
                kind: 'TABLE',
                quantity: 1,
                unitHourlyRate: { amountMinor: 30000 },
                billableHours: 1,
                lineTotal: { amountMinor: 30000 },
              },
            ],
            total: { amountMinor: 30000 },
          },
        }),
      });
      return;
    }

    if (url.pathname.endsWith(`/rentals/${rentalId}`)) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: rentalId,
          branchId,
          reservationId,
          tableId,
          customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
          status: 'ACTIVE',
          startedAt,
          plannedEndsAt: endsAt,
          checkedInBySubject: 'staff-1',
          charges: [
            {
              kind: 'TABLE',
              quantity: 1,
              unitHourlyRate: { amountMinor: 30000 },
            },
          ],
        }),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: rentalId,
            branchId,
            reservationId,
            tableId,
            customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
            status: 'ACTIVE',
            startedAt,
            plannedEndsAt: endsAt,
            checkedInBySubject: 'staff-1',
            charges: [
              {
                kind: 'TABLE',
                quantity: 1,
                unitHourlyRate: { amountMinor: 30000 },
              },
            ],
          },
        ],
      }),
    });
  });

  await page.goto('/workspace/sessions');
  await expect(page.getByText('Khun Ananda').first()).toBeVisible();
  await page.getByRole('button', { name: 'Settle bill' }).first().click();

  const dialog = page.getByRole('dialog', {
    name: 'Table Session Bill',
    exact: true,
  });
  await expect(dialog).toBeVisible();
  await dialog
    .getByRole('button', { name: 'Settle Bill & Complete Session ✓' })
    .click();

  await expect.poll(() => checkedOutRentals).toEqual([rentalId]);
});
