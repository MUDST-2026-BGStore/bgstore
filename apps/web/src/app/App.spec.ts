import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { client } from '../generated/api/client.gen';
import { messages } from '../i18n';
import { createI18n } from 'vue-i18n';

describe('BGStore walking skeleton', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the API greeting after the authenticated path succeeds', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            message: 'Hello, BGStore!',
            service: 'bgstore-api',
            database: 'connected',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const i18n = createI18n({
      legacy: false,
      locale: 'en',
      messages,
    });

    const wrapper = mount(App, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }], i18n],
      },
    });
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('BGStore');
    expect(wrapper.get('[data-testid="api-message"]').text()).toBe(
      'Hello, BGStore!',
    );
    expect(wrapper.text()).toContain('Database connected');
  });

  it('offers BFF sign-in when the API rejects the session', async () => {
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

    const wrapper = mount(App, {
      global: { plugins: [[VueQueryPlugin, { queryClient }], i18n] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Sign in required');
    expect(wrapper.get('a.button').attributes('href')).toBe(
      '/oauth2/authorization/keycloak',
    );
  });
});
