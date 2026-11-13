import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { apiGames, apiTaxonomy } from '../api/catalog.fixtures';
import { useLocale } from '../../../shared/lib/locale';
import { queryClient } from '../../../shared/lib/api';
import GameProduct from './GameProduct.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));
const BRANCH = '00000000-0000-4000-8000-0000000000a1';
const EDITION = '00000000-0000-4000-8000-0000000000e1';
const branches = [
  {
    id: BRANCH,
    name: 'Siam',
    address: 'A',
    openingHours: [],
    active: true,
    fulfillsDelivery: true,
  },
];
const offering = {
  branchId: BRANCH,
  game: apiGames[0],
  saleEnabled: true,
  rentalEnabled: true,
  staffCanTeach: true,
  rentalHourlyRate: { amountMinor: 4000 },
  editions: [
    {
      editionId: EDITION,
      editionKind: 'BASE_GAME',
      languageCode: 'th',
      active: true,
      salePrice: { amountMinor: 149000 },
      saleOnHand: 3,
      saleHeld: 1,
      saleAvailable: 2,
    },
  ],
  rentalTotal: 2,
  rentalUsable: 2,
  rentalAvailableNow: 1,
};
let page: ReturnType<typeof mount>;
let client: QueryClient;

beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
  localStorage.setItem('theparlor.store-context.v1', 'browse');
  useLocale().setLocale('en-US');
  request.mockReset().mockImplementation((config) => {
    if (config.url.includes('/editions'))
      return Promise.resolve({
        items: [
          {
            id: 'edition',
            gameId: apiGames[0].id,
            editionKind: 'BASE_GAME',
            languageCode: 'en',
            active: true,
          },
        ],
      });
    if (config.url === '/api/v1/branches')
      return Promise.resolve({ items: branches });
    if (config.url === `/api/v1/branches/${BRANCH}/games/${apiGames[0].id}`)
      return Promise.resolve(offering);
    if (config.url === '/api/v1/categories')
      return Promise.resolve({ items: apiTaxonomy });
    if (config.url === '/api/v1/mechanics')
      return Promise.resolve({ items: [] });
    const game = apiGames.find(
      (game) => config.url === `/api/v1/games/${game.id}`,
    );
    return game
      ? Promise.resolve(game)
      : Promise.reject(
          Object.assign(new Error('missing'), { response: { status: 404 } }),
        );
  });
});
afterEach(() => {
  page?.unmount();
  client?.clear();
});

async function render(id = apiGames[0].id) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/games/:gameId', name: 'GameProduct', component: GameProduct },
      {
        path: '/',
        name: 'HomeIndex',
        component: { template: '<p>Catalog</p>' },
      },
    ],
  });
  await router.push(`/games/${id}`);
  await router.isReady();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  page = mount(GameProduct, {
    global: {
      plugins: [
        createPinia(),
        router,
        [VueQueryPlugin, { queryClient: client }],
      ],
    },
  });
  await flushPromises();
  return router;
}

it('loads a direct UUID route and real media/editions, with no price until a store is chosen', async () => {
  await render();
  expect(page.get('h1').text()).toBe('Azul');
  expect(page.get('.product-media img').attributes('src')).toBe(
    '/images/games/azul.jpg',
  );
  expect(page.text()).toContain('Choose a store to see prices and stock.');
  expect(page.find('.purchase-form').exists()).toBe(false);
  expect(page.find('.branch-table').exists()).toBe(false);
  expect(page.text()).toContain('English');
  expect(document.title).toBe('Azul | The Parlor');
});

it('reacts to route and language changes without showing the previous game', async () => {
  const router = await render();
  useLocale().setLocale('th-TH');
  await flushPromises();
  expect(page.text()).toContain('จัดเรียงกระเบื้องบนกระดานของคุณ');
  const respond = request.getMockImplementation();
  if (!respond) throw new Error('Missing catalog responder');
  let release!: (game: (typeof apiGames)[1]) => void;
  const pending = new Promise<(typeof apiGames)[1]>((resolve) => {
    release = resolve;
  });
  request.mockImplementation((config) =>
    config.url === `/api/v1/games/${apiGames[1].id}`
      ? pending
      : respond(config),
  );
  await router.push(`/games/${apiGames[1].id}`);
  await flushPromises();
  expect(page.text()).not.toContain('Azul');
  expect(page.get('[role="status"]').text()).toBe('กำลังโหลดเกม…');
  release(apiGames[1]);
  await flushPromises();
  expect(page.get('h1').text()).toBe('Blood on the Clocktower');
  expect(page.find('.product-media img').exists()).toBe(false);
  expect(page.text()).toContain('Find the demon in your town.');
});

it('treats invalid IDs and backend 404 as not found, not a fixture fallback', async () => {
  const router = await render('azul');
  expect(page.get('h1').text()).toBe('Game not found');
  expect(
    request.mock.calls.some(([config]) => config.url === '/api/v1/games/azul'),
  ).toBe(false);
  await router.push('/games/00000000-0000-4000-8000-000000000099');
  await flushPromises();
  expect(page.get('h1').text()).toBe('Game not found');
});

