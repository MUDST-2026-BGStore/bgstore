import { describe, expect, it } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { routes } from '../../router';

/**
 * `router.ts` builds a history-mode router, which jsdom cannot drive, so the
 * route records are reused against a memory history.
 */
function testRouter() {
  return createRouter({ history: createMemoryHistory(), routes: [...routes] });
}

describe('game routes', () => {
  it('sends unmatched paths to the home screen rather than a blank view', async () => {
    const router = testRouter();

    await router.push('/definitely-not-a-route');

    expect(router.currentRoute.value.name).toBe('home');
  });

  it('resolves the game screens by id without consulting a local record', async () => {
    const router = testRouter();

    // Whether the id exists is the API's answer, so an arbitrary id still
    // resolves and the screen renders its own not-found state.
    await router.push('/games/not-in-any-fixture');
    expect(router.currentRoute.value.name).toBe('games-detail');

    await router.push('/games/not-in-any-fixture/edit');
    expect(router.currentRoute.value.name).toBe('games-edit');

    await router.push('/games/new');
    expect(router.currentRoute.value.name).toBe('games-new');
  });
});
