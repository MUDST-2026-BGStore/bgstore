import { expect, test } from '@playwright/test';

for (const viewport of [
  { width: 1440, height: 960 },
  { width: 375, height: 812 },
  { width: 320, height: 812 },
]) {
  test(`renders the Parlor sign-in theme at ${viewport.width}px`, async ({
    page,
    baseURL,
  }) => {
    await page.setViewportSize(viewport);
    await page.goto('/workspace');
    await expect(page.locator('#username')).toBeVisible();
    await expect(page).toHaveTitle('Sign in | The Parlor');
    await expect(page.locator('#kc-header')).toHaveCSS(
      'background-color',
      'rgb(32, 60, 48)',
    );
    await expect(page.locator('#kc-header .parlor-wordmark')).toHaveText(
      'The Parlor',
    );
    await expect(page.locator('#kc-login')).toHaveCSS(
      'background-color',
      'rgb(21, 44, 33)',
    );
    const header = await page.locator('#kc-header').boundingBox();
    expect(header?.x).toBe(0);
    expect(header?.y).toBe(0);
    expect(header?.width).toBe(viewport.width);
    const wordmark = await page.locator('#kc-header-wrapper').boundingBox();
    expect(wordmark).not.toBeNull();
    expect(
      Math.abs(
        (wordmark?.y || 0) +
          (wordmark?.height || 0) / 2 -
          (header?.height || 0) / 2,
      ),
    ).toBeLessThan(2);
    const language = page.locator('#kc-header .parlor-header-language');
    await expect(language).toBeVisible();
    const english = language.getByRole('link', { name: 'EN', exact: true });
    await expect(english).toHaveAttribute('aria-current', 'true');
    await expect(english).toHaveCSS('background-color', 'rgb(198, 173, 122)');
    const switcher = await language.boundingBox();
    expect(switcher?.x).toBeLessThan(32);
    expect((switcher?.x ?? 0) + (switcher?.width ?? 0)).toBeLessThan(
      wordmark?.x ?? 0,
    );
    expect((switcher?.y ?? 0) + (switcher?.height ?? 0)).toBeLessThanOrEqual(
      header?.height ?? 0,
    );
    const form = await page.getByRole('main').boundingBox();
    expect(form).not.toBeNull();
    expect(
      Math.abs((form?.x || 0) + (form?.width || 0) / 2 - viewport.width / 2),
    ).toBeLessThan(1);
    const back = page.getByRole('link', { name: 'Back to The Parlor' });
    await expect(back).toBeVisible();
    expect(new URL((await back.getAttribute('href')) || '').origin).toBe(
      new URL(baseURL || '').origin,
    );
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await back.click();
    await expect(page).toHaveURL(new URL('/', baseURL).href);
  });
}

test('keeps password visibility and invalid-credential feedback functional', async ({
  page,
}) => {
  await page.goto('/workspace');
  await page.locator('#username').fill('theme-check-nonexistent-user');
  await page.locator('#password').fill('invalid-password');
  await page
    .getByRole('button', { name: 'Show password', exact: true })
    .click();
  await expect(page.locator('#password')).toHaveAttribute('type', 'text');
  await page
    .getByRole('button', { name: 'Hide password', exact: true })
    .click();
  await expect(page.locator('#password')).toHaveAttribute('type', 'password');
  await page.locator('#kc-login').click();
  await expect(page.locator('#input-error-username')).toBeVisible();
  await expect(page.locator('#username')).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await expect(page.locator('#kc-login')).toBeEnabled();
  await expect(
    page.getByRole('link', { name: 'Back to The Parlor' }),
  ).toBeVisible();
});

test('uses one focus underline across the password field and visibility control', async ({
  page,
}) => {
  await page.goto('/workspace');
  const password = page.locator('#password');
  const group = page.locator('.pf-v5-c-input-group').filter({ has: password });
  await page.locator('#username').focus();
  await page.keyboard.press('Tab');
  await expect(password).toBeFocused();
  await expect(password).toHaveCSS('outline-style', 'none');
  await expect(group).toHaveCSS('border-bottom-color', 'rgb(21, 44, 33)');
  await page.keyboard.press('Tab');
  const visibility = page.getByRole('button', {
    name: 'Show password',
    exact: true,
  });
  await expect(visibility).toBeFocused();
  await expect(group).toHaveCSS('border-bottom-color', 'rgb(21, 44, 33)');
  await page.keyboard.press('Space');
  await expect(password).toHaveAttribute('type', 'text');
});

