import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { configureApiTransport, type Profile } from '@theparlor/api-client';
import { clearAuth, initAuth } from '@theparlor/auth';
import { clearAccount, ensureAccount, useAccount } from '@theparlor/account';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AuthProfile from './AuthProfile.vue';
import { PhoneNumberInput } from '@theparlor/ui';
// eslint-disable-next-line no-restricted-imports -- Exercise the application's actual translations.
import { i18n } from '../../../app/plugins/i18n';
import { initializeLocaleSync, useLocale } from '../../../shared/lib/locale';
import { queryClient as appQueryClient } from '../../../shared/lib/api';
import { useAuthStore } from '../../../shared/stores/auth';

let queryClient: QueryClient;
let wrapper: ReturnType<typeof mount> | undefined;
let stopLocaleSync: (() => void) | undefined;

beforeEach(async () => {
  HTMLDialogElement.prototype.showModal ??= function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close ??= function () {
    this.removeAttribute('open');
  };
  clearAuth();
  clearAccount();
  configureApiTransport({ baseUrl: 'http://localhost' });
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      Response.json({
        authenticated: true,
        profile: {
          subject: 'staff-1',
          username: 'staff',
          displayName: 'Staff Member',
          email: 'staff@example.com',
          emailVerified: true,
          roles: ['staff'],
        },
      }),
    ),
  );
  await initAuth();
  useLocale().setLocale('en-US');
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
});

afterEach(() => {
  wrapper?.unmount();
  stopLocaleSync?.();
  stopLocaleSync = undefined;
  clearAuth();
  clearAccount();
  queryClient.clear();
  configureApiTransport({});
  vi.unstubAllGlobals();
});

function renderProfile(pinia = createPinia()) {
  wrapper = mount(AuthProfile, {
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }], i18n],
      stubs: { RouterLink: true },
    },
  });
  return wrapper;
}

it('shows loading and renders the contract profile through the generated query', async () => {
  queryClient.setDefaultOptions({
    queries: { retry: false, staleTime: Infinity },
  });
  let respond!: (response: Response) => void;
  const fetch = vi.fn().mockImplementation(
    () =>
      new Promise<Response>((resolve) => {
        respond = resolve;
      }),
  );
  vi.stubGlobal('fetch', fetch);
  const page = renderProfile();
  expect(page.text()).toContain('Loading profile…');
  await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());
  const profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'Member',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'th-TH' },
  };
  respond(Response.json(profile));
  await vi.waitFor(() =>
    expect(page.get('[data-testid="profile-username"]').text()).toBe('staff'),
  );
  expect(page.get('[data-testid="profile-subject"]').text()).toBe('staff-1');
  expect(page.get('[data-testid="profile-account-id"]').text()).toBe(
    'account-1',
  );
  expect(page.get('[data-testid="profile-name"]').text()).toBe('Test Member');
  expect(page.findComponent(PhoneNumberInput).props('modelValue')).toBe(
    '+66812345678',
  );
  expect(page.text()).toContain('th-TH');
  expect(page.text()).toContain('staff@example.com');
  expect(page.text()).not.toContain('Loading profile…');
  page.unmount();
  const cachedPage = renderProfile();
  expect(cachedPage.get('[data-testid="profile-username"]').text()).toBe(
    'staff',
  );
  expect(fetch).toHaveBeenCalledOnce();
});

it('shows HTTP failures without retrying authentication or authorization errors', async () => {
  const fetch = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response('Forbidden', { status: 403 })),
    );
  vi.stubGlobal('fetch', fetch);
  const page = renderProfile();
  await vi.waitFor(() =>
    expect(page.get('[role="alert"]').text()).toContain('403'),
  );
  expect(page.find('[data-testid="profile-username"]').exists()).toBe(false);
  expect(fetch).toHaveBeenCalledOnce();
});

it('changes language immediately and saves it through the account endpoint', async () => {
  queryClient = appQueryClient;
  let profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  let submitted: unknown;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      if (new URL(input.url).pathname === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (input.method === 'PATCH') {
        submitted = await input.json();
        profile = { ...profile, preferences: { preferredLocale: 'th-TH' } };
        expect(new URL(input.url).pathname).toBe('/api/v1/me/settings');
        return Response.json({ preferences: { preferredLocale: 'th-TH' } });
      }
      return Response.json(profile);
    }),
  );
  await ensureAccount();
  stopLocaleSync = initializeLocaleSync();
  const page = renderProfile();
  await vi.waitFor(() =>
    expect(page.find('[data-testid="profile-account-id"]').exists()).toBe(true),
  );

  const thai = page
    .findAll('[role="option"]')
    .find((option) => option.text().includes('ไทย'));
  await thai!.trigger('click');
  expect(document.documentElement.lang).toBe('th-TH');
  await vi.waitFor(() => expect(useLocale().saveStatus.value).toBe('idle'));

  expect(submitted).toEqual({ preferences: { preferredLocale: 'th-TH' } });
  expect(page.text()).toContain('ภาษา');
  expect(page.text()).toContain('th-TH');
  expect(useAccount().value).toMatchObject({
    status: 'ready',
    account: { preferences: { preferredLocale: 'th-TH' } },
  });
});

