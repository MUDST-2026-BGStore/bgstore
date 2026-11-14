import { flushPromises, mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { VueQueryPlugin } from '@tanstack/vue-query';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { clearAuth, initAuth } from '@theparlor/auth';
import { useLocale } from '../../../shared/lib/locale';
import { queryClient } from '../../../shared/lib/api';
import { resetCardTokenization } from '../../../shared/lib/card-tokens';
import { BRANCH_TIME_ZONE } from '../data/booking';
import CustomerReservePage from './CustomerReservePage.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const AZUL = '00000000-0000-4000-8000-0000000000e1';
const TABLE = '00000000-0000-4000-8000-0000000000t1';
const ITEM = '00000000-0000-4000-8000-0000000000m1';
const RESERVATION = '00000000-0000-4000-8000-0000000000r1';

function tomorrow() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: BRANCH_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(Date.now() + 86_400_000));
}

const availability = () => ({
  branchId: SIAM,
  startsAt: `${tomorrow()}T07:00:00+07:00`,
  endsAt: `${tomorrow()}T09:00:00+07:00`,
  partySize: 4,
  withinOpeningHours: true,
  asOf: '2026-11-12T00:00:00Z',
  tables: [
    {
      tableId: TABLE,
      label: 'Window 4',
      capacity: 6,
      hourlyRate: { amountMinor: 3000 },
      available: true,
    },
    {
      tableId: 'table-full',
      label: 'Corner 1',
      capacity: 2,
      hourlyRate: { amountMinor: 2000 },
      available: true,
    },
  ],
  games: [
    {
      gameId: AZUL,
      rentalEnabled: true,
      usableQuantity: 2,
      availableQuantity: 2,
      hourlyRate: { amountMinor: 4000 },
    },
  ],
});

const menu = () => ({
  items: [
    {
      item: {
        id: ITEM,
        sku: 'DRINK-COLA',
        names: { 'en-US': 'Iced Cola', 'th-TH': 'โคล่าเย็น' },
        descriptions: { 'en-US': 'Chilled' },
        category: 'BEVERAGES',
        price: { amountMinor: 8000 },
        dietary: null,
        popular: true,
      },
      available: 5,
    },
  ],
});

const reservationPayloads: unknown[] = [];
let signedIn = true;

beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
  clearAuth();
  resetCardTokenization();
  useLocale().setLocale('en-US');
  reservationPayloads.length = 0;
  signedIn = true;
  request.mockReset().mockImplementation((config) => {
    const method = (config.method ?? 'GET').toUpperCase();
    const url: string = config.url;
    if (url === '/api/auth/session')
      return Promise.resolve({
        authenticated: signedIn,
        profile: {
          subject: 'sub-1',
          username: 'somchai',
          emailVerified: true,
          roles: [],
        },
      });
    if (url === '/api/v1/branches')
      return Promise.resolve({
        items: [
          {
            id: SIAM,
            name: 'Siam',
            address: '483 Siam Square',
            openingHours: [],
            active: true,
            fulfillsDelivery: true,
          },
        ],
      });
    if (url === `/api/v1/branches/${SIAM}/availability`)
      return Promise.resolve(availability());
    if (url === `/api/v1/branches/${SIAM}/games`)
      return Promise.resolve({
        items: [
          {
            branchId: SIAM,
            game: { id: AZUL, name: 'Azul' },
            rentalEnabled: true,
            staffCanTeach: true,
            rentalHourlyRate: { amountMinor: 4000 },
            editions: [],
          },
        ],
      });
    if (url === `/api/v1/branches/${SIAM}/menu`) return Promise.resolve(menu());
    if (url === '/api/v1/payments/configuration')
      return Promise.resolve({ cardTokenization: 'SERVER' });
    if (url === '/api/v1/payments/tokens')
      return Promise.resolve({ token: 'pay-token' });
    if (url === `/api/v1/branches/${SIAM}/reservations` && method === 'POST') {
      reservationPayloads.push(config.data);
      return Promise.resolve({
        id: RESERVATION,
        branchId: SIAM,
        tableId: TABLE,
        customer: { kind: 'REGISTERED', subject: 'sub-1' },
        status: 'RESERVED',
        startsAt: config.data.startsAt,
        endsAt: config.data.endsAt,
        partySize: config.data.partySize,
        games: config.data.games,
        notes: config.data.notes,
        preOrder: config.data.preOrder
          ? {
              items: config.data.preOrder.items.map(
                (item: { menuItemId: string; quantity: number }) => ({
                  menuItemId: item.menuItemId,
                  name: 'Iced Cola',
                  quantity: item.quantity,
                  unitPrice: { amountMinor: 8000 },
                  lineTotal: { amountMinor: 8000 * item.quantity },
                }),
              ),
              subtotal: { amountMinor: 8000 },
              discount: { amountMinor: 800 },
              total: { amountMinor: 7200 },
              payment: { method: 'CARD', status: 'AUTHORIZED' },
            }
          : undefined,
        createdAt: '2026-11-12T00:00:00Z',
        createdBySubject: 'sub-1',
      });
    }
    return Promise.reject(new Error(`Unexpected ${method} ${url}`));
  });
});

