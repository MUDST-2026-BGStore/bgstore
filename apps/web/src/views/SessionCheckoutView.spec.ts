import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import { messages } from '../i18n';
import { routes } from '../router';
import { route, stubApi } from '../test/api-stub';
import SessionCheckoutView from './SessionCheckoutView.vue';

const SESSION = {
  reservationId: 'res-1',
  locationName: 'Central — Rama I Road, Pathum Wan',
  tableName: 'Table 12',
  // Just under two hours, so the rounded-up hour count is stable at 2.
  startedAt: new Date(Date.now() - 119 * 60 * 1000).toISOString(),
  partySize: 2,
  ratePerHour: 50,
  accruedAmount: 200,
  currency: 'THB',
};

const sessionHandler = route('/me/active-session', { body: SESSION });

/**
 * The checkout screen links back to the active-session screen, so the test
 * router needs that route even though the screen is mounted on its own.
 */
async function renderCheckout() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/checkout', component: SessionCheckoutView },
      {
        path: '/sessions/active',
        name: 'client-active-session',
        component: { template: '<div />' },
      },
      ...routes,
    ],
  });
  await router.push('/checkout');
  await router.isReady();

  const wrapper = mount(SessionCheckoutView, {
    global: {
      plugins: [
        router,
        createI18n({ legacy: false, locale: 'en', messages }),
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: { queries: { retry: false } },
            }),
          },
        ],
      ],
    },
  });
  await flushPromises();

  return wrapper;
}

describe('SessionCheckoutView', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the recorded bill and asks staff to settle the session', async () => {
    const calls = stubApi([
      sessionHandler,
      route('/reservations/res-1/assistance', { body: {} }, 'POST'),
    ]);

    const wrapper = await renderCheckout();

    expect(wrapper.get('.receipt-card footer').text()).toContain('200.00');
    expect(wrapper.get('.receipt-card').text()).toContain('2 people × 2 hours');
    expect(wrapper.get('.receipt-card').text()).toContain('/ hour');

    await wrapper.get('.request-settlement').trigger('click');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/assistance'));
    expect(request?.method).toBe('POST');
    expect(JSON.parse(request?.body ?? '{}')).toMatchObject({
      kind: 'EndPlaying',
    });
    expect(wrapper.get('.settlement-complete').text()).toContain(
      'Settlement requested',
    );
    expect(wrapper.find('.settlement-pane').exists()).toBe(false);
    wrapper.unmount();
  });

  it('tells the client when there is no session to settle', async () => {
    stubApi([
      route('/me/active-session', { status: 404, body: { status: 404 } }),
    ]);

    const wrapper = await renderCheckout();

    expect(wrapper.text()).toContain('No active session');
    expect(wrapper.find('.checkout-panel').exists()).toBe(false);
    wrapper.unmount();
  });
});
