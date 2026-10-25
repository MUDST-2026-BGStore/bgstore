import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { effectScope } from 'vue';
import { flushPromises } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { configureApiTransport } from '@theparlor/api-client';
import { clearAccount, useAccount } from '@theparlor/account';
import { clearAuth, initAuth, useAuth } from '@theparlor/auth';
import { installAuthGuards } from './guards';
import type {} from './index';

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock('@theparlor/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@theparlor/auth')>()),
  login,
}));

const profile = {
  id: 'account-1',
  firstName: 'Test',
  lastName: 'User',
  phoneNumber: '0812345678',
  preferences: { preferredLocale: 'en-US' },
};
let scope: ReturnType<typeof effectScope>;
let router: Router;

beforeEach(() => {
  clearAuth();
  clearAccount();
  sessionStorage.clear();
  login.mockReset();
  configureApiTransport({
    baseUrl: 'http://localhost',
    onUnauthorized: clearAuth,
  });
  const component = { template: '<div />' };
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'HomeIndex', component },
      { path: '/login', name: 'AuthLogin', component },
      { path: '/forbidden', name: 'AuthForbidden', component },
      ...[
        { path: '/profile', name: 'AuthProfile' },
        { path: '/onboarding', name: 'AccountOnboarding' },
        { path: '/workspace', name: 'WorkspaceIndex' },
      ].map((route) => ({ ...route, component, meta: { requiresAuth: true } })),
    ],
  });
  scope = effectScope();
  scope.run(() => installAuthGuards(router));
});

afterEach(() => {
  scope.stop();
  configureApiTransport({});
  vi.unstubAllGlobals();
});

async function signIn(subject = 'user-1') {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: { subject, username: subject, emailVerified: true, roles: [] },
      }),
    ),
  );
  await initAuth();
}

function respond(status = 200) {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      Response.json(status === 200 ? profile : { status }, { status }),
    );
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

it('starts login when the session expires during the first account lookup', async () => {
  await signIn();
  respond(401);
  router.onError(vi.fn());

  await router.push('/profile');

  expect(login).toHaveBeenCalledOnce();
  expect(sessionStorage.getItem('theparlor.returnTo')).toBe('/profile');
  expect(useAuth().value.authenticated).toBe(false);
});

it('blocks a protected target when logout happens during account loading', async () => {
  await signIn();
  let release!: (response: Response) => void;
  const fetch = vi.fn().mockImplementation(
    () =>
      new Promise<Response>((resolve) => {
        release = resolve;
      }),
  );
  vi.stubGlobal('fetch', fetch);
  const navigation = router.push('/profile');
  await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());

  clearAuth();
  release(Response.json(profile));
  await navigation;
  await flushPromises();

  expect(router.currentRoute.value.name).not.toBe('AuthProfile');
  expect(login).toHaveBeenCalledOnce();
  expect(useAccount().value.status).toBe('unknown');
});

it('checks the new identity instead of reusing a previous account', async () => {
  await signIn();
  respond();
  await router.push('/profile');

  await signIn('user-2');
  const fetch = respond(404);
  await router.push('/workspace');
  await flushPromises();

  expect(fetch).toHaveBeenCalledOnce();
  expect(router.currentRoute.value.name).toBe('AccountOnboarding');
});

it.each(['resolve', 'reject'])(
  'rechecks the identity when an old lookup finishes: %s',
  async (outcome) => {
    await signIn();
    let release!: (response: Response) => void;
    let reject!: (error: Error) => void;
    const fetch = vi.fn().mockImplementation(
      () =>
        new Promise<Response>((resolve, fail) => {
          release = resolve;
          reject = fail;
        }),
    );
    vi.stubGlobal('fetch', fetch);
    const navigation = router.push('/profile');
    router.onError(vi.fn());
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());

    await signIn('user-2');
    const currentFetch = respond(404);
    if (outcome === 'resolve') release(Response.json(profile));
    else reject(new Error('old request failed'));
    await navigation;
    await flushPromises();

    expect(currentFetch).toHaveBeenCalledOnce();
    expect(router.currentRoute.value.name).toBe('AccountOnboarding');
    expect(useAccount().value.status).toBe('missing');
  },
);

it('still sends a missing account to onboarding', async () => {
  await signIn();
  respond(404);
  await router.push('/profile');
  await flushPromises();
  expect(router.currentRoute.value.name).toBe('AccountOnboarding');
});

it('still redirects an existing account away from onboarding', async () => {
  await signIn();
  respond();
  await router.push('/onboarding');
  await flushPromises();
  expect(router.currentRoute.value.name).toBe('WorkspaceIndex');
});

it('does not swallow account lookup errors while the identity is unchanged', async () => {
  await signIn();
  respond(500);
  router.onError(vi.fn());
  await expect(router.push('/profile')).rejects.toMatchObject({
    response: { status: 500 },
  });
  expect(login).not.toHaveBeenCalled();
  expect(useAuth().value.authenticated).toBe(true);
});
