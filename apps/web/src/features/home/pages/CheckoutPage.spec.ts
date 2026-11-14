import { flushPromises, mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { clearAuth, initAuth } from '@theparlor/auth';
import { useLocale } from '../../../shared/lib/locale';
import { queryClient } from '../../../shared/lib/api';
import { resetCardTokenization } from '../../../shared/lib/card-tokens';
import { CART_SESSION_KEY } from '../stores/cart';
import CheckoutPage from './CheckoutPage.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const AZUL = '00000000-0000-4000-8000-0000000000e1';
const CART = '00000000-0000-4000-8000-0000000000c1';
const ORDER = '00000000-0000-4000-8000-0000000000d1';
const TOKEN = 'guest-token-0123456789abcdef0123456789abcdef';
const QR = 'data:image/svg+xml;base64,PHN2Zy8+';

type Fulfillment = 'SHIPPING' | 'PICKUP';

function cartView(fulfillment: Fulfillment) {
  return {
    id: CART,
    fulfillment:
      fulfillment === 'PICKUP'
        ? { method: 'PICKUP', branchId: SIAM }
        : { method: 'SHIPPING' },
    items: [
      {
        id: AZUL,
        editionId: AZUL,
        gameId: 'game-azul',
        editionKind: 'BASE_GAME',
        editionLanguage: 'en',
        gameName: 'Azul',
        quantity: 1,
        unitPrice: { amountMinor: 74000 },
        lineTotal: { amountMinor: 74000 },
        available: 3,
      },
    ],
    issues: [],
    subtotal: 74000,
    shippingFee: { amountMinor: fulfillment === 'SHIPPING' ? 5000 : 0 },
    total: { amountMinor: fulfillment === 'SHIPPING' ? 79000 : 74000 },
    checkoutReady: true,
    createdAt: '2026-11-12T00:00:00Z',
    updatedAt: '2026-11-12T00:00:00Z',
  };
}

function order(
  status: 'PLACED' | 'CANCELLED',
  payment: Record<string, unknown>,
) {
  return {
    id: ORDER,
    branchId: SIAM,
    customer: { kind: 'REGISTERED', subject: 'sub-1' },
    source: 'CUSTOMER',
    status,
    items: [],
    subtotal: { amountMinor: 74000 },
    shippingFee: { amountMinor: 0 },
    total: { amountMinor: 74000 },
    contact: { name: 'Somchai Jaidee' },
    fulfillmentMethod: 'PICKUP',
    payment: { amount: { amountMinor: 74000 }, ...payment },
    createdAt: '2026-11-14T03:00:00Z',
    createdBySubject: 'sub-1',
  };
}

const pendingQr = () =>
  order('PLACED', {
    method: 'PROMPTPAY',
    status: 'PENDING',
    promptPayQr: QR,
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
  });

let signedIn = false;
let fulfillment: Fulfillment = 'SHIPPING';
let polled: () => unknown = pendingQr;
const checkouts: unknown[] = [];

beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
  clearAuth();
  resetCardTokenization();
  useLocale().setLocale('en-US');
  signedIn = false;
  fulfillment = 'SHIPPING';
  polled = pendingQr;
  checkouts.length = 0;
  request.mockReset().mockImplementation((config) => {
    const url: string = config.url;
    if (url === '/api/auth/session')
      return Promise.resolve(
        signedIn
          ? {
              authenticated: true,
              profile: {
                subject: 'sub-1',
                username: 'somchai',
                email: 'somchai@example.invalid',
                emailVerified: true,
                roles: [],
              },
            }
          : { authenticated: false },
      );
    if (url === '/api/v1/branches')
      return Promise.resolve({
        items: [
          {
            id: SIAM,
            name: 'Siam',
            address: 'A',
            openingHours: [],
            active: true,
            fulfillsDelivery: true,
          },
        ],
      });
    if (url === '/api/v1/me/cart' || url === `/api/v1/carts/${CART}`)
      return Promise.resolve(cartView(fulfillment));
    if (url === `/api/v1/carts/${CART}/checkout`) {
      checkouts.push(config.data);
      return Promise.resolve(pendingQr());
    }
    if (url === `/api/v1/branches/${SIAM}/orders/${ORDER}`)
      return Promise.resolve(polled());
    return Promise.resolve({ items: [] });
  });
});

