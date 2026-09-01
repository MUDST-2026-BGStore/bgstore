import { flushPromises, type DOMWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AddGamePage from './AddGamePage.vue';
import EditGamePage from './EditGamePage.vue';
import GameDetailsPage from './GameDetailsPage.vue';
import GamesInventoryPage from './GamesInventoryPage.vue';
import {
  lastQuery,
  renderScreen,
  route,
  stubApi,
  type ApiHandler,
} from '../../test/api-stub';
import {
  branches,
  explodingKittens,
  gameList,
  splendor,
  ticketToRide,
  ticketToRideId,
} from '../../test/fixtures';

const branchDirectory = route('/branches', { body: { items: branches } });

afterEach(() => {
  vi.unstubAllGlobals();
});

async function inventory(...handlers: ApiHandler[]) {
  const calls = stubApi([branchDirectory, ...handlers]);
  const { wrapper } = await renderScreen(GamesInventoryPage, '/games');

  return { wrapper, calls };
}

describe('game inventory screen', () => {
  it('renders the rows, stat tiles and range the API reports', async () => {
    const { wrapper } = await inventory(route('/games', { body: gameList() }));

    expect(wrapper.get('h1').text()).toBe('Game inventory');
    expect(wrapper.get('[data-testid="inventory-range"]').text()).toBe(
      'Showing 1–2 of 2',
    );

    const stats = wrapper.findAll('[class*="rounded-lg"][class*="px-6"]');
    expect(stats.map((tile) => tile.text())).toEqual([
      expect.stringContaining('2'),
      expect.stringContaining('46'),
      expect.stringContaining('12'),
    ]);

    const rows = wrapper.findAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('Exploding Kittens');
    expect(rows[0].text()).toContain('Card');
    expect(rows[0].text()).toContain('2–5');
    expect(rows[0].text()).toContain('Central Rama II');
    expect(rows[1].text()).toContain('All copies out');
    // Two branches and no single-branch name, so the count stands in.
    expect(rows[1].text()).toContain('2 branches');
  });

  it('shows a loading row until the first response arrives', async () => {
    stubApi([branchDirectory, route('/games', { body: gameList() })]);
    const { wrapper } = await renderScreen(GamesInventoryPage, '/games', {
      flush: false,
    });

    expect(wrapper.get('[data-testid="inventory-loading"]').text()).toBe(
      'Loading…',
    );

    await flushPromises();
    expect(wrapper.find('[data-testid="inventory-loading"]').exists()).toBe(
      false,
    );
    expect(wrapper.findAll('tbody tr')).toHaveLength(2);
  });

  it('offers a retry when the inventory cannot be loaded', async () => {
    let attempt = 0;
    const { wrapper } = await inventory((request) => {
      if (new URL(request.url).pathname !== '/api/v1/games') {
        return undefined;
      }
      attempt += 1;

      return attempt === 1
        ? { status: 500, body: { status: 500, title: 'Server Error' } }
        : { body: gameList([explodingKittens]) };
    });

    expect(wrapper.get('[data-testid="inventory-error"]').text()).toBe(
      'The inventory could not be loaded.',
    );

    await wrapper
      .get('[data-testid="inventory-error"] + button')
      .trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="inventory-error"]').exists()).toBe(
      false,
    );
    expect(wrapper.text()).toContain('Exploding Kittens');
  });

  it('distinguishes an empty catalogue from an empty filter result', async () => {
    const { wrapper } = await inventory(
      route('/games', { body: gameList([]) }),
    );

    expect(wrapper.get('[data-testid="inventory-empty"]').text()).toBe(
      'No games yet. Add the first one to start the inventory.',
    );

    await wrapper.get('#category-filter').setValue('card');
    await flushPromises();

    expect(wrapper.get('[data-testid="inventory-empty"]').text()).toBe(
      'No games match these filters.',
    );
  });

  it('sends the branch, category and status filters to the API', async () => {
    const { wrapper, calls } = await inventory(
      route('/games', { body: gameList() }),
    );

    expect(lastQuery(calls, '/games')?.get('page')).toBe('0');
    expect(lastQuery(calls, '/games')?.get('size')).toBe('20');

    await wrapper.get('#branch-filter').setValue(branches[1].id);
    await wrapper.get('#category-filter').setValue('strategy');
    await wrapper.get('#status-filter').setValue('allCopiesOut');
    await flushPromises();

    const query = lastQuery(calls, '/games');
    expect(query?.get('branchId')).toBe(branches[1].id);
    expect(query?.get('category')).toBe('strategy');
    expect(query?.get('status')).toBe('allCopiesOut');
  });

  it('debounces the search box into a single query parameter', async () => {
    const { wrapper, calls } = await inventory(
      route('/games', { body: gameList() }),
    );

    await wrapper.get('#game-search').setValue('kit');
    await flushPromises();
    expect(lastQuery(calls, '/games')?.get('search')).toBeNull();

    await new Promise((resolve) => setTimeout(resolve, 350));
    await flushPromises();

    expect(lastQuery(calls, '/games')?.get('search')).toBe('kit');
  });

  it('pages forward and back, resetting to the first page on a filter change', async () => {
    const { wrapper, calls } = await inventory(
      route('/games', {
        body: gameList([explodingKittens], {
          page: { number: 0, size: 20, totalElements: 24, totalPages: 2 },
        }),
      }),
    );

    const [previous, next] = wrapper
      .findAll('button')
      .filter((button) => ['Previous', 'Next'].includes(button.text()));
    expect(previous.attributes('disabled')).toBeDefined();

    await next.trigger('click');
    await flushPromises();
    expect(lastQuery(calls, '/games')?.get('page')).toBe('1');

    await wrapper.get('#category-filter').setValue('card');
    await flushPromises();
    expect(lastQuery(calls, '/games')?.get('page')).toBe('0');
  });

  it('lists every filter option, with a selectable "all" entry', async () => {
    const { wrapper } = await inventory(route('/games', { body: gameList() }));

    expect(optionsOf(wrapper.get('#branch-filter'))).toEqual([
      'All branches',
      'Big C Rama I',
      'Central Rama II',
      'Sukhumvit',
    ]);
    expect(optionsOf(wrapper.get('#category-filter'))).toEqual([
      'All categories',
      'Family',
      'Card',
      'Party',
      'Strategy',
    ]);
    expect(optionsOf(wrapper.get('#status-filter'))).toEqual([
      'All statuses',
      'Available',
      'All copies out',
      'Retired',
      'Not stocked',
    ]);
  });

  it('retires a game through the API and reloads the list', async () => {
    let listed = [explodingKittens, splendor];
    const { wrapper, calls } = await inventory(
      (request) => (request.method === 'DELETE' ? { status: 204 } : undefined),
      () => ({ body: gameList(listed) }),
    );

    listed = [splendor];
    const deleteButton = wrapper
      .findAll('tbody tr')[0]
      .findAll('button')
      .find((button) => button.text() === 'Delete');
    await deleteButton?.trigger('click');
    await flushPromises();
    await flushPromises();

    expect(
      calls.some(
        (call) =>
          call.method === 'DELETE' && call.url.endsWith(explodingKittens.id),
      ),
    ).toBe(true);
    expect(wrapper.text()).not.toContain('Exploding Kittens');
  });

  it('reports a failed retire without dropping the row', async () => {
    const { wrapper } = await inventory(
      (request) =>
        request.method === 'DELETE'
          ? { status: 500, body: { status: 500, title: 'Server Error' } }
          : undefined,
      route('/games', { body: gameList() }),
    );

    const deleteButton = wrapper
      .findAll('tbody tr')[0]
      .findAll('button')
      .find((button) => button.text() === 'Delete');
    await deleteButton?.trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="inventory-delete-error"]').text()).toBe(
      'That game could not be retired.',
    );
    expect(wrapper.text()).toContain('Exploding Kittens');
  });
});

