import { flushPromises, mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { nextTick } from 'vue';
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query';
import { apiGames, apiTaxonomy } from '../api/catalog.fixtures';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
// eslint-disable-next-line no-restricted-imports -- Verify the home page inside its actual customer shell.
import CustomerLayout from '../../../app/layouts/CustomerLayout.vue';
import { useAuthStore } from '../../../shared/stores/auth';
import { queryClient } from '../../../shared/lib/api';
import HomeIndex from './HomeIndex.vue';
import { useLocale } from '../../../shared/lib/locale';

/** Picks an option from a shared listbox select by its visible text. */
async function choose(label: string, option: string) {
  const match = page
    .get(`[role="listbox"][aria-label="${label}"]`)
    .findAll('[role="option"]')
    .find((item) => item.text().replace('✓', '').trim() === option);
  if (!match) throw new Error(`No option "${option}" in ${label}`);
  await match.trigger('click');
}
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));
let client: QueryClient;
function respond(config: { url: string }) {
  if (config.url === '/api/v1/games')
    return Promise.resolve({ items: apiGames });
  if (config.url === '/api/v1/categories')
    return Promise.resolve({ items: apiTaxonomy });
  if (config.url === '/api/v1/mechanics') return Promise.resolve({ items: [] });
  return Promise.reject(new Error(`Unexpected request: ${config.url}`));
}
beforeEach(() => {
  request.mockReset().mockImplementation(respond);
  HTMLDialogElement.prototype.showModal = vi.fn(function (
    this: HTMLDialogElement,
  ) {
    this.setAttribute('open', '');
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.removeAttribute('open');
  });
  localStorage.clear();
  queryClient.clear();
  localStorage.setItem('theparlor.store-context.v1', 'browse');
  useLocale().setLocale('en-US');
});

vi.mock('../../../shared/stores/auth', () => ({ useAuthStore: vi.fn() }));
let page: ReturnType<typeof mount>;

afterEach(() => {
  page?.unmount();
  client?.clear();
  document.body.innerHTML = '';
  window.history.replaceState({}, '', '/');
});

async function render(authenticated = false) {
  const logout = vi.fn().mockResolvedValue(undefined);
  vi.mocked(useAuthStore).mockReturnValue({
    authenticated,
    displayName: 'Test Player',
    username: 'player',
    roles: [],
    logout,
  } as unknown as ReturnType<typeof useAuthStore>);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: CustomerLayout,
        children: [
          { path: '', name: 'HomeIndex', component: HomeIndex },
          {
            path: 'games/:gameId',
            name: 'GameProduct',
            component: { template: '<p>Product page</p>' },
          },
          {
            path: 'login',
            name: 'AuthLogin',
            component: { template: '<p>Login destination</p>' },
          },
          {
            path: 'workspace',
            name: 'WorkspaceIndex',
            component: { template: '<p>Workspace destination</p>' },
          },
          {
            path: 'profile',
            name: 'AuthProfile',
            component: { template: '<p>Profile destination</p>' },
          },
        ],
      },
    ],
  });
  await router.push('/');
  await router.isReady();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  page = mount(CustomerLayout, {
    attachTo: document.body,
    global: {
      plugins: [
        createPinia(),
        router,
        [VueQueryPlugin, { queryClient: client }],
      ],
    },
  });
  await flushPromises();
  return { router, logout };
}

it('keeps anonymous sign-in inside the account dropdown and follows its login route', async () => {
  const { router } = await render();
  expect(page.find('a[href="/login"]').exists()).toBe(false);
  expect(page.get('h1').text()).toBe('Shop board games');
  const trigger = page.get('.account-trigger');
  await trigger.trigger('click');
  expect(trigger.attributes('aria-expanded')).toBe('true');
  expect(document.activeElement).toBe(page.get('#account-panel a').element);
  expect(page.get('#account-panel').text()).toContain('Sign in');
  expect(page.find('.sign-out').exists()).toBe(false);
  await page.get('#account-panel a').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.name).toBe('AuthLogin');
  expect(router.currentRoute.value.query).toEqual({ redirect: '/' });
  expect(trigger.attributes('aria-expanded')).toBe('false');
});

