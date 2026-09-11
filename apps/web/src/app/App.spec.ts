import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import App from './App.vue';
import { client } from '../generated/api/client.gen';
import { messages } from '../i18n';
import { router } from '../router';
import { route, stubApi } from '../test/api-stub';

enableAutoUnmount(afterEach);

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
            items: [{ id: 'branch-1', name: 'Central Rama II' }],
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
    expect(wrapper.get('[data-testid="home-branch"] h3').text()).toBe(
      'Central Rama II',
    );
    expect(wrapper.text()).toContain('Book at this branch');
    expect(
      wrapper.get('nav[aria-label="Primary navigation"] a[href="/history"]'),
    ).toBeTruthy();
  });

  it('opens on the floor overview for staff', async () => {
    stubApi([
      route('/me', {
        body: {
          subject: 'floor-staff',
          username: 'staff@example.test',
          email: 'staff@example.test',
          firstName: 'Local',
          lastName: 'Staff',
          roles: ['STAFF'],
          onboardingRequired: false,
        },
      }),
      route('/floor-overview', {
        body: {
          counts: { available: 1, occupied: 0, reserved: 0 },
          items: [],
          total: 0,
          page: 1,
          pageSize: 5,
          totalPages: 1,
        },
      }),
    ]);
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

    await vi.waitFor(async () => {
      await flushPromises();
      expect(wrapper.find('h1').text()).toBe('Floor overview');
    });
    expect(
      wrapper
        .get('nav[aria-label="Staff navigation"] [aria-current="page"]')
        .text(),
    ).toBe('Dashboard');
    expect(
      wrapper.get('nav[aria-label="Staff navigation"] a[href="/tables"]'),
    ).toBeTruthy();
    expect(
      wrapper
        .find('nav[aria-label="Staff navigation"] a[href="/history"]')
        .exists(),
    ).toBe(false);
  });

  it('lets a guest without a session look around the home page', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request) => {
        const url = input instanceof Request ? input.url : input.toString();
        return url.endsWith('/me')
          ? new Response(null, { status: 401 })
          : new Response(
              JSON.stringify({
                items: [{ id: 'branch-1', name: 'Central Rama II' }],
              }),
              {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
              },
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

    expect(wrapper.text()).not.toContain('Sign in to BGStore');
    expect(wrapper.get('[data-testid="home-branch"] h3').text()).toBe(
      'Central Rama II',
    );
    expect(wrapper.get('a[href$="&signup"]').attributes('href')).toBe(
      '/oauth2/authorization/keycloak?returnTo=%2F&signup',
    );
  });

  it('offers BFF sign-in when a guest opens a screen that needs a session', async () => {
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
    await router.push('/games');
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Sign in to BGStore');
    expect(wrapper.get('a.button').attributes('href')).toBe(
      '/oauth2/authorization/keycloak?returnTo=%2Fgames',
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

  it('renders account management inside the shared application shell', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: Infinity },
      },
    });
    queryClient.setQueryData(['current-user'], {
      subject: 'a9c7022e-a678-4d50-aa1b-69c917001234',
      username: 'client@example.test',
      email: 'client@example.test',
      firstName: 'Local',
      lastName: 'Client',
      roles: ['CLIENT'],
      clientProfile: { phone: '+66812345678', completed: true },
      onboardingRequired: false,
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/account/manage');
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('User profile');
    expect(
      wrapper.get('header nav[aria-label="Primary navigation"]'),
    ).toBeTruthy();
    expect(router.currentRoute.value.name).toBe('user-profile');
  });

  it('redirects an unauthenticated visitor away from protected routes', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 401 })),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({ legacy: false, locale: 'en', messages });
    await router.push('/history');
    await router.isReady();

    mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], router, i18n],
      },
    });
    await vi.waitFor(() =>
      expect(router.currentRoute.value.name).toBe('login'),
    );
    expect(router.currentRoute.value.query.redirect).toBe('/history');
  });
});
