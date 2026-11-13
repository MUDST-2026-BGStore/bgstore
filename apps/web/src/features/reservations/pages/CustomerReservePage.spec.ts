import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { clearAuth, initAuth } from '@theparlor/auth';
import { queryClient } from '../../../shared/lib/api';
import { useLocale } from '../../../shared/lib/locale';
import CustomerReservePage from './CustomerReservePage.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const TABLE = '00000000-0000-4000-8000-0000000000b1';
const CATAN = '00000000-0000-4000-8000-0000000000c1';
const COLA = '00000000-0000-4000-8000-0000000000d1';
const RESERVATION = '00000000-0000-4000-8000-0000000000e1';
// 10:00 on 13 November in Bangkok. The default visit is tomorrow at 14:00, 07:00Z.
const NOW = new Date('2026-11-13T03:00:00Z');
const STARTS = '2026-11-14T07:00:00.000Z';
const ENDS = '2026-11-14T09:00:00.000Z';

type Call = {
  method: string;
  url: string;
  params?: unknown;
  data?: unknown;
  headers: Record<string, string>;
};

const fail = (status?: number, data: object = {}) =>
  Promise.reject(
    Object.assign(new Error(status ? `HTTP ${status}` : 'Network Error'), {
      response: status ? { status, data } : undefined,
    }),
  );

function fakeApi() {
  const calls: Call[] = [];
  const state = {
    branchesFail: false,
    availabilityFail: false,
    open: true,
    tableFree: true,
    catanFree: 2,
    colaAvailable: 10,
    create: (): Promise<unknown> =>
      Promise.resolve({
        id: RESERVATION,
        branchId: SIAM,
        tableId: TABLE,
        customer: { kind: 'REGISTERED', accountId: 'acc-1' },
        source: 'CUSTOMER',
        status: 'CONFIRMED',
        startsAt: STARTS,
        endsAt: ENDS,
        partySize: 4,
        games: [
          { gameId: CATAN, quantity: 1, unitHourlyRate: { amountMinor: 4000 } },
        ],
        tableHourlyRate: { amountMinor: 15000 },
        // Deliberately differs from the browser's preview: the server decides.
        preOrder: {
          items: [
            {
              menuItemId: COLA,
              name: 'Cola (server)',
              quantity: 2,
              unitPrice: { amountMinor: 4500 },
              lineTotal: { amountMinor: 9000 },
            },
          ],
          subtotal: { amountMinor: 9000 },
          discount: { amountMinor: 900 },
          total: { amountMinor: 8100 },
          payment: {
            method: 'CARD',
            status: 'AUTHORIZED',
            amount: { amountMinor: 8100 },
            cardBrand: 'VISA',
            cardLast4: '4242',
          },
        },
        notes: 'Birthday',
        createdAt: NOW.toISOString(),
        createdBySubject: 'sub-1',
      }),
  };
  let tokens = 0;

  request.mockReset().mockImplementation((config, options) => {
    const method = (config.method ?? 'GET').toUpperCase();
    const url: string = config.url;
    calls.push({
      method,
      url,
      params: config.params,
      data: config.data,
      headers: { ...config.headers, ...options?.headers },
    });
    if (url === '/api/auth/session')
      return Promise.resolve({
        authenticated: true,
        profile: {
          subject: 'sub-1',
          username: 'pat',
          displayName: 'Pat',
          emailVerified: true,
          roles: [],
        },
      });
    if (url === '/api/v1/branches')
      return state.branchesFail
        ? fail(500)
        : Promise.resolve({
            items: [
              {
                id: SIAM,
                name: 'Siam',
                address: 'Siam Square',
                openingHours: [],
                active: true,
                fulfillsDelivery: true,
              },
            ],
          });
    if (url === `/api/v1/branches/${SIAM}/availability`)
      return state.availabilityFail
        ? fail(500)
        : Promise.resolve({
            branchId: SIAM,
            ...config.params,
            withinOpeningHours: state.open,
            asOf: NOW.toISOString(),
            tables: [
              {
                tableId: TABLE,
                label: 'Window 1',
                capacity: 4,
                hourlyRate: { amountMinor: 15000 },
                available: state.open && state.tableFree,
              },
            ],
            games: [
              {
                gameId: CATAN,
                rentalEnabled: true,
                usableQuantity: 2,
                availableQuantity: state.open ? state.catanFree : 0,
                hourlyRate: { amountMinor: 4000 },
              },
            ],
          });
    if (url === `/api/v1/branches/${SIAM}/games`)
      return Promise.resolve({
        items: [
          {
            branchId: SIAM,
            game: {
              id: CATAN,
              name: 'Catan',
              categories: ['Strategy'],
              mechanics: [],
              minPlayers: 3,
              maxPlayers: 4,
              playMinutesMin: 60,
              playMinutesMax: 90,
              active: true,
            },
            saleEnabled: false,
            rentalEnabled: true,
            staffCanTeach: true,
            rentalHourlyRate: { amountMinor: 4000 },
            editions: [],
            rentalTotal: 2,
            rentalUsable: 2,
            rentalAvailableNow: 2,
          },
        ],
      });
    if (url === `/api/v1/branches/${SIAM}/menu`)
      return Promise.resolve({
        branchId: SIAM,
        items: [
          {
            item: {
              id: COLA,
              sku: 'cola',
              category: 'BEVERAGES',
              names: { 'en-US': 'Cola' },
              price: { amountMinor: 5000 },
              popular: false,
            },
            available: state.colaAvailable,
          },
        ],
      });
    if (url === '/api/v1/payments/tokens')
      return Promise.resolve({
        token: `tok_${String(++tokens).padStart(16, '0')}`,
        cardBrand: 'VISA',
        cardLast4: '4242',
        expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      });
    if (url === `/api/v1/branches/${SIAM}/reservations` && method === 'POST')
      return state.create();
    return fail(404);
  });

  return {
    state,
    sent: (method: string, pattern: RegExp) =>
      calls.filter((call) => call.method === method && pattern.test(call.url)),
  };
}