describe('game details screen', () => {
  it('renders the header, facts, tags and per-branch stock from the API', async () => {
    stubApi([route('/games/' + ticketToRideId, { body: ticketToRide })]);
    const { wrapper } = await renderScreen(
      GameDetailsPage,
      '/games/' + ticketToRideId,
    );

    expect(wrapper.get('h1').text()).toBe('Ticket to Ride');
    expect(wrapper.text()).toContain('added 12 Jan 2025');
    expect(wrapper.text()).toContain('3 copies across 2 branches');
    expect(wrapper.get('[data-testid="game-description"]').text()).toBe(
      ticketToRide.description,
    );

    expect(wrapper.findAll('dt').map((term) => term.text())).toEqual([
      'Category',
      'Players',
      'Play time',
      'Difficulty',
      'Added',
      'Last played',
    ]);
    const values = wrapper.findAll('dd').map((value) => value.text());
    expect(values).toContain('60 minutes');
    // Nothing has recorded a play session yet, so the fact reads as unknown.
    expect(values).toContain('—');

    expect(wrapper.findAll('li').map((tag) => tag.text())).toEqual(
      ticketToRide.tags,
    );

    const rows = wrapper.findAll('tbody tr');
    expect(rows).toHaveLength(3);
    expect(rows[2].text()).toContain('Not stocked');
  });

  it('shows a not-found state for an id the API does not know', async () => {
    stubApi([]);
    const { wrapper } = await renderScreen(
      GameDetailsPage,
      '/games/' + ticketToRideId,
    );

    expect(wrapper.get('[data-testid="game-not-found"]').text()).toBe(
      'That game is no longer in the inventory.',
    );
  });

  it('separates a server failure from a missing game', async () => {
    stubApi([
      route('/games/' + ticketToRideId, {
        status: 503,
        body: { status: 503, title: 'Service Unavailable' },
      }),
    ]);
    const { wrapper } = await renderScreen(
      GameDetailsPage,
      '/games/' + ticketToRideId,
    );

    expect(wrapper.get('[data-testid="details-error"]').text()).toBe(
      'The inventory could not be loaded.',
    );
  });
});

