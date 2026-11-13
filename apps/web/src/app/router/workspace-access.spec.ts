import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { effectScope } from 'vue';
import { flushPromises } from '@vue/test-utils';
import {
  createMemoryHistory,
  createRouter,
  type RouteRecordRaw,
  type Router,
} from 'vue-router';
import { configureApiTransport } from '@theparlor/api-client';
import { clearAccount } from '@theparlor/account';
import { clearAuth, initAuth } from '@theparlor/auth';
import { workspaceRoutes } from '../../features/workspace/routes';
import { staffReservationRoutes } from '../../features/reservations/routes';
import { ACCOUNT_STORAGE_PREFIX } from '../../shared/lib/protected-state';
import { installAuthGuards } from './guards';
import type {} from './index';

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock('@theparlor/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@theparlor/auth')>()),
  login,
}));

/** What Keycloak gives a newly registered customer: no application role. */
const CLIENT_ROLES = [
  'default-roles-theparlor',
  'uma_authorization',
  'offline_access',
];
const MANAGEMENT_PATHS = [
  '/workspace/management',
  '/workspace/reservations',
  '/management',
  '/management/reservations',
  '/staff/reservations',
];

let scope: ReturnType<typeof effectScope>;
let router: Router;

beforeEach(() => {
  clearAuth();
  clearAccount();
  sessionStorage.clear();
  localStorage.clear();
  login.mockReset();
  configureApiTransport({
    baseUrl: 'http://localhost',
    onUnauthorized: clearAuth,
  });
  const component = { template: '<div />' };
  // The real workspace route table, with its role metadata and legacy redirects.
  const stubbed = [...workspaceRoutes, ...staffReservationRoutes].map(
    (route) =>
      (route.redirect ? route : { ...route, component }) as RouteRecordRaw,
  );
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'HomeIndex', component },
      { path: '/login', name: 'AuthLogin', component },
      { path: '/forbidden', name: 'AuthForbidden', component },
      { path: '/profile', name: 'AuthProfile', component },
      { path: '/onboarding', name: 'AccountOnboarding', component },
      ...stubbed,
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

/** Signs in through the session endpoint; the account lookup then succeeds. */
async function signIn(roles: string[], subject = `user-${roles.join('-')}`) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: { subject, username: subject, emailVerified: true, roles },
      }),
    ),
  );
  await initAuth();
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(() =>
      Promise.resolve(
        Response.json({
          id: 'account-1',
          firstName: 'Test',
          lastName: 'User',
          preferences: { preferredLocale: 'en-US' },
        }),
      ),
    ),
  );
}

async function visit(path: string) {
  await router.push(path);
  await flushPromises();
  return router.currentRoute.value;
}

it.each(MANAGEMENT_PATHS)('sends a guest at %s to sign in', async (path) => {
  const route = await visit(path);
  // The guard starts the server login and aborts the in-app navigation.
  expect(route.name).not.toBe('WorkspaceManagement');
  expect(login).toHaveBeenCalledOnce();
  expect(sessionStorage.getItem('theparlor.returnTo')).toBe(
    '/workspace/management',
  );
});

it.each(MANAGEMENT_PATHS)(
  'forbids a newly registered client at %s',
  async (path) => {
    await signIn(CLIENT_ROLES);
    expect((await visit(path)).name).toBe('AuthForbidden');
  },
);

it.each([['staff'], ['manager'], ['admin']])(
  'opens the workspace for %s',
  async (role) => {
    await signIn([...CLIENT_ROLES, role]);
    expect((await visit('/workspace/management')).name).toBe(
      'WorkspaceManagement',
    );
  },
);

it('rechecks the role and clears account data when the account switches', async () => {
  await signIn(['staff'], 'staff-1');
  expect((await visit('/workspace/management')).name).toBe(
    'WorkspaceManagement',
  );
  localStorage.setItem(`${ACCOUNT_STORAGE_PREFIX}walk-ins`, '[{"name":"A"}]');

  await signIn(CLIENT_ROLES, 'client-1');
  await flushPromises();

  expect(localStorage.getItem(`${ACCOUNT_STORAGE_PREFIX}walk-ins`)).toBeNull();
  // Leaves the open workspace without waiting for another navigation.
  expect(router.currentRoute.value.name).toBe('AuthForbidden');
  expect((await visit('/workspace/management')).name).toBe('AuthForbidden');
});
