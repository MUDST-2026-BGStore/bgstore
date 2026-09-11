import { flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import FloorOverviewPage from './FloorOverviewPage.vue';
import {
  lastQuery,
  renderScreen,
  route,
  stubApi,
  type ApiHandler,
} from '../../test/api-stub';
import type {
  FloorOverviewResponse,
  FloorTableResponse,
} from '../../generated/api/types.gen';

function table(
  id: number,
  overrides: Partial<FloorTableResponse> = {},
): FloorTableResponse {
  return {
    id,
    name: `Table ${id}`,
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    reservedSlots: [],
    ...overrides,
  };
}

const designPage: FloorOverviewResponse = {
  counts: { available: 100, occupied: 10, reserved: 10 },
  items: [
    table(1),
    table(2, {
      capacity: 6,
      shape: 'Square',
      status: 'Occupied',
      reservedSlots: [
        { startsAt: '2026-09-08T05:00:00Z', endsAt: '2026-09-08T06:00:00Z' },
      ],
    }),
    table(3, {
      capacity: 6,
      status: 'Reserved',
      reservedSlots: [
        { startsAt: '2026-09-08T07:00:00Z', endsAt: '2026-09-08T09:00:00Z' },
        { startsAt: '2026-09-09T07:00:00Z', endsAt: '2026-09-09T09:00:00Z' },
        { startsAt: '2026-09-10T07:00:00Z', endsAt: '2026-09-10T09:00:00Z' },
      ],
    }),
    table(4, { capacity: 2 }),
    table(5),
  ],
  total: 42,
  page: 1,
  pageSize: 5,
  totalPages: 9,
};

function floorApi(answer: FloorOverviewResponse = designPage): ApiHandler {
  return route('/floor-overview', { body: answer });
}

function rowTexts(
  wrapper: Awaited<ReturnType<typeof renderScreen>>['wrapper'],
) {
  return wrapper
    .findAll('tbody tr')
    .map((row) =>
      row.findAll('td:not([aria-hidden="true"])').map((cell) => cell.text()),
    );
}

describe('FloorOverviewPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the floor counts and the first page of tables as the design does', async () => {
    const calls = stubApi([floorApi()]);

    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    expect(wrapper.get('h1').text()).toBe('Floor overview');
    expect(
      wrapper.findAll('[data-testid="floor-stat"]').map((card) => card.text()),
    ).toEqual(['Available100', 'Occupied10', 'Reserved10']);
    expect(rowTexts(wrapper)).toEqual([
      ['1', 'Table 1', '4 seats', 'Available', 'Round table', '—'],
      [
        '2',
        'Table 2',
        '6 seats',
        'Occupied',
        'Square table',
        '08/09/2569 (12:00–13:00)',
      ],
      [
        '3',
        'Table 3',
        '6 seats',
        'Reserved',
        'Round table',
        '08/09/2569 (14:00–16:00) +2 more',
      ],
      ['4', 'Table 4', '2 seats', 'Available', 'Round table', '—'],
      ['5', 'Table 5', '4 seats', 'Available', 'Round table', '—'],
    ]);
    expect(wrapper.text()).toContain('Showing 1–5 of 42');
    expect(
      wrapper.find('[aria-current="page"][aria-label="Page 1"]').exists(),
    ).toBe(true);
    const query = lastQuery(calls, '/floor-overview');
    expect(query?.get('page')).toBe('1');
    expect(query?.get('pageSize')).toBe('5');
    expect(query?.has('status')).toBe(false);
    expect(query?.has('search')).toBe(false);
  });

  it('colours each status badge by its meaning', async () => {
    stubApi([floorApi()]);

    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    const badges = wrapper.findAll('tbody tr td:nth-child(4) span');
    expect(badges[0].classes()).toContain('bg-success-bg');
    expect(badges[1].classes()).toContain('bg-warning-bg');
    expect(badges[2].classes()).toContain('bg-info-bg');
  });

  it('asks the API for the tables that match the search and status', async () => {
    const calls = stubApi([floorApi()]);
    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    await wrapper.get('#floor-search').setValue('  12 ');
    await wrapper.get('#floor-status').setValue('Reserved');
    await flushPromises();

    const query = lastQuery(calls, '/floor-overview');
    expect(query?.get('search')).toBe('12');
    expect(query?.get('status')).toBe('Reserved');
    expect(query?.get('page')).toBe('1');
  });

  it('pages through the tables and starts over when a filter changes', async () => {
    const calls = stubApi([floorApi()]);
    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    await wrapper.get('[aria-label="Page 2"]').trigger('click');
    await flushPromises();
    expect(lastQuery(calls, '/floor-overview')?.get('page')).toBe('2');

    await wrapper.get('[aria-label="Next page"]').trigger('click');
    await flushPromises();
    expect(lastQuery(calls, '/floor-overview')?.get('page')).toBe('3');

    await wrapper.get('#floor-status').setValue('Occupied');
    await flushPromises();
    expect(lastQuery(calls, '/floor-overview')?.get('page')).toBe('1');
  });

  it('cannot page before the first page', async () => {
    stubApi([floorApi()]);

    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    expect(
      wrapper.get('[aria-label="Previous page"]').attributes('disabled'),
    ).toBeDefined();
  });

  it('says so when no table matches', async () => {
    stubApi([
      floorApi({
        ...designPage,
        items: [],
        total: 0,
        totalPages: 1,
      }),
    ]);

    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    expect(wrapper.text()).toContain('No tables match your filters.');
    expect(wrapper.find('table').exists()).toBe(false);
    expect(wrapper.find('[aria-label="Pagination"]').exists()).toBe(false);
  });

  it('offers a retry when the floor cannot be loaded', async () => {
    const calls = stubApi([
      route('/floor-overview', { status: 500, body: { status: 500 } }),
    ]);
    const { wrapper } = await renderScreen(FloorOverviewPage, '/');

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'We could not load the floor overview.',
    );

    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(
      calls.filter((call) => call.url.includes('/floor-overview')),
    ).toHaveLength(2);
  });
});
