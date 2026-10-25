import { nextTick, ref } from 'vue';
import { flushPromises } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { Profile, AccountSettings } from '@theparlor/api-client';

const mocks = vi.hoisted(() => ({
  auth: { value: { authenticated: false, subject: null as string | null } },
  account: {
    value: { status: 'unknown' } as { status: string; account?: Profile },
  },
  update: vi.fn(),
  cache: vi.fn(),
  cancel: vi.fn(),
  profile: vi.fn(),
}));
vi.mock('@theparlor/auth', () => ({ useAuth: () => mocks.auth }));
vi.mock('@theparlor/account', () => ({
  useAccount: () => mocks.account,
  updateAccountProfile: mocks.profile,
}));
vi.mock('@theparlor/api-client', () => ({
  updateMeSettings: mocks.update,
  getMeQueryKey: () => ['api', 'v1', 'me'],
}));
vi.mock('./api', () => ({
  queryClient: { setQueryData: mocks.cache, cancelQueries: mocks.cancel },
}));

const profile = (
  id = 'account-1',
  preferredLocale: AccountSettings['preferences']['preferredLocale'] = 'en-US',
): Profile => ({
  id,
  firstName: 'Test',
  lastName: 'User',
  phoneNumber: '+66812345678',
  preferences: { preferredLocale },
});
let stop: (() => void) | undefined;
beforeEach(() => {
  vi.resetModules();
  localStorage.clear();
  mocks.auth = ref({ authenticated: false, subject: null });
  mocks.account = ref({ status: 'unknown' });
  mocks.update
    .mockReset()
    .mockImplementation(async ({ preferences }) => ({ preferences }));
  mocks.cache.mockReset();
  mocks.cancel.mockReset().mockResolvedValue(undefined);
  mocks.profile.mockReset();
});
afterEach(() => {
  stop?.();
  stop = undefined;
  vi.restoreAllMocks();
});

function signedIn(
  id = 'account-1',
  locale: AccountSettings['preferences']['preferredLocale'] = 'en-US',
) {
  mocks.auth.value = { authenticated: true, subject: id };
  mocks.account.value = { status: 'ready', account: profile(id, locale) };
}
async function load() {
  const module = await import('./locale');
  stop = module.initializeLocaleSync();
  return module.useLocale();
}

it('remembers guest changes immediately without trying to save an account', async () => {
  localStorage.setItem('theparlor.locale', 'en-US');
  const preference = await load();
  preference.setLocale('th-TH');
  expect(preference.locale.value).toBe('th-TH');
  expect(document.documentElement.lang).toBe('th-TH');
  expect(localStorage.getItem('theparlor.locale')).toBe('th-TH');
  expect(mocks.update).not.toHaveBeenCalled();
});

it('loads the account preference over an older browser cache without echoing a save', async () => {
  localStorage.setItem('theparlor.locale', 'en-US');
  signedIn('account-1', 'th-TH');
  const preference = await load();
  expect(preference.locale.value).toBe('th-TH');
  expect(mocks.update).not.toHaveBeenCalled();
});

it('preserves a selection made while the account is loading', async () => {
  mocks.auth.value = { authenticated: true, subject: 'account-1' };
  const preference = await load();
  preference.setLocale('th-TH');
  signedIn();
  await flushPromises();
  expect(preference.locale.value).toBe('th-TH');
  expect(mocks.update).toHaveBeenCalledWith({
    preferences: { preferredLocale: 'th-TH' },
  });
});

it('does not send another update when onboarding already saved the selection', async () => {
  mocks.auth.value = { authenticated: true, subject: 'account-1' };
  const preference = await load();
  preference.setLocale('th-TH');
  signedIn('account-1', 'th-TH');
  await flushPromises();
  expect(mocks.update).not.toHaveBeenCalled();
});

it('keeps a failed change locally and retries it explicitly', async () => {
  signedIn();
  const preference = await load();
  mocks.update.mockRejectedValueOnce(new Error('offline'));
  preference.setLocale('th-TH');
  await flushPromises();
  expect(preference.locale.value).toBe('th-TH');
  expect(preference.saveStatus.value).toBe('error');
  expect(
    JSON.parse(localStorage.getItem('theparlor.locale.pending') ?? 'null'),
  ).toEqual({ accountId: 'account-1', locale: 'th-TH' });
  await preference.retrySave();
  expect(preference.saveStatus.value).toBe('idle');
  expect(localStorage.getItem('theparlor.locale.pending')).toBeNull();
  expect(mocks.profile).toHaveBeenCalledWith(profile('account-1', 'th-TH'));
  expect(mocks.cache).toHaveBeenCalledWith(
    ['api', 'v1', 'me'],
    profile('account-1', 'th-TH'),
  );
});

it('serializes rapid changes so the newest selection is saved last', async () => {
  signedIn();
  const preference = await load();
  let release!: (value: AccountSettings) => void;
  mocks.update.mockImplementationOnce(
    () =>
      new Promise<AccountSettings>((resolve) => {
        release = resolve;
      }),
  );
  preference.setLocale('th-TH');
  preference.setLocale('en-US');
  expect(mocks.update).toHaveBeenCalledOnce();
  release({ preferences: { preferredLocale: 'th-TH' } });
  await flushPromises();
  expect(
    mocks.update.mock.calls.map(([value]) => value.preferences.preferredLocale),
  ).toEqual(['th-TH', 'en-US']);
  expect(preference.locale.value).toBe('en-US');
  expect(preference.saveStatus.value).toBe('idle');
});

