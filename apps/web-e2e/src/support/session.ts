import { randomUUID } from 'node:crypto';
import {
  expect,
  type APIRequestContext,
  type APIResponse,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import { PERSONAS, personaPassword, type PersonaKey } from './personas';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface CallOptions {
  data?: unknown;
  params?: Record<string, string | number>;
  headers?: Record<string, string>;
}

/** A signed-in browser: the page for UI checks and the same cookies for API calls. */
export interface Session {
  persona: PersonaKey;
  context: BrowserContext;
  page: Page;
  /** Authenticated call through the web proxy; writes carry the CSRF token. */
  call(
    method: Method,
    path: string,
    options?: CallOptions,
  ): Promise<APIResponse>;
  /** Parses the JSON body and fails with the body when the status is not the expected one. */
  json<T = any>( // eslint-disable-line @typescript-eslint/no-explicit-any
    method: Method,
    path: string,
    expected: number,
    options?: CallOptions,
  ): Promise<T>;
}

/** The browser sends English so assertions do not depend on the storefront language. */
async function preferEnglish(context: BrowserContext) {
  await context.addInitScript(() => {
    localStorage.setItem('theparlor.locale', 'en-US');
    localStorage.setItem('theparlor.store-context.v1', 'browse');
  });
}

function bind(
  persona: PersonaKey,
  context: BrowserContext,
  page: Page,
  request: APIRequestContext,
): Session {
  let csrf: { headerName: string; token: string } | undefined;
  async function call(method: Method, path: string, options: CallOptions = {}) {
    const headers: Record<string, string> = { ...options.headers };
    if (method !== 'GET') {
      if (!csrf) {
        const response = await request.get('/api/auth/csrf');
        expect(response.ok(), 'CSRF token request').toBe(true);
        csrf = await response.json();
      }
      if (csrf) headers[csrf.headerName] = csrf.token;
    }
    return request.fetch(path, {
      method,
      headers,
      data: options.data,
      params: options.params,
      failOnStatusCode: false,
    });
  }
  return {
    persona,
    context,
    page,
    call,
    async json(method, path, expected, options) {
      const response = await call(method, path, options);
      const body = await response.text();
      expect(
        response.status(),
        `${persona}: ${method} ${path} -> ${body}`,
      ).toBe(expected);
      return body ? JSON.parse(body) : undefined;
    },
  };
}

/**
 * Signs in through the real Keycloak form, so the BFF session cookie, the realm roles and
 * the provisioned application account all come from the live stack.
 */
export async function signIn(
  browser: Browser,
  baseURL: string,
  persona: PersonaKey,
): Promise<Session> {
  const context = await browser.newContext({ baseURL });
  await preferEnglish(context);
  const page = await context.newPage();
  await page.goto('/workspace');
  await page.locator('#username').fill(PERSONAS[persona].username);
  await page.locator('#password').fill(personaPassword());
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/workspace$/);
  return bind(persona, context, page, context.request);
}

/** An anonymous visitor, for the storefront and for guest-only checks. */
export async function visit(
  browser: Browser,
  baseURL: string,
): Promise<Pick<Session, 'context' | 'page'>> {
  const context = await browser.newContext({ baseURL });
  await preferEnglish(context);
  return { context, page: await context.newPage() };
}

export const idempotencyKey = () => ({ 'Idempotency-Key': randomUUID() });
