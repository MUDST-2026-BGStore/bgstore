import { mount, flushPromises } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLocale } from '../../../shared/lib/locale';
import { useCustomerReservationsStore } from '../data/reservations-store';
import TableBillModal from './TableBillModal.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const BRANCH_ID = '00000000-0000-4000-8000-000000000001';
const TABLE_ID = '00000000-0000-4000-8000-000000000101';
const RES_ID = '00000000-0000-4000-8000-000000000201';
const RENTAL_ID = '00000000-0000-4000-8000-000000000301';

// Booked 13:00-15:00 in Bangkok, checked in at 13:10.
const STARTS = '2026-05-01T06:00:00Z';
const CHECKED_IN = '2026-05-01T13:10:00+07:00';
const ENDS = '2026-05-01T15:00:00+07:00';
const bkk = (time: string) =>
  new Date(`2026-05-01T${time.length === 5 ? `${time}:00` : time}+07:00`);

type RentalOverrides = Record<string, unknown>;

describe('table bill timing for an active session', () => {
  let client: QueryClient;
  let serverSkewMs = 0; // how far the device clock runs from the server's
  let rentalOverrides: () => RentalOverrides = () => ({});
  let responseDelayMs = 0;

  const serverNow = () => new Date(Date.now() + serverSkewMs).toISOString();
  const rentalResponse = () => ({
    id: RENTAL_ID,
    branchId: BRANCH_ID,
    reservationId: RES_ID,
    tableId: TABLE_ID,
    customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
    status: 'ACTIVE',
    startedAt: CHECKED_IN,
    plannedEndsAt: ENDS,
    serverNow: serverNow(),
    checkedInBySubject: 'staff-1',
    charges: [
      { kind: 'TABLE', quantity: 1, unitHourlyRate: { amountMinor: 30000 } },
    ],
    ...rentalOverrides(),
  });

  beforeEach(() => {
    localStorage.clear();
    useLocale().setLocale('en-US');
    useCustomerReservationsStore().resetToDefault();
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    serverSkewMs = 0;
    rentalOverrides = () => ({});
    responseDelayMs = 0;
    request.mockReset();
    request.mockImplementation((config: { url: string; method?: string }) => {
      if (config.url.endsWith(`/rentals/${RENTAL_ID}`)) {
        const body = rentalResponse();
        if (!responseDelayMs) return Promise.resolve(body);
        return new Promise((resolve) =>
          setTimeout(() => resolve(body), responseDelayMs),
        );
      }
      return Promise.resolve({});
    });
    vi.useFakeTimers();
  });

  afterEach(() => {
    client.clear();
    vi.useRealTimers();
  });

  async function open() {
    const wrapper = mount(TableBillModal, {
      props: {
        isStaff: true,
        reservation: {
          id: RES_ID,
          branchId: BRANCH_ID,
          branchName: 'Siam',
          tableId: TABLE_ID,
          tableLabel: 'Table 1',
          startsAt: STARTS,
          endsAt: ENDS,
          checkedInAt: CHECKED_IN,
          rentalId: RENTAL_ID,
          partySize: 4,
          status: 'CHECKED_IN',
          customerName: 'Khun Ananda',
          createdAt: STARTS,
          hourlyRate: 300,
          inPlayOrders: [],
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });
    await flushPromises();
    return wrapper;
  }
  const text = (w: Awaited<ReturnType<typeof open>>, id: string) =>
    w.get(`[data-testid="${id}"]`).text();
  async function advance(ms: number) {
    await vi.advanceTimersByTimeAsync(ms);
    await flushPromises();
  }

  it('shows check-in, scheduled end, elapsed, remaining, rate and estimate labels', async () => {
    vi.setSystemTime(bkk('14:10'));
    const w = await open();

    expect(text(w, 'checked-in-at')).toBe('13:10');
    expect(text(w, 'scheduled-end')).toBe('15:00');
    expect(text(w, 'elapsed')).toBe('1:00:00');
    expect(text(w, 'remaining')).toBe('50:00');
    expect(text(w, 'rate')).toContain('฿300/hr');
    expect(text(w, 'rate')).toContain('1 billable hr');
    expect(text(w, 'play-fee-label')).toBe('Estimated play fee');
    expect(w.text()).toContain('Estimated total (Play + Food):');
    expect(w.find('[data-testid="expiry-warning"]').exists()).toBe(false);
    w.unmount();
  });

  it('updates elapsed, remaining and the fee without a refresh', async () => {
    vi.setSystemTime(bkk('14:09:50'));
    const w = await open();
    expect(text(w, 'elapsed')).toBe('59:50');
    expect(w.get('.section-cost').text()).toBe('฿300');

    await advance(11_000);
    expect(text(w, 'elapsed')).toBe('1:00:01');
    expect(text(w, 'remaining')).toBe('49:59');
    // The second started hour is billed from the first second over.
    expect(text(w, 'rate')).toContain('2 billable hr');
    expect(w.get('.section-cost').text()).toBe('฿600');
    w.unmount();
  });

  it('keeps the 15:00 end for a 13:10 check-in: 110 minutes played, no overtime', async () => {
    vi.setSystemTime(bkk('14:59:00'));
    const w = await open();
    await advance(60_000);
    expect(text(w, 'scheduled-end')).toBe('15:00');
    expect(text(w, 'elapsed')).toBe('1:50:00');
    expect(text(w, 'remaining')).toBe('0:00');
    expect(text(w, 'rate')).toContain('2 billable hr');
    const fee = w.get('.section-cost').text();
    expect(fee).toBe('฿600');

    // An hour past the end: the timer and estimate stay put; the session is not
    // marked completed or finalized by the browser.
    await advance(60 * 60_000);
    expect(text(w, 'elapsed')).toBe('1:50:00');
    expect(text(w, 'remaining')).toBe('0:00');
    expect(w.get('.section-cost').text()).toBe(fee);
    expect(text(w, 'play-fee-label')).toBe('Estimated play fee');
    expect(w.find('[data-testid="ended-notice"]').exists()).toBe(true);
    expect(w.text()).not.toContain('Final play fee');
    w.unmount();
  });

  it('warns before the scheduled end, not earlier', async () => {
    vi.setSystemTime(bkk('14:48'));
    const w = await open();
    expect(w.find('[data-testid="expiry-warning"]').exists()).toBe(false);
    await advance(2 * 60_000);
    expect(w.get('[data-testid="expiry-warning"]').attributes('role')).toBe(
      'alert',
    );
    expect(text(w, 'remaining')).toBe('10:00');
    w.unmount();
  });

  it('switches to the backend final amount when the server completes the rental', async () => {
    vi.setSystemTime(bkk('14:10'));
    const w = await open();
    expect(text(w, 'play-fee-label')).toBe('Estimated play fee');

    // Checked out early at 14:20; the server bills the one started hour... of 70 min = 2h.
    rentalOverrides = () => ({
      status: 'COMPLETED',
      endedAt: '2026-05-01T14:20:00+07:00',
      checkedOutBySubject: 'staff-1',
      billableHours: 2,
      total: { amountMinor: 61000 },
      charges: [
        {
          kind: 'TABLE',
          quantity: 1,
          unitHourlyRate: { amountMinor: 30500 },
          billableHours: 2,
          lineTotal: { amountMinor: 61000 },
        },
      ],
    });
    await advance(30_000);

    expect(text(w, 'play-fee-label')).toBe('Final play fee');
    expect(w.get('.section-cost').text()).toBe('฿610');
    expect(text(w, 'remaining')).toBe('0:00');
    expect(text(w, 'elapsed')).toBe('1:10:00');
    expect(w.text()).not.toContain('Estimated total');
    w.unmount();
  });

  it('is unaffected by a wrong device clock', async () => {
    // Server says 14:10; the device clock is three hours fast.
    serverSkewMs = bkk('14:10').getTime() - bkk('17:10').getTime();
    vi.setSystemTime(bkk('17:10'));
    const w = await open();
    expect(text(w, 'elapsed')).toBe('1:00:00');
    expect(text(w, 'remaining')).toBe('50:00');

    await advance(5_000);
    expect(text(w, 'remaining')).toBe('49:55');
    w.unmount();
  });

  it('shows the same times after a reload', async () => {
    vi.setSystemTime(bkk('14:30'));
    const first = await open();
    const before = [text(first, 'elapsed'), text(first, 'remaining')];
    first.unmount();

    const second = await open();
    expect([text(second, 'elapsed'), text(second, 'remaining')]).toEqual(
      before,
    );
    second.unmount();
  });

  it('does not run ahead of the server while the response is delayed', async () => {
    vi.setSystemTime(bkk('14:10'));
    responseDelayMs = 4_000;
    const w = mount(TableBillModal, {
      props: {
        isStaff: true,
        reservation: {
          id: RES_ID,
          branchId: BRANCH_ID,
          branchName: 'Siam',
          tableId: TABLE_ID,
          tableLabel: 'Table 1',
          startsAt: STARTS,
          endsAt: ENDS,
          checkedInAt: CHECKED_IN,
          rentalId: RENTAL_ID,
          partySize: 4,
          status: 'CHECKED_IN',
          customerName: 'Khun Ananda',
          createdAt: STARTS,
          hourlyRate: 300,
          inPlayOrders: [],
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });
    await advance(5_000);
    // True server time is 14:10:05; the delayed stamp was taken at 14:10:00.
    expect(text(w, 'elapsed')).toMatch(/^1:00:0\d$/);
    expect(Number(text(w, 'remaining').split(':')[1])).toBeGreaterThanOrEqual(
      55 - 5,
    );
    w.unmount();
  });

  it('labels the estimate in Thai', async () => {
    useLocale().setLocale('th-TH');
    vi.setSystemTime(bkk('14:10'));
    const w = await open();
    expect(text(w, 'play-fee-label')).toBe('ค่าเล่นโดยประมาณ');
    w.unmount();
  });
});