afterEach(() => {
  vi.useRealTimers();
});

async function mountPage() {
  if (signedIn) await initAuth();
  else
    localStorage.setItem(
      CART_SESSION_KEY,
      JSON.stringify({ id: CART, token: TOKEN }),
    );
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/cart', component: { template: '<div />' } },
      { path: '/checkout', component: CheckoutPage },
    ],
  });
  await router.push('/checkout');
  const page = mount(CheckoutPage, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, [VueQueryPlugin, { queryClient }]],
      stubs: { CartFulfillment: true },
    },
  });
  await flushPromises();
  return page;
}

const methods = (page: Awaited<ReturnType<typeof mountPage>>) =>
  page
    .findAll('[data-method]')
    .map((option) => option.attributes('data-method'));

it('offers guests card payment only', async () => {
  const page = await mountPage();
  expect(methods(page)).toEqual(['CARD']);
  page.unmount();
});

it('offers signed-in customers PromptPay, and cash on delivery only when the cart ships', async () => {
  signedIn = true;
  const shipping = await mountPage();
  expect(methods(shipping)).toEqual(['CARD', 'PROMPTPAY', 'COD']);
  shipping.unmount();

  fulfillment = 'PICKUP';
  const pickup = await mountPage();
  expect(methods(pickup)).toEqual(['CARD', 'PROMPTPAY']);
  pickup.unmount();
});

async function placePromptPayOrder() {
  signedIn = true;
  fulfillment = 'PICKUP';
  const page = await mountPage();
  await page.find('[data-method="PROMPTPAY"] input').setValue(true);
  // Card fields only render for card payment.
  expect(page.find('input[autocomplete="cc-number"]').exists()).toBe(false);
  await page.find('input[autocomplete="tel"]').setValue('0812345678');
  vi.useFakeTimers();
  await page.find('.btn-place-order').trigger('click');
  await flushPromises();
  expect(checkouts).toHaveLength(1);
  expect(checkouts[0]).toMatchObject({ paymentMethod: 'PROMPTPAY' });
  expect(
    (checkouts[0] as { paymentToken?: string }).paymentToken,
  ).toBeUndefined();
  expect(page.find('img.qr-image').attributes('src')).toBe(QR);
  expect(page.find('.timer-tag').text()).toMatch(/^1[45]:\d\d$/);
  return page;
}

it('shows the QR and switches to the success state once the payment arrives', async () => {
  const page = await placePromptPayOrder();
  await vi.advanceTimersByTimeAsync(3000);
  expect(page.find('img.qr-image').exists()).toBe(true);

  polled = () => order('PLACED', { method: 'PROMPTPAY', status: 'CAPTURED' });
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  expect(page.find('img.qr-image').exists()).toBe(false);
  expect(page.find('#order-placed-title').text()).toBe('Order Placed');
  expect(page.text()).toContain('PromptPay · paid');

  // Paid: no more polling.
  const calls = request.mock.calls.length;
  await vi.advanceTimersByTimeAsync(9000);
  expect(request.mock.calls.length).toBe(calls);
  page.unmount();
});

it('tells the customer when the server cancelled an unpaid QR', async () => {
  const page = await placePromptPayOrder();
  polled = () => order('CANCELLED', { method: 'PROMPTPAY', status: 'VOIDED' });
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  expect(page.find('#promptpay-cancelled-title').text()).toBe(
    'Payment was not received, the order was cancelled',
  );
  expect(page.text()).toContain('Back to Cart');
  page.unmount();
});

it('stops polling when the page is left', async () => {
  const page = await placePromptPayOrder();
  page.unmount();
  const calls = request.mock.calls.length;
  await vi.advanceTimersByTimeAsync(9000);
  expect(request.mock.calls.length).toBe(calls);
});
