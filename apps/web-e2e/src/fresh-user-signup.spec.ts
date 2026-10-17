import { execSync } from 'node:child_process';
import { expect, test, type Page } from './coverage-fixture';

// A visitor who signs up must become a CLIENT before their first branch
// browse: the realm grants default roles at registration, and the client
// branch directory is the first thing the onboarding return points at. This
// test locks the whole self-registration path — registration, email
// verification, client onboarding, and the branch directory — against the
// real Compose stack. The mock-backed suites cannot catch regressions here
// because they serve /api/v1/branches from fixtures.

function requireFullStack() {
  test.skip(
    !process.env['BGSTORE_FULL_STACK'],
    'requires the Docker Compose stack',
  );
}

async function apiRequest(page: Page, path: string) {
  return page.evaluate(async (path) => {
    const csrfToken = document.cookie
      .split('; ')
      .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
      ?.split('=')[1];
    const response = await fetch(path, {
      credentials: 'include',
      headers: csrfToken
        ? { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken) }
        : {},
    });
    const body = response.ok ? await response.json() : null;
    return {
      status: response.status,
      count: body?.items?.length ?? -1,
      roles: body?.roles ?? null,
    };
  }, path);
}

async function verificationLink(email: string) {
  // Mailpit is reachable only from the Compose network in E2E mode.
  const project = process.env['BGSTORE_E2E_COMPOSE_PROJECT'] ?? '';
  const messages = execSync(
    `docker run --rm --network ${project}_default curlimages/curl -s http://mailpit:8025/api/v1/messages?limit=10`,
  ).toString();
  const mail = (
    JSON.parse(messages).messages as Array<{
      ID: string;
      To: Array<{ Address: string }>;
    }>
  ).find((m) => m.To.some((t) => t.Address === email));
  if (!mail) throw new Error(`No Mailpit message for ${email}`);
  const messageJson = execSync(
    `docker run --rm --network ${project}_default curlimages/curl -s http://mailpit:8025/api/v1/message/${mail.ID}`,
  ).toString();
  const link = messageJson.match(/https?:\/\/[^\s"\\]+/)?.[0];
  if (!link) throw new Error('No verification link in the email');
  return link;
}

test.describe('fresh user self-registration', () => {
  test('a newly registered client sees the branch directory', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    requireFullStack();

    // A guest can browse the directory before having an account.
    await page.goto('/branches');
    const guest = await apiRequest(page, '/api/v1/branches');
    expect(guest.status).toBe(200);
    expect(guest.count).toBeGreaterThan(0);

    // Sign up through the app link and the Keycloak registration form.
    await page.goto('/auth/sign-up?returnTo=%2Fbranches');
    await expect(page.locator('#firstName')).toBeVisible({ timeout: 30_000 });
    const email = `fresh-${Date.now()}@example.test`;
    await page.locator('#firstName').fill('Fresh');
    await page.locator('#lastName').fill('Tester');
    await page.locator('#email').fill(email);
    await page.locator('#password').fill('FreshUser123!');
    await page.getByRole('button', { name: /sign up/i }).click();

    // Email verification is required; open the link from Mailpit.
    await expect(
      page.getByRole('heading', { name: 'Check your inbox' }),
    ).toBeVisible({ timeout: 30_000 });
    await page.goto(await verificationLink(email));
    const proceed = page.getByRole('link', { name: /proceed/i });
    if (await proceed.isVisible().catch(() => false)) {
      await proceed.click();
    }

    // Verification is a detached action token; sign in with the new account.
    await page.goto('/auth/sign-in?returnTo=%2Fbranches');
    await expect(page.locator('#username')).toBeVisible({ timeout: 30_000 });
    await page.getByLabel('Username').fill(email);
    await page.getByLabel('Password', { exact: true }).fill('FreshUser123!');

    // The unverified profile cannot use the app yet, but is a client.
    await page.getByRole('button', { name: 'Sign in' }).click();
    const phoneInput = page.getByTestId('phone-input');
    await expect(phoneInput).toBeVisible({ timeout: 30_000 });
    const beforeOnboarding = await apiRequest(page, '/api/v1/branches');
    expect(beforeOnboarding.status).toBe(428);
    const identity = await apiRequest(page, '/api/v1/me');
    expect(identity.roles).toContain('CLIENT');

    // Completing onboarding returns the visitor to the directory.
    await phoneInput.fill('0898765432');
    await page.getByRole('button', { name: 'Continue' }).click();
    await expect(page).toHaveURL(`${process.env['BASE_URL']}/branches`, {
      timeout: 30_000,
    });
    const fresh = await apiRequest(page, '/api/v1/branches');
    expect(fresh.status).toBe(200);
    expect(fresh.count).toBe(guest.count);
  });
});