it('supports keyboard navigation, Escape, outside dismissal and tabbing away', async () => {
  await render(true);
  const trigger = page.get('.account-trigger');
  await trigger.trigger('keydown', { key: 'ArrowDown' });
  const items = page.findAll('#account-panel a, #account-panel button');
  expect(document.activeElement).toBe(items[0].element);
  await items[0].trigger('keydown', { key: 'ArrowDown' });
  expect(document.activeElement).toBe(items[1].element);
  await items[1].trigger('keydown', { key: 'End' });
  const lastItem = page.get('#account-panel .sign-out');
  expect(document.activeElement).toBe(lastItem.element);
  await lastItem.trigger('keydown', { key: 'ArrowDown' });
  expect(document.activeElement).toBe(items[0].element);
  await items[0].trigger('keydown', { key: 'Escape' });
  expect(trigger.attributes('aria-expanded')).toBe('false');
  expect(document.activeElement).toBe(trigger.element);
  await trigger.trigger('keydown', { key: 'ArrowUp' });
  expect(document.activeElement).toBe(page.get('.sign-out').element);
  document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
  await nextTick();
  expect(trigger.attributes('aria-expanded')).toBe('false');
  await trigger.trigger('click');
  (page.get('.search-field input').element as HTMLButtonElement).focus();
  await nextTick();
  expect(trigger.attributes('aria-expanded')).toBe('false');
});

it('shows profile, workspace and the existing logout action only for authenticated visitors', async () => {
  const { logout } = await render(true);
  await page.get('.account-trigger').trigger('click');
  expect(page.get('#account-panel').text()).toContain('Test Player');
  expect(page.get('a[href="/profile"]').text()).toContain('Your profile');
  expect(page.get('#account-panel a[href="/workspace"]').text()).toContain(
    'Open your workspace',
  );
  await page.get('.sign-out').trigger('click');
  expect(logout).toHaveBeenCalledOnce();
  expect(page.get('.account-trigger').attributes('aria-expanded')).toBe(
    'false',
  );
});

it('navigates to the profile page when clicking Your profile in the account dropdown', async () => {
  const { router } = await render(true);
  await page.get('.account-trigger').trigger('click');
  await page.get('#account-panel a[href="/profile"]').trigger('click');
  await flushPromises();
  expect(router.currentRoute.value.name).toBe('AuthProfile');
  expect(page.get('.account-trigger').attributes('aria-expanded')).toBe(
    'false',
  );
});

it('preserves sign-in error feedback with a route back to the account action', async () => {
  window.history.replaceState({}, '', '/?login_error');
  await render();
  expect(page.get('[role="alert"]').text()).toContain(
    'Open Account to try again',
  );
});

it('uses backend IDs/media, complete catalog filters and no fixture prices or availability', async () => {
  await render();
  expect(page.findAll('.game-card')).toHaveLength(2);
  const first = page.get(`[data-game="${apiGames[0].id}"]`);
  expect(first.get('a').attributes('href')).toBe(`/games/${apiGames[0].id}`);
  expect(first.get('img').attributes('src')).toBe('/images/games/azul.jpg');
  expect(page.find('.game-price').exists()).toBe(false);
  expect(page.find('.game-flags').exists()).toBe(false);
  expect(page.find('button[aria-label="To buy"]').exists()).toBe(false);
  await page.get('input[type="search"]').setValue('hidden roles');
  expect(page.findAll('.game-card')).toHaveLength(1);
  expect(page.get('.game-card').text()).toContain('Blood on the Clocktower');
  await page.get('.reset-link').trigger('click');
  await choose('Players', '4 players');
  expect(page.findAll('.game-card')).toHaveLength(1);
  await choose('Time at the table', 'Up to 30 minutes');
  expect(page.findAll('.game-card')).toHaveLength(0);
  await page.get('.empty-state button').trigger('click');
  expect(page.findAll('.game-card')).toHaveLength(2);
});

it('loads every cursor page before calculating counts and local filters', async () => {
  request.mockImplementation((config) =>
    config.url === '/api/v1/games'
      ? Promise.resolve(
          config.params.cursor
            ? { items: [apiGames[1]] }
            : { items: [apiGames[0]], nextCursor: 'page-2' },
        )
      : respond(config),
  );
  await render();
  expect(page.findAll('.game-card')).toHaveLength(2);
  expect(
    request.mock.calls.filter(([config]) => config.url === '/api/v1/games'),
  ).toHaveLength(2);
});

