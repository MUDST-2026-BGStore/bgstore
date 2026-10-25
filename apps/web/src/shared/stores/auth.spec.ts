import { createPinia, setActivePinia } from 'pinia';
import { afterEach, expect, it, vi } from 'vitest';
import { configureApiTransport } from '@theparlor/api-client';
import { clearAuth, initAuth } from '@theparlor/auth';
import { useAuthStore } from './auth';

afterEach(() => {
  clearAuth();
  configureApiTransport({});
  vi.unstubAllGlobals();
});

it('updates the existing store when a server session is restored and cleared', async () => {
  clearAuth();
  configureApiTransport({ baseUrl: 'http://localhost' });
  setActivePinia(createPinia());
  const store = useAuthStore();
  expect(store.authenticated).toBe(false);
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: {
          subject: 'staff-1',
          username: 'seller',
          displayName: 'Seller',
          email: 'seller@example.com',
          emailVerified: true,
          roles: ['staff'],
        },
      }),
    ),
  );

  await initAuth();
  expect(store.authenticated).toBe(true);
  expect(store.subject).toBe('staff-1');
  expect(store.username).toBe('seller');
  expect(store.displayName).toBe('Seller');
  expect(store.email).toBe('seller@example.com');
  expect(store.emailVerified).toBe(true);
  expect(store.roles).toEqual(['staff']);

  clearAuth();
  expect(store.authenticated).toBe(false);
  expect(store.username).toBeNull();
  expect(store.subject).toBeNull();
  expect(store.roles).toEqual([]);
});