let api: ReturnType<typeof fakeApi>;
let page: VueWrapper;
let client: QueryClient;

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  localStorage.clear();
  queryClient.clear();
  useLocale().setLocale('en-US');
  api = fakeApi();
  await initAuth();
});

afterEach(() => {
  page?.unmount();
  client?.clear();
  clearAuth();
  vi.useRealTimers();
});

async function render(query = '') {
  const stub = { template: '<p>elsewhere</p>' };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reservations/new', component: CustomerReservePage },
      {
        path: '/reservations',
        name: 'CustomerReservationsList',
        component: stub,
      },
      { path: '/', name: 'HomeIndex', component: stub },
      { path: '/login', name: 'AuthLogin', component: stub },
    ],
  });
  await router.push(`/reservations/new${query}`);
  await router.isReady();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  page = mount(CustomerReservePage, {
    global: {
      plugins: [
        createPinia(),
        router,
        [VueQueryPlugin, { queryClient: client }],
      ],
    },
  });
  await flushPromises();
}

async function click(label: string) {
  const button = page
    .findAll('button')
    .find((candidate) => candidate.text().includes(label));
  if (!button) throw new Error(`No button "${label}" in: ${page.text()}`);
  await button.trigger('click');
  await flushPromises();
}

/** Branch and time (defaults), a table, one Catan, and two colas. */
async function chooseVisit({ food = true } = {}) {
  await click('Check Available Tables');
  await page.find(`input[name="table"][value="${TABLE}"]`).setValue(true);
  await page.find('button[aria-label="More copies of Catan"]').trigger('click');
  await page.find('#res-notes').setValue('Birthday');
  await click('Continue →');
  if (food) {
    await page.find('button[aria-label="Add Cola"]').trigger('click');
    await page.find('button[aria-label="Add Cola"]').trigger('click');
  }
  await click(food ? 'Review & Pay' : 'Continue without food');
}

async function enterCard() {
  await page.find('#card-number').setValue('4242 4242 4242 4242');
  await page.find('#card-expiry').setValue('12/30');
  await page.find('#card-cvc').setValue('123');
  await page.find('#card-holder').setValue('Pat');
}

const bookings = () => api.sent('POST', /\/reservations$/);

