import { defineComponent } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Reservation } from '@theparlor/api-client';
import { useLocale } from '../../../shared/lib/locale';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { useWorkspaceReservations } from './useWorkspaceReservations';
import TableBillModal from '../components/TableBillModal.vue';
import InPlayOrderModal from '../components/InPlayOrderModal.vue';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const BRANCH_ID = '00000000-0000-4000-8000-000000000001';
const TABLE_ID = '00000000-0000-4000-8000-000000000101';
const RES_ID = '00000000-0000-4000-8000-000000000201';
const RENTAL_ID = '00000000-0000-4000-8000-000000000301';

describe('reservations, rental checkout, and branch menu integration', () => {
  let client: QueryClient;

  beforeEach(() => {
    localStorage.clear();
    useLocale().setLocale('en-US');
    useCustomerReservationsStore().resetToDefault();
    client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    request.mockReset();
  });

  afterEach(() => {
    client.clear();
    vi.useRealTimers();
  });

  it.each([
    {
      name: 'an occupied table',
      error: {
        response: {
          status: 409,
          data: {
            message: 'The reserved table is still occupied by an earlier visit',
          },
        },
      },
      expected:
        'Check-in conflict: The reserved table is still occupied by an earlier visit',
    },
    {
      name: 'an overdue game copy',
      error: {
        response: {
          status: 409,
          data: {
            message: 'A reserved copy is still out with an earlier visit',
          },
        },
      },
      expected:
        'Check-in conflict: A reserved copy is still out with an earlier visit',
    },
    {
      name: 'a booking outside its check-in window',
      error: {
        response: {
          status: 409,
          data: {
            message: 'Check-in is only possible during the booked window',
          },
        },
      },
      expected:
        'Check-in conflict: Check-in is only possible during the booked window',
    },
    {
      name: 'a problem detail response',
      error: {
        response: {
          status: 409,
          data: { detail: 'The reserved table is out of service' },
        },
      },
      expected: 'Check-in conflict: The reserved table is out of service',
    },
    {
      name: 'a conflict with no explanation',
      error: { response: { status: 409, data: { message: ' ', detail: 123 } } },
      expected:
        'Check-in conflict. Refresh the reservation and check table and game availability.',
    },
    {
      name: 'a network failure',
      error: new Error('Network Error'),
      expected: 'Check-in failed. Refresh and retry.',
    },
    {
      name: 'a server failure',
      error: {
        response: {
          status: 500,
          data: { message: 'Internal database details' },
        },
      },
      expected: 'Check-in failed. Refresh and retry.',
    },
  ])(
    'explains $name without changing the reservation and clears the error on retry',
    async ({ error, expected }) => {
      const startsAt = new Date(Date.now() - 30 * 60000).toISOString();
      const endsAt = new Date(Date.now() + 60 * 60000).toISOString();
      const reservation: Reservation = {
        id: RES_ID,
        branchId: BRANCH_ID,
        tableId: TABLE_ID,
        customer: { kind: 'GUEST', displayName: 'Waiting party' },
        source: 'STAFF_BOOKING',
        status: 'CONFIRMED',
        startsAt,
        endsAt,
        partySize: 2,
        games: [],
        tableHourlyRate: { amountMinor: 20000 },
        createdAt: startsAt,
        createdBySubject: 'manager-1',
      };
      let failure: unknown = error;
      request.mockImplementation((config: { url: string }) => {
        if (config.url === '/api/v1/branches')
          return Promise.resolve({ items: [{ id: BRANCH_ID, name: 'Siam' }] });
        if (config.url === `/api/v1/branches/${BRANCH_ID}/reservations`)
          return Promise.resolve({ items: [{ ...reservation }] });
        if (config.url.endsWith('/check-in')) {
          if (failure) return Promise.reject(failure);
          reservation.status = 'CHECKED_IN';
          reservation.rentalId = RENTAL_ID;
          return Promise.resolve({ reservation: { ...reservation } });
        }
        return Promise.resolve({ items: [] });
      });

      let workspace!: ReturnType<typeof useWorkspaceReservations>;
      const wrapper = mount(
        defineComponent({
          setup() {
            workspace = useWorkspaceReservations();
            return () => null;
          },
        }),
        { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } },
      );
      try {
        await flushPromises();
        await flushPromises();
        await workspace.handleCheckIn(reservation);
        expect(workspace.actionError.value).toBe(expected);
        expect(workspace.actionInProgress.value).toBeNull();
        expect(
          workspace.allReservations.value.find((item) => item.id === RES_ID),
        ).toMatchObject({ status: 'CONFIRMED', endsAt });
        expect(reservation.rentalId).toBeUndefined();

        failure = undefined;
        await workspace.handleCheckIn(reservation);
        await flushPromises();
        expect(workspace.actionError.value).toBe('');
        expect(workspace.actionInProgress.value).toBeNull();
        expect(
          workspace.allReservations.value.find((item) => item.id === RES_ID),
        ).toMatchObject({ status: 'CHECKED_IN', rentalId: RENTAL_ID, endsAt });
      } finally {
        wrapper.unmount();
      }
    },
  );

  it('loads live tables, reservations, and rentals and prepares the bill with rentalId and preOrder', async () => {
    const startedAt = new Date(Date.now() - 60 * 60000).toISOString();
    const endsAt = new Date(Date.now() + 60 * 60000).toISOString();

    request.mockImplementation((config: { url: string; method?: string }) => {
      if (config.url === '/api/v1/branches') {
        return Promise.resolve({
          items: [
            {
              id: BRANCH_ID,
              name: 'Siam',
              address: 'Pathum Wan',
              openingHours: [],
              active: true,
              fulfillsDelivery: true,
            },
          ],
        });
      }
      if (config.url === `/api/v1/branches/${BRANCH_ID}/tables`) {
        return Promise.resolve({
          items: [
            {
              id: TABLE_ID,
              branchId: BRANCH_ID,
              label: 'The Emerald Salon (Table 1)',
              capacity: 4,
              hourlyRate: { amountMinor: 32000 },
              operationalStatus: 'IN_SERVICE',
              state: 'OCCUPIED',
            },
          ],
        });
      }
      if (config.url === `/api/v1/branches/${BRANCH_ID}/reservations`) {
        return Promise.resolve({
          items: [
            {
              id: RES_ID,
              branchId: BRANCH_ID,
              tableId: TABLE_ID,
              customer: {
                kind: 'GUEST',
                displayName: 'Khun Ananda',
                phone: '0811112222',
              },
              source: 'ONLINE',
              status: 'CHECKED_IN',
              startsAt: startedAt,
              endsAt,
              partySize: 4,
              games: [],
              tableHourlyRate: { amountMinor: 32000 },
              preOrder: {
                items: [
                  {
                    menuItemId: 'menu-matcha',
                    name: 'Iced Matcha Latte',
                    quantity: 2,
                    unitPrice: { amountMinor: 9000 },
                    lineTotal: { amountMinor: 18000 },
                  },
                ],
                subtotal: { amountMinor: 18000 },
                discount: { amountMinor: 1800 },
                total: { amountMinor: 16200 },
                payment: {
                  method: 'CARD',
                  status: 'CAPTURED',
                  amount: { amountMinor: 16200 },
                },
              },
              createdAt: startedAt,
              createdBySubject: 'cust-1',
              checkedInAt: startedAt,
              rentalId: RENTAL_ID,
            },
          ],
        });
      }
      if (config.url === `/api/v1/branches/${BRANCH_ID}/rentals`) {
        return Promise.resolve({
          items: [
            {
              id: RENTAL_ID,
              branchId: BRANCH_ID,
              reservationId: RES_ID,
              tableId: TABLE_ID,
              customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
              status: 'ACTIVE',
              startedAt,
              plannedEndsAt: endsAt,
              checkedInBySubject: 'staff-1',
              charges: [
                {
                  kind: 'TABLE',
                  quantity: 1,
                  unitHourlyRate: { amountMinor: 32000 },
                },
              ],
            },
          ],
        });
      }
      return Promise.resolve({ items: [] });
    });

    let workspace!: ReturnType<typeof useWorkspaceReservations>;
    const Harness = defineComponent({
      setup() {
        workspace = useWorkspaceReservations();
        return () => null;
      },
    });

    const wrapper = mount(Harness, {
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });
    await flushPromises();
    await flushPromises();

    expect(workspace.branchFloorTables).toHaveLength(1);
    expect(workspace.branchFloorTables[0]).toMatchObject({
      id: TABLE_ID,
      number: 1,
      seats: 4,
      rate: 320,
      label: 'The Emerald Salon (Table 1)',
    });

    expect(workspace.allReservations.value).toHaveLength(1);
    const liveRes = workspace.allReservations.value[0];
    expect(liveRes).toMatchObject({
      id: RES_ID,
      live: true,
      rentalId: RENTAL_ID,
      hourlyRate: 320,
      clientType: 'RESERVED',
    });
    // Sample visits stay off a floor whose tables come from the server.
    expect(
      workspace.allReservations.value.some((r) => r.id === 'res-thm-live'),
    ).toBe(false);
    // One started hour at the table's server rate.
    expect(workspace.calculateCurrentPlayFee(liveRes)).toBe(320);

    workspace.openCheckBill(liveRes);
    expect(workspace.staffBillReservation.value).toMatchObject({
      id: RES_ID,
      rentalId: RENTAL_ID,
      tableLabel: 'The Emerald Salon (Table 1)',
      hourlyRate: 320,
      preOrder: {
        subtotal: 180,
        discountAmount: 18,
        discountedTotal: 162,
        paid: true,
      },
    });
    // The in-play tab has no backend, so a server bill carries none of it.
    expect(workspace.staffBillReservation.value.inPlayOrders).toEqual([]);

    wrapper.unmount();
  });

  it('fetches live rental charges and calls checkOutRental when staff settles the bill', async () => {
    vi.useFakeTimers();
    const startedAt = new Date(Date.now() - 60 * 60000).toISOString();
    const endedAt = new Date().toISOString();

    request.mockImplementation((config: { url: string; method?: string }) => {
      if (
        config.url === `/api/v1/branches/${BRANCH_ID}/rentals/${RENTAL_ID}` &&
        config.method === 'GET'
      ) {
        return Promise.resolve({
          id: RENTAL_ID,
          branchId: BRANCH_ID,
          reservationId: RES_ID,
          tableId: TABLE_ID,
          customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
          status: 'ACTIVE',
          startedAt,
          plannedEndsAt: endedAt,
          checkedInBySubject: 'staff-1',
          charges: [
            {
              kind: 'TABLE',
              quantity: 1,
              unitHourlyRate: { amountMinor: 30000 },
            },
            {
              kind: 'GAME',
              gameId: 'game-azul',
              quantity: 1,
              unitHourlyRate: { amountMinor: 4000 },
            },
          ],
        });
      }
      if (
        config.url ===
          `/api/v1/branches/${BRANCH_ID}/rentals/${RENTAL_ID}/check-out` &&
        config.method === 'POST'
      ) {
        return Promise.resolve({
          reservation: {
            id: RES_ID,
            branchId: BRANCH_ID,
            tableId: TABLE_ID,
            customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
            source: 'ONLINE',
            status: 'COMPLETED',
            startsAt: startedAt,
            endsAt: endedAt,
            partySize: 4,
            games: [],
            tableHourlyRate: { amountMinor: 30000 },
            createdAt: startedAt,
            createdBySubject: 'cust-1',
            checkedInAt: startedAt,
            completedAt: endedAt,
            rentalId: RENTAL_ID,
          },
          rental: {
            id: RENTAL_ID,
            branchId: BRANCH_ID,
            reservationId: RES_ID,
            tableId: TABLE_ID,
            customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
            status: 'COMPLETED',
            startedAt,
            plannedEndsAt: endedAt,
            endedAt,
            checkedInBySubject: 'staff-1',
            checkedOutBySubject: 'staff-1',
            billableHours: 1,
            charges: [
              {
                kind: 'TABLE',
                quantity: 1,
                unitHourlyRate: { amountMinor: 30000 },
                billableHours: 1,
                lineTotal: { amountMinor: 30000 },
              },
              {
                kind: 'GAME',
                gameId: 'game-azul',
                quantity: 1,
                unitHourlyRate: { amountMinor: 4000 },
                billableHours: 1,
                lineTotal: { amountMinor: 4000 },
              },
            ],
            total: { amountMinor: 34000 },
          },
        });
      }
      return Promise.resolve({});
    });

    const wrapper = mount(TableBillModal, {
      props: {
        isStaff: true,
        reservation: {
          id: RES_ID,
          branchId: BRANCH_ID,
          branchName: 'Siam',
          tableId: TABLE_ID,
          tableLabel: 'Table 1',
          startsAt: startedAt,
          endsAt: endedAt,
          checkedInAt: startedAt,
          rentalId: RENTAL_ID,
          partySize: 4,
          status: 'CHECKED_IN',
          customerName: 'Khun Ananda',
          createdAt: startedAt,
          hourlyRate: 200,
          inPlayOrders: [],
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });

    await flushPromises();
    expect(wrapper.find('.section-cost').text()).toBe('฿340');

    await wrapper.find('.settle-btn').trigger('click');
    await flushPromises();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: `/api/v1/branches/${BRANCH_ID}/rentals/${RENTAL_ID}/check-out`,
        method: 'POST',
      }),
      undefined,
    );

    vi.advanceTimersByTime(1500);
    expect(wrapper.emitted('settled')).toBeTruthy();
    wrapper.unmount();
  });

  it('keeps the session open and says so when the server refuses checkout', async () => {
    const startedAt = new Date(Date.now() - 90 * 60000).toISOString();
    request.mockImplementation((config: { url: string; method?: string }) => {
      if (config.method === 'POST')
        return Promise.reject(
          Object.assign(new Error('Conflict'), { status: 409 }),
        );
      return Promise.resolve({
        id: RENTAL_ID,
        branchId: BRANCH_ID,
        reservationId: RES_ID,
        tableId: TABLE_ID,
        customer: { kind: 'GUEST', displayName: 'Khun Ananda' },
        status: 'ACTIVE',
        startedAt,
        plannedEndsAt: new Date().toISOString(),
        checkedInBySubject: 'staff-1',
        charges: [
          {
            kind: 'TABLE',
            quantity: 1,
            unitHourlyRate: { amountMinor: 30000 },
          },
        ],
      });
    });
    const wrapper = mount(TableBillModal, {
      props: {
        isStaff: true,
        reservation: {
          id: RES_ID,
          branchId: BRANCH_ID,
          branchName: 'Siam',
          tableId: TABLE_ID,
          tableLabel: 'Table 1',
          startsAt: startedAt,
          endsAt: new Date().toISOString(),
          checkedInAt: startedAt,
          rentalId: RENTAL_ID,
          partySize: 4,
          status: 'CHECKED_IN',
          customerName: 'Khun Ananda',
          createdAt: startedAt,
          inPlayOrders: [],
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });
    await flushPromises();
    // 90 minutes is two started hours.
    expect(wrapper.find('.section-cost').text()).toBe('฿600');
    expect(wrapper.text()).not.toContain('PromptPay QR');

    await wrapper.find('.settle-btn').trigger('click');
    await flushPromises();

    expect(wrapper.find('[role="alert"]').text()).toContain('Checkout failed');
    expect(wrapper.emitted('settled')).toBeUndefined();
    wrapper.unmount();
  });

  it('offers the live branch menu for a sample visit tab', async () => {
    request.mockImplementation((config: { url: string }) => {
      if (config.url === `/api/v1/branches/${BRANCH_ID}/menu`) {
        return Promise.resolve({
          branchId: BRANCH_ID,
          items: [
            {
              item: {
                id: 'menu-yuzu',
                sku: 'BEV-YUZU-SODA',
                category: 'BEVERAGES',
                names: { 'en-US': 'Yuzu Craft Soda' },
                descriptions: { 'en-US': 'Sparkling citrus refresher' },
                price: { amountMinor: 8500 },
                popular: true,
              },
              available: 5,
            },
          ],
        });
      }
      return Promise.resolve({ items: [] });
    });

    const wrapper = mount(InPlayOrderModal, {
      props: {
        reservation: {
          ...useCustomerReservationsStore().reservations.value.find(
            (r) => r.id === 'res-thm-live',
          )!,
          branchId: BRANCH_ID,
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });

    await flushPromises();
    expect(wrapper.text()).toContain('Yuzu Craft Soda');
    expect(wrapper.text()).toContain('฿85');

    await wrapper.find('.step-btn.plus').trigger('click');
    await wrapper.find('.submit-order-btn').trigger('click');

    const orders =
      useCustomerReservationsStore().reservations.value.find(
        (r) => r.id === 'res-thm-live',
      )?.inPlayOrders ?? [];
    expect(orders.at(-1)).toBeDefined();
    expect(orders.length).toBeGreaterThan(0);
    expect(orders.at(-1)!.subtotal).toBe(85);
    expect(orders.at(-1)!.items[0].item.name).toBe('Yuzu Craft Soda');

    wrapper.unmount();
  });

  it('keeps late confirmed bookings as CONFIRMED until staff manually submit no-show', async () => {
    const startsAt = new Date(Date.now() - 30 * 60000).toISOString();
    const endsAt = new Date(Date.now() + 90 * 60000).toISOString();
    let status = 'CONFIRMED';

    request.mockImplementation(
      (config: { url: string; method?: string; data?: unknown }) => {
        if (config.url === '/api/v1/branches') {
          return Promise.resolve({
            items: [{ id: BRANCH_ID, name: 'Siam' }],
          });
        }
        if (config.url === `/api/v1/branches/${BRANCH_ID}/tables`) {
          return Promise.resolve({
            items: [
              {
                id: TABLE_ID,
                branchId: BRANCH_ID,
                label: 'Table 1',
                capacity: 4,
                hourlyRate: { amountMinor: 20000 },
                operationalStatus: 'IN_SERVICE',
              },
            ],
          });
        }
        if (config.url === `/api/v1/branches/${BRANCH_ID}/reservations`) {
          return Promise.resolve({
            items: [
              {
                id: RES_ID,
                branchId: BRANCH_ID,
                tableId: TABLE_ID,
                customer: { kind: 'GUEST', displayName: 'Late Guest' },
                source: 'ONLINE',
                status,
                startsAt,
                endsAt,
                partySize: 4,
                games: [],
                tableHourlyRate: { amountMinor: 20000 },
                createdAt: startsAt,
                createdBySubject: 'cust-1',
              },
            ],
          });
        }
        if (
          config.url ===
            `/api/v1/branches/${BRANCH_ID}/reservations/${RES_ID}/no-show` &&
          config.method === 'POST'
        ) {
          status = 'NO_SHOW';
          return Promise.resolve({
            id: RES_ID,
            branchId: BRANCH_ID,
            tableId: TABLE_ID,
            customer: { kind: 'GUEST', displayName: 'Late Guest' },
            source: 'ONLINE',
            status,
            startsAt,
            endsAt,
            partySize: 4,
            games: [],
            tableHourlyRate: { amountMinor: 20000 },
            createdAt: startsAt,
            createdBySubject: 'cust-1',
          });
        }
        return Promise.resolve({ items: [] });
      },
    );

    let workspace!: ReturnType<typeof useWorkspaceReservations>;
    const wrapper = mount(
      defineComponent({
        setup() {
          workspace = useWorkspaceReservations();
          return () => null;
        },
      }),
      { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } },
    );
    await flushPromises();
    await flushPromises();

    expect(workspace.allReservations.value[0].status).toBe('CONFIRMED');
    expect(
      request.mock.calls.some(([call]) => call.url?.endsWith('/no-show')),
    ).toBe(false);
    expect(workspace.needsYouItems.value[0]).toMatchObject({
      type: 'LATE',
      subtitle: expect.stringMatching(/^Scheduled /),
    });
    expect(workspace.needsYouItems.value[0].subtitle).not.toContain(
      'Auto-released',
    );

    workspace.openNoShow(workspace.allReservations.value[0]);
    expect(workspace.showNoShowModal.value).toBe(true);
    workspace.noShowReason.value = 'Did not arrive after call';
    await workspace.submitNoShow();
    await flushPromises();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: `/api/v1/branches/${BRANCH_ID}/reservations/${RES_ID}/no-show`,
        method: 'POST',
        data: { reason: 'Did not arrive after call' },
      }),
      undefined,
    );
    expect(workspace.showNoShowModal.value).toBe(false);
    expect(workspace.allReservations.value[0].status).toBe('NO_SHOW');
    wrapper.unmount();
  });
});
