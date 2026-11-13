import { defineComponent } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
      rentalId: RENTAL_ID,
      hourlyRate: 320,
      clientType: 'RESERVED',
    });

    useCustomerReservationsStore().addStaffSnackOrder(RES_ID, [
      {
        item: { id: 'snack-1', name: 'Crispy Chips', price: 45 },
        quantity: 2,
      },
    ]);

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
    expect(workspace.staffBillReservation.value.inPlayOrders).toHaveLength(1);
    expect(workspace.staffBillReservation.value.inPlayOrders[0].subtotal).toBe(
      90,
    );

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

  it('loads live branch menu items in InPlayOrderModal and records tab orders', async () => {
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
          id: RES_ID,
          branchId: BRANCH_ID,
          branchName: 'Siam',
          tableId: TABLE_ID,
          tableLabel: 'Table 1',
          startsAt: new Date().toISOString(),
          endsAt: new Date(Date.now() + 3600000).toISOString(),
          partySize: 2,
          status: 'CHECKED_IN',
          customerName: 'Khun Ananda',
          createdAt: new Date().toISOString(),
        },
      },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    });

    await flushPromises();
    expect(wrapper.text()).toContain('Yuzu Craft Soda');
    expect(wrapper.text()).toContain('฿85');

    await wrapper.find('.step-btn.plus').trigger('click');
    await wrapper.find('.submit-order-btn').trigger('click');

    const orders = useCustomerReservationsStore().getInPlayOrders(RES_ID);
    expect(orders).toHaveLength(1);
    expect(orders[0].subtotal).toBe(85);
    expect(orders[0].items[0].item.name).toBe('Yuzu Craft Soda');

    wrapper.unmount();
  });
});