it('distinguishes pending, failed and empty catalog results and retries', async () => {
  request.mockImplementation((config) =>
    config.url === '/api/v1/games'
      ? new Promise(() => {
          /* Keep the response pending to verify loading UI. */
        })
      : respond(config),
  );
  await render();
  expect(page.text()).toContain('Loading games…');
  page.unmount();
  client.clear();
  request.mockImplementation((config) =>
    config.url === '/api/v1/games'
      ? Promise.reject(new Error('offline'))
      : respond(config),
  );
  await render();
  expect(page.text()).toContain('Games could not be loaded.');
  expect(page.find('.game-card').exists()).toBe(false);
  request.mockImplementation((config) =>
    config.url === '/api/v1/games'
      ? Promise.resolve({ items: [] })
      : respond(config),
  );
  await page.get('.empty-state button').trigger('click');
  await flushPromises();
  expect(page.text()).toContain('No matching games');
  expect(page.text()).not.toContain('Games could not be loaded.');
});

it('keeps API descriptions localized in list view', async () => {
  await render();
  await page.get('button[aria-label="List view"]').trigger('click');
  expect(page.get('.game-grid').classes()).toContain('list-view');
  useLocale().setLocale('th-TH');
  await nextTick();
  expect(page.text()).toContain('จัดเรียงกระเบื้องบนกระดานของคุณ');
  expect(page.text()).toContain('Find the demon in your town.');
});

it('sorts names and durations without changing cached shelf order', async () => {
  const shelf = [apiGames[1], { ...apiGames[0], playMinutesMax: 180 }];
  request.mockImplementation((config) =>
    config.url === '/api/v1/games'
      ? Promise.resolve({ items: shelf })
      : respond(config),
  );
  await render();
  const ids = () =>
    page.findAll('.game-card').map((card) => card.attributes('data-game'));
  expect(ids()).toEqual([apiGames[1].id, apiGames[0].id]);
  await choose('Sort games', 'Name: A–Z');
  expect(ids()).toEqual([apiGames[0].id, apiGames[1].id]);
  await choose('Sort games', 'Shortest playtime');
  expect(ids()).toEqual([apiGames[1].id, apiGames[0].id]);
  await choose('Sort games', 'Shelf order');
  expect(ids()).toEqual([apiGames[1].id, apiGames[0].id]);
  expect(shelf.map((game) => game.id)).toEqual([
    apiGames[1].id,
    apiGames[0].id,
  ]);
});

it('filters catalog discovery by games sold at or in the library of the chosen branch', async () => {
  const BRANCH = '00000000-0000-4000-8000-0000000000a1';
  localStorage.setItem('theparlor.store-context.v1', BRANCH);
  request.mockImplementation((config) => {
    if (config.url === '/api/v1/branches')
      return Promise.resolve({
        items: [
          {
            id: BRANCH,
            name: 'Siam',
            address: '483 Siam Square',
            openingHours: [],
            active: true,
            fulfillsDelivery: true,
          },
        ],
      });
    if (config.url === `/api/v1/branches/${BRANCH}/games`)
      return Promise.resolve({
        items: [
          {
            branchId: BRANCH,
            game: apiGames[0],
            saleEnabled: true,
            rentalEnabled: false,
            staffCanTeach: false,
            rentalHourlyRate: { amountMinor: 4000 },
            editions: [
              {
                editionId: '00000000-0000-4000-8000-0000000000e1',
                editionKind: 'BASE_GAME',
                languageCode: 'en',
                active: true,
                salePrice: { amountMinor: 149000 },
                saleOnHand: 3,
                saleHeld: 0,
                saleAvailable: 3,
              },
            ],
            rentalTotal: 0,
            rentalUsable: 0,
            rentalAvailableNow: 0,
          },
          {
            branchId: BRANCH,
            game: apiGames[1],
            saleEnabled: false,
            rentalEnabled: true,
            staffCanTeach: true,
            rentalHourlyRate: { amountMinor: 5000 },
            editions: [],
            rentalTotal: 2,
            rentalUsable: 2,
            rentalAvailableNow: 1,
          },
        ],
      });
    return respond(config);
  });

  await render();
  expect(page.findAll('.game-card')).toHaveLength(2);
  const soldCheck = page.get('input[aria-label="Sold here"]');
  const libraryCheck = page.get('input[aria-label="In this library"]');

  await soldCheck.setValue(true);
  expect(
    page.findAll('.game-card').map((card) => card.attributes('data-game')),
  ).toEqual([apiGames[0].id]);

  await soldCheck.setValue(false);
  await libraryCheck.setValue(true);
  expect(
    page.findAll('.game-card').map((card) => card.attributes('data-game')),
  ).toEqual([apiGames[1].id]);

  await soldCheck.setValue(true);
  expect(page.findAll('.game-card')).toHaveLength(0);

  await page.get('.reset-link').trigger('click');
  expect(page.findAll('.game-card')).toHaveLength(2);
});
