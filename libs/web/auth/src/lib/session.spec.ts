import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { clearAuth, initAuth, logout, useAuth } from './session';
import { configureApiTransport } from '@theparlor/api-client';

beforeEach(() => {
  clearAuth();
  configureApiTransport({ baseUrl: 'http://localhost' });
});
afterEach(() => {
  configureApiTransport({});
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it('restores identity from the server without keeping OAuth tokens', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: {
          subject: 'staff-1',
          username: 'staff',
          displayName: 'Staff Member',
          emailVerified: true,
          roles: ['staff'],
        },
      }),
    ),
  );
  expect(await initAuth()).toBe(true);
  expect(useAuth().value).toMatchObject({
    authenticated: true,
    subject: 'staff-1',
    roles: ['staff'],
  });
  expect(useAuth().value).not.toHaveProperty('token');
});
it('clears previous identity when the server reports an anonymous session', async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      Response.json({
        authenticated: true,
        profile: {
          subject: 'staff-1',
          username: 'staff',
          emailVerified: false,
          roles: ['staff'],
        },
      }),
    )
    .mockResolvedValueOnce(Response.json({ authenticated: false }));
  vi.stubGlobal('fetch', fetch);
  await initAuth();
  expect(await initAuth()).toBe(false);
  expect(useAuth().value.subject).toBeNull();
  expect(useAuth().value.roles).toEqual([]);
});
it('does not invent a login session when the server is unavailable', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  await expect(initAuth()).rejects.toThrow();
  expect(useAuth().value.authenticated).toBe(false);
});
it('submits logout as a native POST with a session-bound CSRF token', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        token: 'masked-token',
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
      }),
    ),
  );
  let submitted: HTMLFormElement | undefined;
  vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(function (
    this: HTMLFormElement,
  ) {
    submitted =
      document.querySelector<HTMLFormElement>(
        'form[action="/api/auth/logout"]',
      ) ?? undefined;
  });
  await logout();
  expect(submitted?.method).toBe('post');
  expect(submitted?.getAttribute('action')).toBe('/api/auth/logout');
  expect(
    submitted?.querySelector<HTMLInputElement>('input[name="_csrf"]')?.value,
  ).toBe('masked-token');
});