describe('add game screen', () => {
  it('posts the typed values and returns to the inventory', async () => {
    const calls = stubApi([
      branchDirectory,
      (request) =>
        request.method === 'POST'
          ? { status: 201, body: ticketToRide }
          : undefined,
    ]);
    const { wrapper, router } = await renderScreen(AddGamePage, '/games/new');

    expect(wrapper.get('h1').text()).toBe('Add game');
    expect(
      wrapper.findAll('tbody tr').map((row) => row.get('th').text()),
    ).toEqual(['Big C Rama I', 'Central Rama II', 'Sukhumvit']);

    await wrapper.get('#game-title').setValue('Catan');
    await wrapper.get('#game-category').setValue('strategy');
    await wrapper.get('#game-min').setValue('3');
    await wrapper.get('#game-max').setValue('4');
    await wrapper.get('#game-play-time').setValue('90');
    await wrapper.get('#copies-1').setValue('2');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const posted = calls.find((call) => call.method === 'POST');
    expect(JSON.parse(posted?.body ?? '{}')).toMatchObject({
      title: 'Catan',
      category: 'strategy',
      minPlayers: 3,
      maxPlayers: 4,
      playTimeMinutes: 90,
      lifecycle: 'active',
      copies: [
        { branchId: branches[0].id, copies: 0 },
        { branchId: branches[1].id, copies: 2 },
        { branchId: branches[2].id, copies: 0 },
      ],
    });
    expect(router.currentRoute.value.path).toBe('/games');
    // The inventory reports the save; the new row is the rest of the story.
    expect(router.currentRoute.value.query.saved).toBe(ticketToRide.title);
  });

  it('leaves an unchosen category out rather than sending an empty value', async () => {
    const calls = stubApi([
      branchDirectory,
      (request) =>
        request.method === 'POST'
          ? {
              status: 422,
              body: {
                status: 422,
                errors: [{ field: 'category', message: 'required' }],
              },
            }
          : undefined,
    ]);
    const { wrapper } = await renderScreen(AddGamePage, '/games/new');

    await wrapper.get('#game-title').setValue('Catan');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const posted = JSON.parse(
      calls.find((call) => call.method === 'POST')?.body ?? '{}',
    );
    expect(posted).not.toHaveProperty('category');
    expect(wrapper.get('[data-testid="game-category-error"]').text()).toBe(
      'This is required.',
    );
  });

  it('marks the fields the API rejected and stays on the form', async () => {
    stubApi([
      branchDirectory,
      (request) =>
        request.method === 'POST'
          ? {
              status: 422,
              body: {
                status: 422,
                title: 'Unprocessable Content',
                errors: [
                  { field: 'title', message: 'required' },
                  { field: 'maxPlayers', message: 'belowMinimum' },
                ],
              },
            }
          : undefined,
    ]);
    const { wrapper, router } = await renderScreen(AddGamePage, '/games/new');

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe(
      'Check the highlighted fields and try again.',
    );
    expect(wrapper.get('[data-testid="game-title-error"]').text()).toBe(
      'This is required.',
    );
    expect(wrapper.get('[data-testid="game-max-error"]').text()).toBe(
      'Cannot be below the minimum players.',
    );
    expect(router.currentRoute.value.path).toBe('/games/new');
  });

  it('reports a failure that names no field', async () => {
    stubApi([
      branchDirectory,
      (request) =>
        request.method === 'POST'
          ? { status: 500, body: { status: 500, title: 'Server Error' } }
          : undefined,
    ]);
    const { wrapper } = await renderScreen(AddGamePage, '/games/new');

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe(
      'The game could not be saved.',
    );
  });
});

