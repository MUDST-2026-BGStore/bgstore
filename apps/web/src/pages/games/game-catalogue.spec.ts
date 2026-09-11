import { flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import GameCatalogueDetailPage from './GameCatalogueDetailPage.vue';
import GameCataloguePage from './GameCataloguePage.vue';
import {
  lastQuery,
  renderScreen,
  route,
  stubApi,
  type ApiHandler,
} from '../../test/api-stub';
import {
  explodingKittens,
  explodingKittensDetail,
  gameList,
  splendor,
  ticketToRide,
  ticketToRideId,
} from '../../test/fixtures';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function catalogue(...handlers: ApiHandler[]) {
  const calls = stubApi(handlers);
  const { wrapper } = await renderScreen(GameCataloguePage, '/games');

  return { wrapper, calls };
}

async function detail(gameId: string, ...handlers: ApiHandler[]) {
  stubApi(handlers);
  const { wrapper } = await renderScreen(
    GameCatalogueDetailPage,
    '/games/' + gameId,
  );

  return wrapper;
}

describe('game catalogue screen', () => {
  it('renders a card per game with its player count and play time', async () => {
    const withCover = {
      ...explodingKittens,
      playTimeMinutes: 15,
      coverImageUrl: 'https://cdn.example.com/ek-box.jpg',
    };
    const { wrapper } = await catalogue(
      route('/games', { body: gameList([withCover, splendor]) }),
    );

    expect(wrapper.get('h1').text()).toBe('All games');
    expect(wrapper.get('[data-testid="catalogue-count"]').text()).toBe(
      '2 games',
    );

    const cards = wrapper.findAll('[data-testid="catalogue-card"]');
    expect(cards).toHaveLength(2);
    expect(cards[0].attributes('href')).toBe('/games/' + explodingKittens.id);
    expect(cards[0].text()).toContain('Exploding Kittens');
    expect(cards[0].text()).toContain('2–5 players · 15 min');
    expect(cards[0].get('img').attributes('src')).toBe(
      'https://cdn.example.com/ek-box.jpg',
    );
    // No play time and no photo: the line stops at the players, and the
    // thumbnail keeps its empty tile rather than a broken image.
    expect(cards[1].text()).toContain('2–4 players');
    expect(cards[1].text()).not.toContain('min');
    expect(cards[1].find('img').exists()).toBe(false);
  });

  it('asks only for games the store still offers, in the reader’s language', async () => {
    const { calls } = await catalogue(route('/games', { body: gameList() }));

    const sent = lastQuery(calls, '/games');
    expect(sent?.get('lifecycle')).toBe('active');
    expect(sent?.get('locale')).toBe('en');
    expect(sent?.get('page')).toBe('0');
  });

  it('narrows the list to the chosen category chip', async () => {
    const { wrapper, calls } = await catalogue(
      route('/games', { body: gameList() }),
    );

    const chips = wrapper.findAll('[role="group"] button');
    expect(chips.map((chip) => chip.text())).toEqual([
      'All',
      'Card games',
      'Party',
      'Strategy',
      'Family',
    ]);
    expect(chips[0].attributes('aria-pressed')).toBe('true');

    await chips[3].trigger('click');
    await flushPromises();

    expect(lastQuery(calls, '/games')?.get('category')).toBe('strategy');
    expect(chips[3].attributes('aria-pressed')).toBe('true');
    expect(chips[0].attributes('aria-pressed')).toBe('false');
  });

  it('searches once typing settles', async () => {
    vi.useFakeTimers();
    const { wrapper, calls } = await catalogue(
      route('/games', { body: gameList() }),
    );

    await wrapper.get('#catalogue-search').setValue('  kitt ');
    expect(lastQuery(calls, '/games')?.get('search')).toBeNull();

    await vi.advanceTimersByTimeAsync(300);
    await flushPromises();

    expect(lastQuery(calls, '/games')?.get('search')).toBe('kitt');
  });

  it('tells an empty store from a search that found nothing', async () => {
    const { wrapper } = await catalogue(
      route('/games', { body: gameList([]) }),
    );

    expect(wrapper.get('[data-testid="catalogue-empty"]').text()).toBe(
      'There are no games in the catalogue yet.',
    );
    expect(wrapper.get('[data-testid="catalogue-count"]').text()).toBe(
      'No games',
    );

    await wrapper.findAll('[role="group"] button')[1].trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="catalogue-empty"]').text()).toBe(
      'No games match this search.',
    );
  });

  it('loads the next page of cards on request', async () => {
    const firstPage = gameList([explodingKittens], {
      page: { number: 0, size: 24, totalElements: 2, totalPages: 2 },
    });
    const secondPage = gameList([splendor], {
      page: { number: 1, size: 24, totalElements: 2, totalPages: 2 },
    });
    const { wrapper } = await catalogue((request) => {
      const url = new URL(request.url);
      if (url.pathname !== '/api/v1/games') {
        return undefined;
      }
      return {
        body: url.searchParams.get('page') === '1' ? secondPage : firstPage,
      };
    });

    expect(wrapper.findAll('[data-testid="catalogue-card"]')).toHaveLength(1);

    const more = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Show more games');
    await more?.trigger('click');
    await flushPromises();

    expect(wrapper.findAll('[data-testid="catalogue-card"]')).toHaveLength(2);
    expect(
      wrapper
        .findAll('button')
        .some((button) => button.text() === 'Show more games'),
    ).toBe(false);
  });

  it('offers a retry when the catalogue cannot be loaded', async () => {
    const { wrapper } = await catalogue(
      route('/games', { status: 500, body: { status: 500 } }),
    );

    expect(wrapper.get('[data-testid="catalogue-error"]').text()).toBe(
      'We could not load the games.',
    );
  });

  it('frames the page with the guest header, with Game marked as current', async () => {
    const { wrapper } = await catalogue(route('/games', { body: gameList() }));

    const current = wrapper.get('header [aria-current="page"]');
    expect(current.text()).toBe('Game');
    expect(current.attributes('href')).toBe('/games');
    expect(wrapper.get('header').text()).toContain('Book a table');
  });
});

