import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const mailpit =
  process.env['E2E_MAILPIT_URL'] ||
  `http://localhost:${process.env['MAILPIT_PORT'] || '8025'}`;
const adminPassword = process.env['KC_BOOTSTRAP_ADMIN_PASSWORD'] || '';
const adminUsername = process.env['KC_BOOTSTRAP_ADMIN_USERNAME'] || 'admin';
const keycloak = `http://localhost:${process.env['KEYCLOAK_PORT'] || '8081'}`;

test('registers into onboarding and resets the password through the local inbox', async ({
  page,
  browser,
  request,
  baseURL,
}) => {
  expect(
    adminPassword,
    'Local admin credentials are required to clean up the test identity.',
  ).not.toBe('');
  const tokenResponse = await request.post(
    `${keycloak}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: adminUsername,
        password: adminPassword,
      },
    },
  );
  expect(tokenResponse.ok()).toBe(true);
  const { access_token: token } = await tokenResponse.json();
  const headers = { Authorization: `Bearer ${token}` };
  const username = `selfservice-${randomUUID()}`;
  const email = `${username}@example.test`;
  const password = `${randomUUID()}-Initial1!`;
  const newPassword = `${randomUUID()}-Changed1!`;
  const resetContext = await browser.newContext();
  const freshContext = await browser.newContext();

  try {
    await page.goto('/workspace');
    await page.getByRole('link', { name: 'Sign up', exact: true }).click();
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.locator('#password-confirm').fill(password);
    await page.locator('#email').fill(email);
    await page.locator('#firstName').fill('Theme');
    await page.locator('#lastName').fill('Check');
    await page.getByRole('button', { name: 'Sign up', exact: true }).click();
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(
      page.getByRole('heading', { name: /Introduce yourself|แนะนำตัวคุณ/ }),
    ).toBeVisible();

    const resetPage = await resetContext.newPage();
    await resetPage.goto(new URL('/workspace', baseURL).href);
    await resetPage
      .getByRole('link', { name: 'Forgot password?', exact: true })
      .click();
    await resetPage.locator('#username').fill(email);
    await resetPage
      .getByRole('button', { name: 'Send reset link', exact: true })
      .click();
    await expect(resetPage.locator('.kc-feedback-text')).toContainText(
      'You should receive an email shortly',
    );

    let messageId: string | undefined;
    await expect
      .poll(async () => {
        const messages = await request.get(`${mailpit}/api/v1/messages`);
        expect(messages.ok()).toBe(true);
        const inbox = await messages.json();
        messageId = inbox.messages.find(
          (message: { To: { Address: string }[] }) =>
            message.To.some((recipient) => recipient.Address === email),
        )?.ID;
        return messageId;
      })
      .toBeTruthy();
    const messageResponse = await request.get(
      `${mailpit}/api/v1/message/${messageId}`,
    );
    expect(messageResponse.ok()).toBe(true);
    const message = await messageResponse.json();
    const resetLink = [...message.HTML.matchAll(/href="([^"]+)"/g)]
      .map((match) => match[1].replaceAll('&amp;', '&'))
      .find((link) => link.includes('/login-actions/action-token'));
    expect(resetLink).toBeTruthy();
    await resetPage.goto(resetLink || 'about:blank');
    await resetPage.locator('#password-new').fill(newPassword);
    await resetPage.locator('#password-confirm').fill(newPassword);
    await resetPage.locator('#kc-submit').click();
    await expect(resetPage).toHaveURL(/\/onboarding$/);

    const freshPage = await freshContext.newPage();
    await freshPage.goto(new URL('/workspace', baseURL).href);
    await freshPage.locator('#username').fill(username);
    await freshPage.locator('#password').fill(newPassword);
    await freshPage.locator('#kc-login').click();
    await expect(freshPage).toHaveURL(/\/onboarding$/);
  } finally {
    await resetContext.close();
    await freshContext.close();
    // Stop before onboarding submission, so there is no application account to
    // remove. Delete only the identity created by this test, even on failure.
    const usersResponse = await request.get(
      `${keycloak}/admin/realms/theparlor/users`,
      {
        headers,
        params: { username, exact: true },
      },
    );
    expect(usersResponse.ok()).toBe(true);
    for (const user of await usersResponse.json()) {
      const deleted = await request.delete(
        `${keycloak}/admin/realms/theparlor/users/${user.id}`,
        { headers },
      );
      expect(deleted.ok()).toBe(true);
    }
  }
});
