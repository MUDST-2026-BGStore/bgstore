import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import {
  configureApiTransport,
  type CreateAccountRequest,
  type Profile,
} from '@theparlor/api-client';
import { clearAccount, useAccount } from '@theparlor/account';
import Onboarding from './Onboarding.vue';
// eslint-disable-next-line no-restricted-imports -- Exercise the application's actual translations.
import { i18n } from '../../../app/plugins/i18n';
import { useLocale } from '../../../shared/lib/locale';

let wrapper: ReturnType<typeof mount> | undefined;

beforeEach(() => {
  clearAccount();
  localStorage.clear();
  useLocale().setLocale('en-US');
  configureApiTransport({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  wrapper?.unmount();
  clearAccount();
  configureApiTransport({});
  vi.unstubAllGlobals();
});

it.each([
  ['en-US', 'EN'],
  ['th-TH', 'ไทย'],
] as const)(
  'sends and reloads the selected locale unchanged: %s',
  async (locale, label) => {
    let submitted: CreateAccountRequest | undefined;
    const profile: Profile = {
      id: 'account-1',
      firstName: 'Test',
      lastName: 'User',
      phoneNumber: '+66812345678',
      preferences: { preferredLocale: locale },
    };
    const fetch = vi.fn().mockImplementation(async (input: Request) => {
      const path = new URL(input.url).pathname;
      if (path === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf-token',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (path === '/api/v1/me' && input.method === 'POST') {
        submitted = await input.json();
        return Response.json(profile);
      }
      if (path === '/api/v1/me' && input.method === 'GET') {
        return Response.json(profile);
      }
      throw new Error(`Unexpected request: ${input.method} ${input.url}`);
    });
    vi.stubGlobal('fetch', fetch);
    const page = mount(Onboarding, { global: { plugins: [i18n] } });
    wrapper = page;
    const languageButton = page
      .findAll('button')
      .find((button) => button.text() === label);
    if (!languageButton) throw new Error(`Missing language option: ${label}`);
    await languageButton.trigger('click');
    expect(page.text()).toContain(i18n.global.t('onboarding.title'));
    expect(document.documentElement.lang).toBe(locale);
    expect(localStorage.getItem('theparlor.locale')).toBe(locale);
    await page.get('#parlor-first').setValue('Test');
    await page.get('#parlor-last').setValue('User');
    await page.get('#parlor-phone').setValue('0812345678');
    await page.get('form').trigger('submit');

    await vi.waitFor(() =>
      expect(page.text()).toContain(i18n.global.t('onboarding.ready')),
    );
    expect(submitted).toEqual({
      firstName: 'Test',
      lastName: 'User',
      phoneNumber: '+66812345678',
      preferences: { preferredLocale: locale },
    });
    expect(useAccount().value).toEqual({ status: 'ready', account: profile });
    expect(fetch).toHaveBeenCalledTimes(3);
  },
);
