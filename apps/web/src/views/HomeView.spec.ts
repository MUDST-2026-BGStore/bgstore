import { afterEach, describe, expect, it, vi } from 'vitest';
import HomeView from './HomeView.vue';
import {
  renderScreen,
  route,
  stubApi,
  type ApiHandler,
} from '../test/api-stub';
import type { Branch } from '../generated/api/types.gen';

afterEach(() => {
  vi.unstubAllGlobals();
});

const signedOut = route('/me', { status: 401 });

/** Ordered by name, as the API answers; the fourth is past the featured three. */
const directory: Branch[] = [
  {
    id: '3f0d7d5a-9a2b-4a71-8f0e-000000000002',
    name: 'Big C Rama I',
    address: '999/9 ถ. พระรามที่ 1 แขวงปทุมวัน เขตปทุมวัน กรุงเทพฯ 10330',
    opensAt: '10:00',
    closesAt: '20:00',
    status: 'ACTIVE',
  },
  {
    id: '3f0d7d5a-9a2b-4a71-8f0e-000000000003',
    name: 'Big C Rama IX',
    address: '999/9 ถ. พระรามที่ 9 แขวงห้วยขวาง เขตห้วยขวาง กรุงเทพฯ 10310',
    opensAt: '10:00',
    closesAt: '21:00',
    status: 'ACTIVE',
  },
  {
    id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001',
    name: 'Central Rama II',
    address: '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
    opensAt: '09:00',
    closesAt: '19:00',
    status: 'ACTIVE',
  },
  {
    id: '3f0d7d5a-9a2b-4a71-8f0e-000000000005',
    name: 'Silom',
    address: null,
    opensAt: null,
    closesAt: null,
    status: 'ACTIVE',
  },
];

const branches = (items: Branch[] = directory) =>
  route('/branches', { body: { items } });

async function home(...handlers: ApiHandler[]) {
  stubApi(handlers);
  const { wrapper } = await renderScreen(HomeView, '/');

  return wrapper;
}

describe('home shell boundary', () => {
  it('leaves application navigation to the shared app shell', async () => {
    const wrapper = await home(signedOut, branches());

    expect(wrapper.find('nav[aria-label="Primary navigation"]').exists()).toBe(
      false,
    );
  });
});

describe('home branches', () => {
  it('features the first three branches with where they are and when they open', async () => {
    const wrapper = await home(signedOut, branches());

    const cards = wrapper.findAll('[data-testid="home-branch"]');
    expect(cards.map((card) => card.get('h3').text())).toEqual([
      'Big C Rama I',
      'Big C Rama IX',
      'Central Rama II',
    ]);
    expect(cards[2].get('address').text()).toBe(
      '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
    );
    expect(cards[2].text()).toContain('09:00–19:00');
    expect(cards[2].text()).toContain('Book at this branch');
  });

  it('reveals the rest of the directory in place, leaving out details a branch lacks', async () => {
    const wrapper = await home(signedOut, branches());
    const toggle = wrapper.get('button[aria-expanded]');
    expect(toggle.text()).toBe('View all');

    await toggle.trigger('click');

    const cards = wrapper.findAll('[data-testid="home-branch"]');
    expect(cards).toHaveLength(4);
    expect(cards[3].get('h3').text()).toBe('Silom');
    expect(cards[3].find('address').exists()).toBe(false);
    expect(cards[3].text()).not.toContain('–');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(toggle.text()).toBe('Show fewer');
  });

  it('has nothing to reveal when every branch is already featured', async () => {
    const wrapper = await home(signedOut, branches(directory.slice(0, 3)));

    expect(wrapper.find('button[aria-expanded]').exists()).toBe(false);
  });

  it('says so when there are no branches', async () => {
    const wrapper = await home(signedOut, branches([]));

    expect(wrapper.find('[data-testid="home-branches-empty"]').exists()).toBe(
      true,
    );
  });

  it('offers a retry when the directory fails to load', async () => {
    const wrapper = await home(
      signedOut,
      route('/branches', { status: 500, body: { status: 500 } }),
    );

    expect(wrapper.get('[data-testid="home-branches-error"]').text()).toBe(
      'We could not load the branches.',
    );
    expect(wrapper.text()).toContain('Try again');
  });
});

describe('home hero', () => {
  it('moves between slides with the arrows, wrapping at either end', async () => {
    const wrapper = await home(signedOut, branches());
    const current = () =>
      wrapper
        .findAll('[data-testid="hero-dot"]')
        .findIndex((dot) => dot.attributes('aria-current') === 'true');

    expect(current()).toBe(0);
    await wrapper.get('button[aria-label="Previous slide"]').trigger('click');
    expect(current()).toBe(2);
    await wrapper.get('button[aria-label="Next slide"]').trigger('click');
    expect(current()).toBe(0);
  });

  it('jumps straight to the slide a dot names', async () => {
    const wrapper = await home(signedOut, branches());

    await wrapper.get('button[aria-label="Slide 2 of 3"]').trigger('click');

    expect(
      wrapper
        .get('button[aria-label="Slide 2 of 3"]')
        .attributes('aria-current'),
    ).toBe('true');
  });
});
