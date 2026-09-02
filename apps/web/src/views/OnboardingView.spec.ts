import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import OnboardingView from './OnboardingView.vue';
import { client } from '../generated/api/client.gen';
import { messages } from '../i18n';
import { router } from '../router';

describe('client onboarding', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('saves a country-coded phone number then returns to the requested page', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ phone: '+66812345678', completed: true }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/onboarding?returnTo=%2F');
    await router.isReady();

    const wrapper = mount(OnboardingView, {
      global: { plugins: [[VueQueryPlugin, { queryClient }], router, i18n] },
    });
    await wrapper.get('#countryCode').setValue('+66');
    await wrapper.get('#phoneNumber').setValue('081 234 5678');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const request = vi.mocked(fetch).mock.calls[0]?.[0] as Request;
    expect(request.url).toBe('http://localhost/api/v1/me/client-profile');
    expect(await request.text()).toBe(
      '{"countryCode":"+66","phoneNumber":"081 234 5678"}',
    );
    expect(router.currentRoute.value.fullPath).toBe('/');
  });

  it('keeps the client on onboarding when the profile is rejected', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Bad request', status: 400 }), {
          status: 400,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/onboarding?returnTo=https%3A%2F%2Fexample.test');
    await router.isReady();

    const wrapper = mount(OnboardingView, {
      global: { plugins: [[VueQueryPlugin, { queryClient }], router, i18n] },
    });
    await wrapper.get('#phoneNumber').setValue('not a phone number');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('We could not save that number');
    expect(router.currentRoute.value.name).toBe('onboarding');
  });
});
