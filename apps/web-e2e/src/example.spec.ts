import { expect, test, type Page } from '@playwright/test';

// These scenarios exercise authentication after the browsing choice is complete.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
});

async function signIn(page: Page, startPath = '/workspace') {
  const username = process.env['E2E_USERNAME'];
  const password = process.env['E2E_PASSWORD'];
  if (!username || !password)
    throw new Error(
      'Set E2E_USERNAME and E2E_PASSWORD for a local staff user.',
    );
  await page.goto(startPath);
  if (startPath === '/') {
    await page.getByRole('button', { name: 'Account', exact: true }).click();
    await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  }
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByLabel('Signed-in user')).toHaveText(username);
  return username;
}

test('allows anonymous visitors to browse the public page', async ({
  page,
}) => {
  await page.goto('/');
  await expect(
    page.getByRole('searchbox', { name: 'Search games', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Account', exact: true }).click();
  await expect(
    page.getByRole('link', { name: 'Sign in', exact: true }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
});

test('starts confidential server-side login with PKCE and a backend callback', async ({
  page,
}) => {
  await page.goto('/workspace');
  await expect(page.locator('#username')).toBeVisible();
  const url = new URL(page.url());
  expect(url.pathname).toContain(
    '/realms/theparlor/protocol/openid-connect/auth',
  );
  expect(url.searchParams.get('client_id')).toBe('theparlor-bff');
  expect(url.searchParams.get('code_challenge_method')).toBe('S256');
  expect(
    new URL(url.searchParams.get('redirect_uri') || 'about:blank').pathname,
  ).toBe('/api/auth/callback/keycloak');
});

test('restores a cookie session and reads the server profile without bearer tokens', async ({
  page,
  context,
}) => {
  const username = await signIn(page, '/');
  await expect(page.getByTestId('api-service')).toHaveText('theparlor-api');
  await expect(page.getByTestId('api-identity')).toHaveText(
    `API identity: ${username}`,
  );
  const session = (await context.cookies()).find(
    (cookie) => cookie.name === 'THEPARLOR_SESSION',
  );
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe('Lax');
  const request = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/api/v1/me',
  );
  await page.getByRole('link', { name: 'Your profile', exact: true }).click();
  await expect(page.getByTestId('profile-username')).toHaveText(username);
  expect((await request).headers()['authorization']).toBeUndefined();
  await page.reload();
  await expect(page.getByTestId('profile-username')).toHaveText(username);
  const response = await page.request.get('/api/auth/session');
  const body = await response.json();
  expect(body.authenticated).toBe(true);
  expect(body).not.toHaveProperty('access_token');
  expect(body).not.toHaveProperty('refresh_token');
});

test('denies administrator access to the seeded staff account', async ({
  page,
}) => {
  await signIn(page);
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/forbidden$/);
  expect((await page.request.get('/api/v1/admin/me')).status()).toBe(403);
});

test('rejects logout without CSRF and keeps the authenticated session', async ({
  page,
}) => {
  await signIn(page);
  expect((await page.request.post('/api/auth/logout')).status()).toBe(403);
  expect((await page.request.get('/api/v1/me')).status()).toBe(200);
});

test('logs out through a CSRF-protected POST and clears both application and SSO sessions', async ({
  page,
}) => {
  await signIn(page);
  const appRoot = new URL('/', page.url()).href;
  const logoutPost = page.waitForRequest(
    (request) => new URL(request.url()).pathname === '/api/auth/logout',
  );
  const keycloakLogout = page.waitForRequest((request) =>
    new URL(request.url()).pathname.endsWith('/protocol/openid-connect/logout'),
  );
  await page.getByRole('button', { name: 'Sign Out' }).click();
  const post = await logoutPost;
  expect(post.method()).toBe('POST');
  expect(post.postData()).toContain('_csrf=');
  const logoutUrl = new URL((await keycloakLogout).url());
  expect(logoutUrl.searchParams.get('post_logout_redirect_uri')).toBe(appRoot);
  await expect(page).toHaveURL(appRoot);
  await page.getByRole('button', { name: 'Account', exact: true }).click();
  await expect(
    page.getByRole('link', { name: 'Sign in', exact: true }),
  ).toBeVisible();
  expect((await page.request.get('/api/v1/me')).status()).toBe(401);
  await page.goto('/workspace');
  await expect(page.locator('#username')).toBeVisible();
});
