import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { apiGames, apiTaxonomy } from '../api/catalog.fixtures';
import { useLocale } from '../../../shared/lib/locale';
import GameProduct from './GameProduct.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));
let page: ReturnType<typeof mount>;
let client: QueryClient;

beforeEach(() => {
  localStorage.clear();
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

it('loads a direct UUID route and real media/editions without preview price or stock claims', async () => {
  await render();
  expect(page.get('h1').text()).toBe('Azul');
  expect(page.get('.product-media img').attributes('src')).toBe(
    '/images/games/azul.jpg',
  );
  expect(page.text()).toContain('Price and stock information is unavailable.');
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
