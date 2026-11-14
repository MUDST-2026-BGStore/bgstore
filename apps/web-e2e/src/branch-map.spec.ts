import { expect, test, type Locator, type Page } from '@playwright/test';

// Keep MapLibre's real camera and marker positioning while removing tile-network
// variability. These checks exercise the public branch page with a mocked branch list.
test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({ json: { authenticated: false } }),
  );
  // The page reads branches from the API; positions come from the frontend's
  // approximate coordinates for these seeded names.
  await page.route('**/api/v1/branches?*', (route) =>
    route.fulfill({
      json: {
        items: [
          ['00000000-0000-4000-8000-0000000000a1', 'Siam', true],
          ['00000000-0000-4000-8000-0000000000a2', 'Ari', false],
          ['00000000-0000-4000-8000-0000000000a3', 'Riverside', false],
        ].map(([id, name, fulfillsDelivery]) => ({
          id,
          name,
          address: `${name} address`,
          openingHours: [
            { dayOfWeek: 'MONDAY', opensAt: '12:00', closesAt: '21:00' },
          ],
          active: true,
          fulfillsDelivery,
        })),
      },
    }),
  );
  await page.route('https://tiles.openfreemap.org/**', (route) =>
    route.fulfill({
      json: {
        version: 8,
        sources: {},
        layers: [
          {
            id: 'background',
            type: 'background',
            paint: { 'background-color': '#eef0ec' },
          },
        ],
      },
    }),
  );
});

function pin(page: Page, name: string) {
  return page
    .locator('.branch-pin')
    .and(page.getByRole('button', { name, exact: true }));
}

async function expectAnchored(marker: Locator) {
  await expect
    .poll(() =>
      marker.evaluate((element) => {
        const rect = element.getBoundingClientRect();
        const canvas = element.parentElement?.getBoundingClientRect();
        if (!canvas) return Number.POSITIVE_INFINITY;
        // MapLibre writes the projected geographic location into this translation.
        // The pin's bottom-center must land there, independent of zoom and siblings.
        const translation = [
          ...element.style.transform.matchAll(
            /translate\(([\d.e+-]+)px,\s*([\d.e+-]+)px\)/g,
          ),
        ].at(-1);
        if (!translation) return Number.POSITIVE_INFINITY;
        return Math.max(
          Math.abs(
            rect.left + rect.width / 2 - canvas.left - Number(translation[1]),
          ),
          Math.abs(rect.bottom - canvas.top - Number(translation[2])),
        );
      }),
    )
    .toBeLessThan(1);
}

async function expectFocused(page: Page, name: string) {
  await expect(pin(page, name)).toHaveAttribute('aria-pressed', 'true');
  await expect
    .poll(() =>
      page.evaluate((name) => {
        const marker = [
          ...document.querySelectorAll<HTMLElement>('.branch-pin'),
        ].find((element) => element.getAttribute('aria-label') === name);
        const canvas = document.querySelector('.branch-map .canvas');
        const directory = document.querySelector('.directory');
        if (!marker || !canvas || !directory) return Number.POSITIVE_INFINITY;
        const rect = marker.getBoundingClientRect();
        const mapRect = canvas.getBoundingClientRect();
        const panel = directory.getBoundingClientRect();
        const mobile = matchMedia('(max-width: 700px)').matches;
        const left = mobile ? mapRect.left : panel.right;
        const bottom = mobile ? panel.top : mapRect.bottom;
        return Math.max(
          Math.abs(rect.left + rect.width / 2 - (left + mapRect.right) / 2),
          Math.abs(rect.bottom - (mapRect.top + bottom) / 2),
        );
      }, name),
    )
    .toBeLessThan(2);
}