test('uses the same theme on Keycloak error pages', async ({ page }) => {
  await page.goto('/workspace');
  await expect(page.locator('#username')).toBeVisible();
  const url = new URL(page.url());
  url.searchParams.set('redirect_uri', 'https://invalid.example/callback');
  await page.goto(url.href);
  await expect(page.locator('#kc-error-message')).toBeVisible();
  await expect(page.locator('#kc-header')).toHaveCSS(
    'background-color',
    'rgb(32, 60, 48)',
  );
  await expect(
    page.getByRole('link', { name: 'Back to The Parlor' }),
  ).toBeVisible();
});

test('offers sign-up and password reset with matching screens', async ({
  page,
}) => {
  await page.goto('/workspace');
  await page.getByRole('link', { name: 'Sign up', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Create your account' }),
  ).toBeVisible();
  await expect(page.locator('#kc-register-form')).toBeVisible();
  await expect(page.getByRole('main')).toHaveCSS(
    'background-color',
    'rgb(245, 237, 224)',
  );
  await expect(
    page.getByRole('link', { name: 'Sign in', exact: true }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  await page
    .getByRole('link', { name: 'Forgot password?', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Reset your password' }),
  ).toBeVisible();
  await expect(page.locator('#kc-reset-password-form')).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Send reset link', exact: true }),
  ).toBeVisible();
  await expect(page.locator('#parlor-reset-description')).toContainText(
    'send you a link',
  );
  await expect(page.getByRole('main')).toHaveCSS(
    'background-color',
    'rgb(245, 237, 224)',
  );
  await page
    .getByRole('link', { name: 'Back to sign in', exact: true })
    .click();
  await expect(page.locator('#kc-login')).toBeVisible();
  await page.getByRole('link', { name: 'Sign up', exact: true }).click();
  await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  await expect(page.locator('#kc-login')).toBeVisible();
});

test('uses the storefront language switcher size on sign-up', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 812 });
  await page.goto('/');
  const storefront = page.locator('.header-language .locale-switcher');
  await expect(storefront).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  const storefrontSize = await storefront.boundingBox();
  expect(storefrontSize).not.toBeNull();

  await page.goto('/login');
  await page.getByRole('link', { name: 'Sign up', exact: true }).click();
  const signup = page.locator('#kc-header .parlor-header-language');
  await expect(signup).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  const signupSize = await signup.boundingBox();
  expect(signupSize).not.toBeNull();
  expect(signupSize!.width).toBeCloseTo(storefrontSize!.width, 0);
  expect(signupSize!.height).toBeCloseTo(storefrontSize!.height, 0);
  expect(signupSize!.x).toBeLessThan(32);

  const thai = signup.getByRole('link', { name: 'ไทย', exact: true });
  await thai.click();
  await expect(thai).toHaveAttribute('aria-current', 'true');
  await expect(
    page.getByRole('heading', { name: 'สร้างบัญชีของคุณ' }),
  ).toBeVisible();
  await signup.getByRole('link', { name: 'EN', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Create your account' }),
  ).toBeVisible();
});

test('switches sign-in to the storefront Thai font while keeping the wordmark', async ({
  page,
}) => {
  await page.goto('/workspace');
  const language = page.locator('#kc-header .parlor-header-language');
  const thai = language.getByRole('link', { name: 'ไทย', exact: true });
  await thai.click();
  await expect(thai).toHaveAttribute('aria-current', 'true');
  const title = page.getByRole('heading', { name: 'เข้าสู่ระบบ' });
  await expect(title).toHaveCSS('font-family', /FC Mee Sakul/);
  await expect(page.locator('#username')).toHaveCSS(
    'font-family',
    /FC Mee Sakul/,
  );
  await expect(page.locator('.parlor-wordmark')).toHaveCSS(
    'font-family',
    /Plus Jakarta Sans/,
  );
  await expect(page.locator('#kc-header .parlor-suit').first()).toHaveCSS(
    'font-family',
    /Plus Jakarta Sans/,
  );
  await language.getByRole('link', { name: 'EN', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});
