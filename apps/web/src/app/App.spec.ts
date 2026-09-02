import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import App from './App.vue';
import { client } from '../generated/api/client.gen';
import { messages } from '../i18n';
import { router } from '../router';

describe('BGStore authentication context', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses the cookie-backed user context before rendering the app', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request) => {
        const url = input instanceof Request ? input.url : input.toString();
        if (url.endsWith('/me')) {
          return new Response(
            JSON.stringify({
              subject: 'a9c7022e-a678-4d50-aa1b-69c917001234',
              username: 'client@example.test',
              email: 'client@example.test',
              firstName: 'Local',
              lastName: 'Client',
              roles: ['CLIENT'],
              clientProfile: { phone: '+66812345678', completed: true },
              onboardingRequired: false,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response(
          JSON.stringify({
            message: 'Hello, BGStore!',
            service: 'bgstore-api',
            database: 'connected',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/');
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('BGStore');
    expect(wrapper.get('[data-testid="api-message"]').text()).toBe(
      'Hello, BGStore!',
    );
  });

  it('offers BFF sign-in when no application session exists', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Unauthorized', status: 401 }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/');
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Sign in to BGStore');
    expect(wrapper.get('a.button').attributes('href')).toBe(
      '/oauth2/authorization/keycloak?returnTo=%2F',
    );
  });

  it('sends an incomplete client to onboarding before rendering app content', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            subject: 'a9c7022e-a678-4d50-aa1b-69c917001234',
            username: 'client@example.test',
            email: 'client@example.test',
            firstName: 'Local',
            lastName: 'Client',
            roles: ['CLIENT'],
            clientProfile: { completed: false },
            onboardingRequired: true,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/');
    await router.isReady();

    mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await flushPromises();

    expect(router.currentRoute.value.name).toBe('onboarding');
    expect(router.currentRoute.value.query.returnTo).toBe('/');
  });
});
