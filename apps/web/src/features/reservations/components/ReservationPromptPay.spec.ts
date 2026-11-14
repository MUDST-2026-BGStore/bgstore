import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, ref } from 'vue';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { Reservation } from '@theparlor/api-client';
import { useLocale } from '../../../shared/lib/locale';
import ReservationPromptPay from './ReservationPromptPay.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));
const QR = 'data:image/svg+xml;base64,PHN2Zy8+';
function reservation(
  status: 'PENDING' | 'CAPTURED' | 'VOIDED' = 'PENDING',
): Reservation {
  return {
    id: 'reservation-1',
    branchId: 'branch-1',
    tableId: 'table-1',
    customer: { kind: 'REGISTERED', subject: 'customer-1' },
    source: 'CUSTOMER',
    status: status === 'VOIDED' ? 'CANCELLED' : 'CONFIRMED',
    startsAt: '2030-01-07T06:00:00Z',
    endsAt: '2030-01-07T08:00:00Z',
    partySize: 4,
    games: [],
    tableHourlyRate: { amountMinor: 5000 },
    createdAt: '2030-01-06T06:00:00Z',
    createdBySubject: 'customer-1',
    payment: {
      method: 'PROMPTPAY',
      status,
      amount: { amountMinor: 16300 },
      ...(status === 'PENDING'
        ? { promptPayQr: QR, expiresAt: '2030-01-06T06:15:00Z' }
        : {}),
    },
  };
}
let polled: Reservation;
let client: QueryClient;
beforeEach(() => {
  vi.useFakeTimers({
    toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
  });
  useLocale().setLocale('en-US');
  polled = reservation();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  request.mockReset().mockImplementation(() => Promise.resolve(polled));
});
afterEach(() => {
  client.clear();
  vi.useRealTimers();
});
async function showQr() {
  const harness = defineComponent({
    components: { ReservationPromptPay },
    setup: () => ({ current: ref(reservation()) }),
    template:
      '<ReservationPromptPay :reservation="current" @updated="current = $event" />',
  });
  const page = mount(harness, {
    global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
  });
  await flushPromises();
  return page;
}

it('reopens an unpaid QR and replaces it with payment confirmation', async () => {
  const page = await showQr();
  expect(page.get('img').attributes('src')).toBe(QR);
  expect(page.text()).toContain('฿163');
  expect(page.text()).toContain('Awaiting payment');
  expect(page.text()).toContain('refunds are unavailable');
  polled = reservation('CAPTURED');
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  expect(page.find('img').exists()).toBe(false);
  expect(page.text()).toContain('Paid');
  const count = request.mock.calls.length;
  await vi.advanceTimersByTimeAsync(9000);
  expect(request).toHaveBeenCalledTimes(count);
  page.unmount();
});

it('shows server cancellation and removes an expired QR', async () => {
  const page = await showQr();
  polled = reservation('VOIDED');
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  expect(page.find('img').exists()).toBe(false);
  expect(page.text()).toContain('This reservation was cancelled');
  page.unmount();
});

it('stops payment requests when the customer leaves', async () => {
  const page = await showQr();
  page.unmount();
  const count = request.mock.calls.length;
  await vi.advanceTimersByTimeAsync(9000);
  expect(request).toHaveBeenCalledTimes(count);
});