it('retries a stored pending change only for its owning account', async () => {
  localStorage.setItem(
    'theparlor.locale.pending',
    JSON.stringify({ accountId: 'account-1', locale: 'th-TH' }),
  );
  signedIn('account-2');
  const preference = await load();
  expect(mocks.update).not.toHaveBeenCalled();
  signedIn();
  await flushPromises();
  expect(mocks.update).toHaveBeenCalledOnce();
  expect(preference.locale.value).toBe('th-TH');
});

it('ignores a save response after logout and keeps the display language', async () => {
  signedIn();
  const preference = await load();
  let release!: (value: AccountSettings) => void;
  mocks.update.mockImplementationOnce(
    () =>
      new Promise<AccountSettings>((resolve) => {
        release = resolve;
      }),
  );
  preference.setLocale('th-TH');
  mocks.auth.value = { authenticated: false, subject: null };
  mocks.account.value = { status: 'unknown' };
  await nextTick();
  release({ preferences: { preferredLocale: 'th-TH' } });
  await flushPromises();
  expect(preference.locale.value).toBe('th-TH');
  expect(preference.saveStatus.value).toBe('idle');
  expect(mocks.profile).not.toHaveBeenCalled();
  expect(mocks.cache).not.toHaveBeenCalled();
});

it('still changes language when browser storage is unavailable', async () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
    throw new Error('unavailable');
  });
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
    throw new Error('unavailable');
  });
  const preference = await load();
  preference.setLocale('th-TH');
  expect(preference.locale.value).toBe('th-TH');
});

it.each(['en-US', 'th-TH'] as const)(
  'prefers a fresh %s selection over an older stored change',
  async (locale) => {
    localStorage.setItem(
      'theparlor.locale.pending',
      JSON.stringify({
        accountId: 'account-1',
        locale: locale === 'en-US' ? 'th-TH' : 'en-US',
      }),
    );
    mocks.auth.value = { authenticated: true, subject: 'account-1' };
    const preference = await load();
    preference.setLocale(locale);
    signedIn('account-1', 'en-US');
    await flushPromises();

    expect(preference.locale.value).toBe(locale);
    if (locale === 'en-US') expect(mocks.update).not.toHaveBeenCalled();
    else
      expect(mocks.update).toHaveBeenCalledExactlyOnceWith({
        preferences: { preferredLocale: locale },
      });
    expect(localStorage.getItem('theparlor.locale.pending')).toBeNull();
  },
);

it('prevents a profile query started before the save from restoring stale data', async () => {
  const { QueryClient } = await import('@tanstack/vue-query');
  const query = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  try {
    signedIn();
    const preference = await load();
    mocks.cache.mockImplementation((key, value) =>
      query.setQueryData(key, value),
    );
    mocks.cancel.mockImplementation((filters) => query.cancelQueries(filters));
    let release!: (value: Profile) => void;
    const oldQuery = query
      .fetchQuery({
        queryKey: ['api', 'v1', 'me'],
        queryFn: () =>
          new Promise<Profile>((resolve) => {
            release = resolve;
          }),
      })
      .catch(() => undefined);
    preference.setLocale('th-TH');
    await flushPromises();
    release(profile('account-1', 'en-US'));
    await oldQuery;
    expect(
      query.getQueryData<Profile>(['api', 'v1', 'me'])?.preferences
        .preferredLocale,
    ).toBe('th-TH');
  } finally {
    query.clear();
  }
});

it('checks the identity again after awaiting query cancellation', async () => {
  signedIn();
  const preference = await load();
  let release!: () => void;
  mocks.cancel.mockImplementationOnce(
    () =>
      new Promise<void>((resolve) => {
        release = resolve;
      }),
  );
  preference.setLocale('th-TH');
  await flushPromises();
  expect(mocks.cancel).toHaveBeenCalledOnce();
  mocks.auth.value = { authenticated: false, subject: null };
  mocks.account.value = { status: 'unknown' };
  await nextTick();
  release();
  await flushPromises();
  expect(mocks.cache).not.toHaveBeenCalled();
  expect(mocks.profile).not.toHaveBeenCalled();
});

it('saves a fresh choice after an older in-flight write even if the loaded profile matches it', async () => {
  signedIn();
  const preference = await load();
  let release!: (value: AccountSettings) => void;
  mocks.update.mockImplementationOnce(
    () =>
      new Promise<AccountSettings>((resolve) => {
        release = resolve;
      }),
  );
  preference.setLocale('th-TH');
  mocks.account.value = { status: 'unknown' };
  await nextTick();
  preference.setLocale('en-US');
  signedIn('account-1', 'en-US');
  await nextTick();
  release({ preferences: { preferredLocale: 'th-TH' } });
  await flushPromises();
  expect(
    mocks.update.mock.calls.map(([value]) => value.preferences.preferredLocale),
  ).toEqual(['th-TH', 'en-US']);
  expect(preference.locale.value).toBe('en-US');
  expect(preference.saveStatus.value).toBe('idle');
});