afterEach(() => {
  localStorage.clear();
  document.body.innerHTML = '';
});

async function mountPage() {
  await initAuth();
  await flushPromises();
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'HomeIndex', component: { template: '<div />' } },
      { path: '/login', name: 'AuthLogin', component: { template: '<div />' } },
      {
        path: '/reservations/new',
        name: 'CustomerReserve',
        component: CustomerReservePage,
      },
      {
        path: '/reservations',
        name: 'MyReservations',
        component: { template: '<div />' },
      },
      {
        path: '/my-reservations',
        name: 'CustomerReservationsList',
        component: { template: '<div />' },
      },
    ],
  });
  await router.push('/reservations/new');
  const page = mount(CustomerReservePage, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, [VueQueryPlugin, { queryClient }]],
    },
  });
  await flushPromises();
  return page;
}

const primaryButton = (page: Awaited<ReturnType<typeof mountPage>>) =>
  page.findAll('.wizard-step .btn.btn-primary').at(-1)!;

it('books all selected hours through PromptPay without requesting card details', async () => {
  const original = request.getMockImplementation();
  if (!original)
    throw new Error('The booking transport fixture must be configured');
  const payment = {
    method: 'PROMPTPAY',
    status: 'PENDING',
    amount: { amountMinor: 6000 },
    promptPayQr: 'data:image/svg+xml;base64,PHN2Zy8+',
    expiresAt: new Date(Date.now() + 900_000).toISOString(),
  };
  let booked: Record<string, unknown> | undefined;
  request.mockImplementation(async (config) => {
    if (config.url === `/api/v1/branches/${SIAM}/reservations/${RESERVATION}`)
      return booked;
    const response = await original(config);
    if (
      config.url === `/api/v1/branches/${SIAM}/reservations` &&
      config.method === 'POST'
    ) {
      booked = { ...response, status: 'CONFIRMED', payment };
      return booked;
    }
    return response;
  });
  const page = await mountPage();
  for (let index = 0; index < 4; index++) {
    await primaryButton(page).trigger('click');
    await flushPromises();
  }
  expect(page.find('input[autocomplete="cc-number"]').exists()).toBe(false);
  expect(page.text()).toContain('฿60');
  await primaryButton(page).trigger('click');
  await flushPromises();
  expect(reservationPayloads[0]).toMatchObject({ paymentMethod: 'PROMPTPAY' });
  expect(reservationPayloads[0]).toMatchObject({ preOrder: undefined });
  expect(page.get('.receipt-badge').text()).toContain(
    'Awaiting PromptPay payment',
  );
  expect(
    page
      .get('img[alt="PromptPay QR code for this reservation"]')
      .attributes('src'),
  ).toBe(payment.promptPayQr);
  expect(
    request.mock.calls.some(
      ([config]) => config.url === '/api/v1/payments/tokens',
    ),
  ).toBe(false);
  page.unmount();
});