it('books the chosen table, games and food, then shows the receipt the server returned', async () => {
  await render();
  expect(page.find<HTMLSelectElement>('#branch-select').element.value).toBe(
    SIAM,
  );
  await chooseVisit();

  const availability = api.sent('GET', /\/availability$/)[0];
  expect(availability.params).toEqual({
    startsAt: STARTS,
    endsAt: ENDS,
    partySize: 4,
  });
  // The preview is labelled as an estimate: 2 × ฿50, less 10%.
  expect(page.text()).toContain('Estimated total');
  expect(page.text()).toContain('฿90');

  await enterCard();
  await click('Confirm & Authorize Card');

  const [token] = api.sent('POST', /\/payments\/tokens$/);
  expect(token.data).toEqual({
    number: '4242424242424242',
    expMonth: 12,
    expYear: 2030,
    cvc: '123',
    holderName: 'Pat',
  });
  const [booking] = bookings();
  expect(booking.data).toEqual({
    tableId: TABLE,
    startsAt: STARTS,
    endsAt: ENDS,
    partySize: 4,
    games: [{ gameId: CATAN, quantity: 1 }],
    notes: 'Birthday',
    preOrder: {
      items: [{ menuItemId: COLA, quantity: 2 }],
      paymentToken: 'tok_0000000000000001',
    },
  });
  expect(booking.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
  expect(JSON.stringify(booking.data)).not.toContain('4242');

  const receipt = page.text();
  expect(receipt).toContain('Reservation Confirmed');
  expect(receipt).toContain(RESERVATION);
  expect(receipt).toContain('Saturday, November 14, 2026 · 14:00–16:00');
  expect(receipt).toContain('Window 1');
  expect(receipt).toContain('Catan × 1');
  expect(receipt).toContain('Cola (server)');
  expect(receipt).toContain('฿81');
  expect(receipt).toContain(
    'VISA •••• 4242: authorized, charged when you check in',
  );
  await click('Book Another Visit');
  expect(page.text()).toContain('Choose Your Branch & Time');
});

it('books a table only without asking for a card', async () => {
  await render();
  await chooseVisit({ food: false });
  expect(page.find('#card-number').exists()).toBe(false);
  await click('Confirm Reservation');
  expect(api.sent('POST', /\/payments\/tokens$/)).toHaveLength(0);
  expect(bookings()[0].data).not.toHaveProperty('preOrder');
  expect(page.text()).toContain('Reservation Confirmed');
});

it('shows an error, not sample branches, when branches cannot be loaded', async () => {
  api.state.branchesFail = true;
  await render();
  expect(page.text()).toContain('Branches could not be loaded.');
  expect(page.find('#branch-select').exists()).toBe(false);
  expect(page.text()).not.toMatch(/Ari|Riverside/);

  api.state.branchesFail = false;
  await click('Try again');
  expect(page.find('#branch-select').exists()).toBe(true);
});

it('shows availability errors and closed times instead of sample tables', async () => {
  api.state.availabilityFail = true;
  await render();
  await click('Check Available Tables');
  expect(page.text()).toContain('Availability could not be loaded.');
  expect(page.findAll('input[name="table"]')).toHaveLength(0);

  api.state.availabilityFail = false;
  api.state.open = false;
  await click('Try again');
  expect(page.text()).toContain('The branch is not open for this whole visit');
  expect(page.findAll('input[name="table"]')).toHaveLength(0);
});

it('rejects a start time that has passed on the branch clock', async () => {
  await render();
  await page.find('#res-date').setValue('2026-11-13');
  await page.find('#res-time').setValue('09:30');
  expect(page.text()).toContain('Choose a start time that has not passed.');
  const next = page
    .findAll('button')
    .find((button) => button.text().includes('Check Available Tables'));
  expect(next?.attributes('disabled')).toBeDefined();
});

it('keeps the choices and shows no receipt when the card is declined', async () => {
  api.state.create = () => fail(402, { code: 'PAYMENT_DECLINED' });
  await render();
  await chooseVisit();
  await enterCard();
  await click('Confirm & Authorize Card');

  expect(page.text()).toContain('The card was declined. Nothing was booked');
  expect(page.text()).not.toContain('Reservation Confirmed');
  expect(page.text()).toContain('Window 1');
  expect(page.text()).toContain('Catan × 1');
  expect(page.find<HTMLInputElement>('#card-holder').element.value).toBe('Pat');
});

it('stops before booking when the table was taken since it was chosen', async () => {
  await render();
  await chooseVisit();
  await enterCard();
  api.state.tableFree = false;
  await click('Confirm & Authorize Card');

  expect(page.text()).toContain('Availability changed since you chose.');
  expect(page.text()).toContain('Your table is no longer free for this time.');
  expect(bookings()).toHaveLength(0);
  expect(api.sent('POST', /\/payments\/tokens$/)).toHaveLength(0);
  expect(page.text()).not.toContain('Reservation Confirmed');
});

it('stops before booking when menu stock fell below the food order', async () => {
  await render();
  await chooseVisit();
  await enterCard();
  api.state.colaAvailable = 1;
  await click('Confirm & Authorize Card');

  expect(page.text()).toContain('The branch menu changed. Updated: Cola.');
  expect(bookings()).toHaveLength(0);
  // The order now holds what the branch can serve.
  expect(page.text()).toContain('1× Cola');
});

it('retries a lost response with the same key and token, so it cannot book twice', async () => {
  let attempts = 0;
  const confirmed = api.state.create;
  api.state.create = () => (++attempts === 1 ? fail() : confirmed());
  await render();
  await chooseVisit();
  await enterCard();
  await click('Confirm & Authorize Card');

  expect(page.text()).toContain(
    'We could not confirm whether your booking went through.',
  );
  expect(page.text()).not.toContain('Reservation Confirmed');

  await click('Confirm & Authorize Card');
  const [lost, retry] = bookings();
  expect(retry.headers['Idempotency-Key']).toBe(
    lost.headers['Idempotency-Key'],
  );
  expect(retry.data).toEqual(lost.data);
  expect(api.sent('POST', /\/payments\/tokens$/)).toHaveLength(1);
  expect(page.text()).toContain('Reservation Confirmed');
});

it('offers food only inside the pre-order window', async () => {
  await render();
  await page.find('#res-date').setValue('2026-11-25');
  await click('Check Available Tables');
  await page.find(`input[name="table"][value="${TABLE}"]`).setValue(true);
  await click('Continue →');

  expect(page.text()).toContain(
    'Food can be pre-ordered only for visits starting within 6 days.',
  );
  expect(api.sent('GET', /\/menu$/)).toHaveLength(0);
  await click('Continue without food');
  await click('Confirm Reservation');
  expect(bookings()[0].data).toMatchObject({
    startsAt: '2026-11-25T07:00:00.000Z',
  });
  expect(bookings()[0].data).not.toHaveProperty('preOrder');
});