for (const width of [390, 1440]) {
  test(`opens booking for the chosen branch at ${width}px`, async ({
    page,
  }) => {
    await page.route('**/api/auth/session', (route) =>
      route.fulfill({
        json: {
          authenticated: true,
          profile: { subject: 'customer', username: 'customer', roles: [] },
        },
      }),
    );
    await page.route('**/api/v1/me', (route) =>
      route.fulfill({
        json: { id: 'customer', preferences: { preferredLocale: 'en-US' } },
      }),
    );
    await page.route('**/api/v1/me/cart', (route) =>
      route.fulfill({ json: { items: [] } }),
    );
    await page.route('**/api/v1/branches/*/games?*', (route) =>
      route.fulfill({ json: { items: [] } }),
    );
    await page.setViewportSize({ width, height: 960 });
    for (const [name, id] of [
      ['Siam', '00000000-0000-4000-8000-0000000000a1'],
      ['Ari', '00000000-0000-4000-8000-0000000000a2'],
      ['Riverside', '00000000-0000-4000-8000-0000000000a3'],
    ]) {
      await page.goto('/branches');
      await page
        .getByRole('button', { name: `${name} details`, exact: true })
        .click();
      await page
        .getByRole('link', { name: 'Reserve a table here', exact: true })
        .click();
      await expect(page).toHaveURL(`/reservations/new?branch=${id}`);
      await expect(
        page.getByRole('heading', { name: 'Choose Your Branch & Time' }),
      ).toBeVisible();
      await expect(
        page.getByRole('button', {
          name: `Branch Location: ${name} — ${name} address`,
          exact: true,
        }),
      ).toBeVisible();
    }
  });

  test(`sends a guest reservation click to sign in at ${width}px`, async ({
    page,
  }) => {
    await page.route('**/api/auth/login/keycloak?*', (route) =>
      route.fulfill({ body: 'Sign in', contentType: 'text/plain' }),
    );
    await page.setViewportSize({ width, height: 960 });
    await page.goto('/branches');
    await page
      .getByRole('button', { name: 'Ari details', exact: true })
      .click();
    await page
      .getByRole('link', { name: 'Reserve a table here', exact: true })
      .click();
    await expect(page).toHaveURL(
      /\/api\/auth\/login\/keycloak\?ui_locales=en-US$/,
    );
    expect(
      await page.evaluate(() => sessionStorage.getItem('theparlor.returnTo')),
    ).toBe('/reservations/new?branch=00000000-0000-4000-8000-0000000000a2');
  });

  test(`shows every branch pin and label on arrival at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 960 });
    await page.goto('/branches');
    await expect(page.locator('.branch-pin')).toHaveCount(3);
    for (const name of ['Siam', 'Ari', 'Riverside']) {
      await expect(pin(page, name).locator('svg')).toBeVisible();
      await expect(pin(page, name).locator('.branch-pin-label')).toHaveCSS(
        'opacity',
        '1',
      );
      // IntersectionObserver can round a fully visible transformed marker just below 1.
      await expect(pin(page, name)).toBeInViewport({ ratio: 0.999 });
      await expectAnchored(pin(page, name));
    }
    await expect
      .poll(() =>
        page.evaluate(() => {
          const panel = document
            .querySelector('.directory')
            ?.getBoundingClientRect();
          if (!panel) return false;
          return [...document.querySelectorAll('.branch-pin')].every(
            (marker) => {
              const rect = marker.getBoundingClientRect();
              return (
                rect.right <= panel.left ||
                rect.left >= panel.right ||
                rect.bottom <= panel.top ||
                rect.top >= panel.bottom
              );
            },
          );
        }),
      )
      .toBe(true);
  });

  test(`focuses a chosen branch and keeps pins anchored through zoom at ${width}px`, async ({
    page,
  }) => {
    if (width === 1440)
      await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.setViewportSize({ width, height: 960 });
    await page.goto('/branches');
    await expect(page.locator('.branch-pin')).toHaveCount(3);
    const span = () =>
      page.locator('.branch-pin').evaluateAll((markers) => {
        const positions = markers.map((element) =>
          element.getBoundingClientRect(),
        );
        return (
          Math.max(...positions.map((rect) => rect.top)) -
          Math.min(...positions.map((rect) => rect.top))
        );
      });
    const overviewSpan = await span();
    // The first row is also selectable even if a saved browsing preference
    // already identifies it; repeated selections must still focus the camera.
    const siamRow = page.locator(
      '[data-branch="00000000-0000-4000-8000-0000000000a1"] .row-main',
    );
    await siamRow.click();
    await expectFocused(page, 'Siam');
    await expect.poll(span).toBeGreaterThan(overviewSpan * 2);
    for (const action of ['Zoom in', 'Zoom out', 'Zoom out']) {
      await page.getByRole('button', { name: action, exact: true }).click();
      for (const name of ['Siam', 'Ari', 'Riverside']) {
        await expectAnchored(pin(page, name));
      }
    }
    await siamRow.click();
    await expectFocused(page, 'Siam');
    await page
      .getByRole('button', { name: 'Show all branches', exact: true })
      .click();
    for (const name of ['Siam', 'Ari', 'Riverside']) {
      await expect(pin(page, name)).toBeInViewport({ ratio: 0.999 });
    }
    await pin(page, 'Ari').click();
    await expectFocused(page, 'Ari');
    await page.setViewportSize({
      width: width === 390 ? 1440 : 390,
      height: 960,
    });
    await expectFocused(page, 'Ari');
    await expectAnchored(pin(page, 'Ari'));
  });
}
