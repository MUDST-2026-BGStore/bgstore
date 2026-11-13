import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { clearAuth, initAuth } from '@theparlor/auth';
import { configureApiTransport } from '@theparlor/api-client';
import { useWorkspaceNavigation } from './useWorkspaceNavigation';

beforeEach(() => {
  setActivePinia(createPinia());
  clearAuth();
  configureApiTransport({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  configureApiTransport({});
  vi.unstubAllGlobals();
});

async function signIn(roles: string[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: { subject: 's', username: 's', emailVerified: true, roles },
      }),
    ),
  );
  await initAuth();
}

it.each([
  [[], ''],
  [['default-roles-theparlor', 'uma_authorization', 'offline_access'], ''],
  [['developer'], ''],
  [['staff'], 'Floor Staff'],
  [['staff', 'manager'], 'Branch Manager'],
  [['manager', 'admin'], 'Administrator'],
])(
  'labels roles %j as "%s" from the verified session',
  async (roles, title) => {
    await signIn(roles);
    const navigation = useWorkspaceNavigation();
    expect(navigation.userRoleTitle.value).toBe(title);
    expect(navigation.roleBadgeLabel.value === '').toBe(title === '');
  },
);

it('grants no management or administration views without those roles', async () => {
  await signIn(['default-roles-theparlor', 'developer']);
  const navigation = useWorkspaceNavigation();
  expect(navigation.isManager.value).toBe(false);
  expect(navigation.isAdmin.value).toBe(false);
});