it('distinguishes a failed detail request from 404 and can retry', async () => {
  request.mockImplementation((config) =>
    config.url.includes('/games/')
      ? Promise.reject(new Error('offline'))
      : Promise.resolve({ items: [] }),
  );
  await render();
  expect(page.text()).toContain('This game could not be loaded.');
  expect(page.text()).not.toContain('This game is not in the catalog.');
  request.mockImplementation((config) =>
    config.url.includes('/editions')
      ? Promise.resolve({ items: [] })
      : config.url.includes('/games/')
        ? Promise.resolve(apiGames[0])
        : Promise.resolve({ items: [] }),
  );
  await page.get('.product-missing button').trigger('click');
  await flushPromises();
  expect(page.get('h1').text()).toBe('Azul');
});

it('shows the chosen branch’s price and stock and adds the edition to the server cart', async () => {
  localStorage.setItem('theparlor.store-context.v1', BRANCH);
  const cartCalls: { url: string; method: string; data?: unknown }[] = [];
  const respond = request.getMockImplementation()!;
  request.mockImplementation((config) => {
    if (config.url.startsWith('/api/v1/carts')) {
      cartCalls.push({
        url: config.url,
        method: config.method,
        data: config.data,
      });
      const cart = {
        id: 'cart-1',
        fulfillment: { method: 'SHIPPING' },
        items: [],
        issues: [],
        subtotal: 0,
        checkoutReady: false,
        createdAt: '2026-11-12T00:00:00Z',
        updatedAt: '2026-11-12T00:00:00Z',
      };
      return Promise.resolve(
        config.url === '/api/v1/carts'
          ? { ...cart, token: 't'.repeat(40) }
          : cart,
      );
    }
    return respond(config);
  });
  await render();

  expect(page.get('.product-price').text()).toContain('1,490');
  expect(page.get('.stock-line').text()).toContain('Only 2 left');
  expect(page.get('.stock-line').text()).toContain('Pickup at Siam');
  // Library membership only: no live rental copy count on a product page.
  expect(page.get('.library-line').text()).toContain('In the rental library');
  expect(page.text()).not.toContain('rentalAvailableNow');
  expect(page.get('input[name="quantity"]').attributes('max')).toBe('2');

  await page.get('.purchase-form').trigger('submit');
  await flushPromises();
  expect(cartCalls.map((call) => `${call.method} ${call.url}`)).toEqual([
    'POST /api/v1/carts',
    'PATCH /api/v1/carts/cart-1',
    'POST /api/v1/carts/cart-1/items',
  ]);
  expect(cartCalls[1].data).toEqual({
    fulfillment: { method: 'PICKUP', pickupBranchId: BRANCH },
  });
  expect(cartCalls[2].data).toEqual({ editionId: EDITION, quantity: 1 });
  expect(page.text()).toContain('Added to your cart.');
});

it('compares rental library membership and selected edition retail stock across branches from the API', async () => {
  const ARI = '00000000-0000-4000-8000-0000000000a2';
  const RIVERSIDE = '00000000-0000-4000-8000-0000000000a3';
  localStorage.setItem('theparlor.store-context.v1', BRANCH);
  const respond = request.getMockImplementation();
  if (!respond) throw new Error('Missing catalog responder');
  request.mockImplementation((config) => {
    if (config.url === '/api/v1/branches')
      return Promise.resolve({
        items: [
          ...branches,
          {
            id: ARI,
            name: 'Ari',
            address: 'B',
            openingHours: [],
            active: true,
            fulfillsDelivery: false,
          },
          {
            id: RIVERSIDE,
            name: 'Riverside',
            address: 'C',
            openingHours: [],
            active: true,
            fulfillsDelivery: false,
          },
        ],
      });
    if (config.url === `/api/v1/branches/${ARI}/games/${apiGames[0].id}`)
      return Promise.resolve({
        ...offering,
        branchId: ARI,
        rentalEnabled: false,
        editions: [
          {
            ...offering.editions[0],
            saleOnHand: 0,
            saleHeld: 0,
            saleAvailable: 0,
          },
        ],
      });
    if (config.url === `/api/v1/branches/${RIVERSIDE}/games/${apiGames[0].id}`)
      return Promise.reject(
        Object.assign(new Error('missing'), { response: { status: 404 } }),
      );
    return respond(config);
  });

  await render();
  const rows = page.findAll('.branch-row');
  expect(rows).toHaveLength(3);
  expect(rows[0].text()).toContain('Siam');
  expect(rows[0].text()).toContain('Included');
  expect(rows[0].text()).toContain('2 in stock');
  expect(rows[1].text()).toContain('Ari');
  expect(rows[1].text()).toContain('Not offered');
  expect(rows[1].text()).toContain('Sold out');
  expect(rows[2].text()).toContain('Riverside');
  expect(rows[2].text()).toContain('Not offered');
  expect(rows[2].text()).toContain('Sold out');
});