describe('edit game screen', () => {
  it('fills the form from the loaded game and puts the changes back', async () => {
    const calls = stubApi([
      branchDirectory,
      route('/games/' + ticketToRideId, { body: ticketToRide }),
      (request) =>
        request.method === 'PUT' ? { body: ticketToRide } : undefined,
    ]);
    const { wrapper, router } = await renderScreen(
      EditGamePage,
      '/games/' + ticketToRideId + '/edit',
    );

    expect(wrapper.get('h1').text()).toBe('Edit game — Ticket to Ride');
    expect(wrapper.get<HTMLInputElement>('#game-title').element.value).toBe(
      'Ticket to Ride',
    );
    expect(wrapper.get<HTMLSelectElement>('#game-category').element.value).toBe(
      'family',
    );
    expect(
      wrapper.get<HTMLTextAreaElement>('#game-description').element.value,
    ).toBe(ticketToRide.description);
    expect(wrapper.get<HTMLInputElement>('#game-play-time').element.value).toBe(
      '60',
    );
    // Copies come back per branch, in directory order.
    expect(
      wrapper
        .findAll('#copies-0, #copies-1, #copies-2')
        .map((input) => (input.element as HTMLInputElement).value),
    ).toEqual(['1', '2', '0']);

    await wrapper.get('#game-title').setValue('Ticket to Ride Europe');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const put = calls.find((call) => call.method === 'PUT');
    expect(put?.url).toContain('/games/' + ticketToRideId);
    expect(JSON.parse(put?.body ?? '{}')).toMatchObject({
      title: 'Ticket to Ride Europe',
      lifecycle: 'active',
      // Neither has a control in the design, so the edit must not drop them.
      tags: ticketToRide.tags,
      difficulty: 'Easy to teach',
    });
    expect(router.currentRoute.value.path).toBe('/games/' + ticketToRideId);
  });

  it('shows a not-found state instead of an empty form', async () => {
    stubApi([branchDirectory]);
    const { wrapper } = await renderScreen(
      EditGamePage,
      '/games/' + ticketToRideId + '/edit',
    );

    expect(wrapper.get('[data-testid="game-not-found"]').text()).toBe(
      'That game is no longer in the inventory.',
    );
  });

  it('marks the fields a rejected update names', async () => {
    stubApi([
      branchDirectory,
      route('/games/' + ticketToRideId, { body: ticketToRide }),
      (request) =>
        request.method === 'PUT'
          ? {
              status: 422,
              body: {
                status: 422,
                errors: [{ field: 'copies[0].copies', message: 'belowInUse' }],
              },
            }
          : undefined,
    ]);
    const { wrapper } = await renderScreen(
      EditGamePage,
      '/games/' + ticketToRideId + '/edit',
    );

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain(
      'Copies out on a session cannot be removed.',
    );
  });
});

function optionsOf(select: Omit<DOMWrapper<Element>, 'exists'>) {
  return select.findAll('option').map((option) => option.text());
}
