import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { lastQuery, renderScreen, stubApi } from '../../test/api-stub';
import StaffReservationsListPage from './StaffReservationsListPage.vue';

type Row = Record<string, string | number>;

const row = (id: string, overrides: Partial<Row> = {}): Row => ({
  id,
  branchName: 'Silom',
  customerName: 'Jane Doe',
  phoneNumber: '088-888-8888',
  date: '2026-10-20',
  timeSlot: '14:00–16:00',
  partySize: 4,
  tableName: 'Table 1',
  status: 'Reserved',
  checkInTime: '-',
  totalPrice: 0,
  ...overrides,
});

/** Answers /staff/reservations with real filtering and pagination. */
const staffReservationsApi =
  (records: Row[]) =>
  (request: Request): { body: unknown } | undefined => {
    const url = new URL(request.url);
    if (
      request.method !== 'GET' ||
      url.pathname !== '/api/v1/staff/reservations'
    ) {
      return undefined;
    }
    const status = url.searchParams.get('status');
    const date = url.searchParams.get('date');
    const page = Number(url.searchParams.get('page') ?? 1);
    const pageSize = Number(url.searchParams.get('pageSize') ?? 20);
    const filtered = records.filter(
      (record) =>
        (!status || record.status === status) &&
        (!date || record.date === date),
    );
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const safePage = Math.min(Math.max(1, page), totalPages);
    const start = (safePage - 1) * pageSize;
    return {
      body: {
        items: filtered.slice(start, start + pageSize),
        total: filtered.length,
        page: safePage,
        pageSize,
        totalPages,
      },
    };
  };

afterEach(() => vi.unstubAllGlobals());

describe('Staff Reservations List', () => {
  it('renders the branch reservations table', async () => {
    stubApi([
      staffReservationsApi([
        row('res-1'),
        row('res-2', {
          status: 'Completed',
          date: '2026-10-22',
          tableName: 'Table 5',
        }),
      ]),
    ]);
    const { wrapper } = await renderScreen(
      StaffReservationsListPage,
      '/staff/reservations',
    );

    expect(wrapper.get('h1').text()).toBe('Reservations');
    const rows = wrapper.findAll('[data-testid="staff-reservation-row"]');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('Jane Doe');
    expect(rows[0].text()).toContain('088-888-8888');
    expect(rows[0].text()).toContain('Table 1');
    expect(rows[0].text()).toContain('Silom');
    expect(rows[1].text()).toContain('Completed');
    expect(
      wrapper.find('[data-testid="staff-reservations-empty"]').exists(),
    ).toBe(false);
  });

  it('shows the empty state when no reservations match', async () => {
    stubApi([staffReservationsApi([])]);
    const { wrapper } = await renderScreen(
      StaffReservationsListPage,
      '/staff/reservations',
    );

    expect(
      wrapper.find('[data-testid="staff-reservations-empty"]').text(),
    ).toContain('No reservations match these filters');
  });

  it('sends the date and status filters and page size to the API', async () => {
    const calls = stubApi([
      staffReservationsApi([
        row('res-1'),
        row('res-2', { status: 'Cancelled', date: '2026-10-21' }),
      ]),
    ]);
    const { wrapper } = await renderScreen(
      StaffReservationsListPage,
      '/staff/reservations',
    );

    expect(lastQuery(calls, '/staff/reservations')!.get('pageSize')).toBe('20');

    await wrapper.find('#staff-reservation-date').setValue('2026-10-21');
    await flushPromises();
    await wrapper.find('#staff-reservation-status').setValue('Cancelled');
    await flushPromises();

    const params = lastQuery(calls, '/staff/reservations')!;
    expect(params.get('date')).toBe('2026-10-21');
    expect(params.get('status')).toBe('Cancelled');
    expect(params.get('page')).toBe('1');
    expect(
      wrapper.findAll('[data-testid="staff-reservation-row"]'),
    ).toHaveLength(1);
    expect(
      wrapper.get('[data-testid="staff-reservation-row"]').text(),
    ).toContain('Cancelled');
  });

  it('requests the chosen page from the pagination control', async () => {
    const calls = stubApi([
      staffReservationsApi(
        Array.from({ length: 22 }, (_, index) => row(`res-${index + 1}`)),
      ),
    ]);
    const { wrapper } = await renderScreen(
      StaffReservationsListPage,
      '/staff/reservations',
    );

    expect(
      wrapper.findAll('[data-testid="staff-reservation-row"]'),
    ).toHaveLength(20);
    expect(
      wrapper.get('[data-testid="staff-reservations-pagination"]').text(),
    ).toContain('Showing 1–20 of 22');

    const pageTwo = wrapper
      .findAll('button')
      .find((button) => button.text() === '2');
    expect(pageTwo).toBeDefined();
    await pageTwo!.trigger('click');
    await flushPromises();

    expect(lastQuery(calls, '/staff/reservations')!.get('page')).toBe('2');
    expect(
      wrapper.findAll('[data-testid="staff-reservation-row"]'),
    ).toHaveLength(2);
  });
});