describe('game catalogue detail screen', () => {
  const kittens = route('/games/' + explodingKittensDetail.id, {
    body: explodingKittensDetail,
  });

  it('shows the title, the other-language title and the three stats', async () => {
    const wrapper = await detail(explodingKittensDetail.id, kittens);

    expect(wrapper.get('h1').text()).toBe('Exploding Kittens');
    expect(wrapper.get('[data-testid="catalogue-subtitle"]').text()).toBe(
      'เหมียวระเบิด',
    );
    expect(wrapper.get('nav[aria-label="Breadcrumb"]').text()).toContain(
      'All games',
    );

    const stats = wrapper.findAll('dl > div');
    expect(stats.map((stat) => stat.text())).toEqual([
      'Players2–5 players',
      'Play time15 min',
      'DifficultyEasy',
    ]);
    expect(wrapper.text()).toContain('Card games');
    expect(wrapper.text()).toContain('Popular');
  });

  it('counts the copies free right now across every branch', async () => {
    const wrapper = await detail(explodingKittensDetail.id, kittens);

    const availability = wrapper.get('[data-testid="catalogue-availability"]');
    expect(availability.text()).toBe('3 sets free');
    expect(availability.classes()).toContain('text-success-fg');
  });

  it('says when every copy is out rather than reporting none free', async () => {
    const wrapper = await detail(
      explodingKittensDetail.id,
      route('/games/' + explodingKittensDetail.id, {
        body: { ...explodingKittensDetail, status: 'allCopiesOut' },
      }),
    );

    const availability = wrapper.get('[data-testid="catalogue-availability"]');
    expect(availability.text()).toBe('All sets are in use');
    expect(availability.classes()).toContain('text-warning-fg');
  });

  it('reveals the branches that hold the game on request', async () => {
    const wrapper = await detail(explodingKittensDetail.id, kittens);

    const toggle = wrapper.get('button[aria-controls="catalogue-branches"]');
    expect(toggle.attributes('aria-expanded')).toBe('false');

    await toggle.trigger('click');

    expect(toggle.attributes('aria-expanded')).toBe('true');
    const rows = wrapper.findAll('[data-testid="catalogue-branches"] li');
    // A branch with no copies is not somewhere to find the game.
    expect(
      rows.map((row) => row.findAll('span').map((cell) => cell.text())),
    ).toEqual([
      ['Big C Rama I', 'All in use'],
      ['Central Rama II', '3 free'],
    ]);
  });

  it('writes out the guide, falling back to the player range for players', async () => {
    const wrapper = await detail(explodingKittensDetail.id, kittens);

    const bullets = wrapper.findAll('[data-testid="catalogue-goal"]');
    expect(bullets.map((bullet) => bullet.text())).toEqual([
      'Goal: Be the last player left standing.',
      'Players: 2–5 (up to 10 with the Party Pack)',
    ]);

    const steps = wrapper.findAll('[data-testid="catalogue-step"]');
    expect(steps).toHaveLength(2);
    expect(steps[0].text()).toContain('Deal the cards');
    expect(steps[0].text()).toContain('Everyone gets a Defuse card');
    expect(steps[1].text()).toContain('End your turn');
  });

  it('keeps the goal card and drops the walkthrough for a game with no guide', async () => {
    const wrapper = await detail(
      ticketToRideId,
      route('/games/' + ticketToRideId, { body: ticketToRide }),
    );

    expect(
      wrapper.findAll('[data-testid="catalogue-goal"]').map((b) => b.text()),
    ).toEqual(['Players: 2–5 players']);
    expect(wrapper.find('[data-testid="catalogue-step"]').exists()).toBe(false);
    // No photo, and no arrows to page through nothing.
    expect(wrapper.find('[data-testid="catalogue-photo"]').exists()).toBe(
      false,
    );
    expect(wrapper.find('button[aria-label="Next photo"]').exists()).toBe(
      false,
    );
  });

  it('pages through the photos in both directions and wraps around', async () => {
    const wrapper = await detail(explodingKittensDetail.id, kittens);
    const photo = () =>
      wrapper.get('[data-testid="catalogue-photo"]').attributes('src');

    expect(photo()).toBe('https://cdn.example.com/ek-box.jpg');

    await wrapper.get('button[aria-label="Next photo"]').trigger('click');
    expect(photo()).toBe('https://cdn.example.com/ek-cards.jpg');

    await wrapper.get('button[aria-label="Next photo"]').trigger('click');
    expect(photo()).toBe('https://cdn.example.com/ek-box.jpg');

    await wrapper.get('button[aria-label="Previous photo"]').trigger('click');
    expect(photo()).toBe('https://cdn.example.com/ek-cards.jpg');
  });

  it('renders its own not-found state inside the guest header', async () => {
    const wrapper = await detail(
      'not-in-any-fixture',
      route('/games/not-in-any-fixture', {
        status: 404,
        body: { status: 404 },
      }),
    );

    expect(wrapper.get('[data-testid="catalogue-game-not-found"]').text()).toBe(
      'That game is no longer in the inventory.',
    );
    expect(wrapper.get('header').text()).toContain('Book a table');
  });
});