it('saves an edited international phone number and updates the account cache', async () => {
  const profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  let submitted: unknown;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      if (new URL(input.url).pathname === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (input.method === 'PATCH') {
        submitted = await input.json();
        return Response.json({ ...profile, phoneNumber: '+442079460018' });
      }
      return Response.json(profile);
    }),
  );
  await ensureAccount();
  const page = renderProfile();
  await vi.waitFor(() =>
    expect(page.find('#profile-phone').exists()).toBe(true),
  );
  await page.get('#profile-phone').setValue('+44 20 7946 0018');
  await page.get('form').trigger('submit');
  await vi.waitFor(() => expect(page.text()).toContain('Phone number saved.'));
  expect(submitted).toEqual({ phoneNumber: '+442079460018' });
  expect(useAccount().value).toMatchObject({
    status: 'ready',
    account: {
      phoneNumber: '+442079460018',
      preferences: { preferredLocale: 'en-US' },
    },
  });
  expect(page.get('.country-trigger').text()).toBe('+44');
  expect(
    page.get('button[type="submit"]').attributes('disabled'),
  ).toBeDefined();
});

it('keeps an edited phone number after a failed save and allows retrying', async () => {
  const profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  let patches = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      if (new URL(input.url).pathname === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (input.method === 'PATCH') {
        patches++;
        if (patches === 1) return new Response('Unavailable', { status: 503 });
        return Response.json({ ...profile, phoneNumber: '+6581234567' });
      }
      return Response.json(profile);
    }),
  );
  const page = renderProfile();
  await vi.waitFor(() =>
    expect(page.find('#profile-phone').exists()).toBe(true),
  );
  await page.get('#profile-phone').setValue('+65 8123 4567');
  await page.get('form').trigger('submit');
  await vi.waitFor(() =>
    expect(page.get('[role="alert"]').text()).toContain('Couldn’t save'),
  );
  expect(page.findComponent(PhoneNumberInput).props('modelValue')).toBe(
    '+6581234567',
  );
  await page.get('form').trigger('submit');
  await vi.waitFor(() => expect(page.text()).toContain('Phone number saved.'));
  expect(patches).toBe(2);
});

it('confirms account deletion by calling deleteMe and then logging out', async () => {
  const profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  let deleted = false;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      const path = new URL(input.url).pathname;
      if (path === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (input.method === 'DELETE' && path === '/api/v1/me') {
        deleted = true;
        return new Response(null, { status: 204 });
      }
      return Response.json(profile);
    }),
  );
  const pinia = createPinia();
  const page = renderProfile(pinia);
  const authStore = useAuthStore(pinia);
  const logoutSpy = vi.spyOn(authStore, 'logout').mockResolvedValue();

  await vi.waitFor(() =>
    expect(page.find('[data-testid="delete-account-button"]').exists()).toBe(
      true,
    ),
  );
  await page.get('[data-testid="delete-account-button"]').trigger('click');
  const dialog = page.find('dialog');
  expect(dialog.exists()).toBe(true);
  const confirmBtn = dialog
    .findAll('button')
    .find((b) => b.text().trim() === 'Delete account');
  expect(confirmBtn).toBeDefined();
  await confirmBtn!.trigger('click');

  await vi.waitFor(() => expect(logoutSpy).toHaveBeenCalledOnce());
  expect(deleted).toBe(true);
});

it('shows server message on 409 conflict when deleting account and does not log out', async () => {
  const profile: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '+66812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      const path = new URL(input.url).pathname;
      if (path === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (input.method === 'DELETE' && path === '/api/v1/me') {
        return Response.json(
          { detail: 'Account has an open reservation or order' },
          { status: 409 },
        );
      }
      return Response.json(profile);
    }),
  );
  const pinia = createPinia();
  const page = renderProfile(pinia);
  const authStore = useAuthStore(pinia);
  const logoutSpy = vi.spyOn(authStore, 'logout').mockResolvedValue();

  await vi.waitFor(() =>
    expect(page.find('[data-testid="delete-account-button"]').exists()).toBe(
      true,
    ),
  );
  await page.get('[data-testid="delete-account-button"]').trigger('click');
  const dialog = page.find('dialog');
  const confirmBtn = dialog
    .findAll('button')
    .find((b) => b.text().trim() === 'Delete account');
  await confirmBtn!.trigger('click');

  await vi.waitFor(() =>
    expect(page.get('[data-testid="delete-account-error"]').text()).toContain(
      'Account has an open reservation or order',
    ),
  );
  expect(page.find('dialog').exists()).toBe(false);
  expect(logoutSpy).not.toHaveBeenCalled();
});
