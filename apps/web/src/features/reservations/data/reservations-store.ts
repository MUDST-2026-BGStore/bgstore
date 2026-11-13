// Local sample reservations and in-play tab orders. Customer and staff
// reservation pages use the live reservation, check-in and rental checkout
// endpoints when available and fall back to this store when unseeded or offline.
import { ref, computed } from 'vue';

export interface PreOrderItemRecord {
  item: {
    id: string;
    name: string;
    nameTh?: string;
    price: number;
  };
  quantity: number;
}

export interface CustomerReservationPreOrder {
  items: PreOrderItemRecord[];
  subtotal: number;
  discountRate?: number;
  discountAmount?: number;
  discountedTotal: number;
  paid?: boolean;
  paymentMethod?: string;
  paymentReference?: string;
  paidAtTimestamp?: string;
}

export interface InPlayOrder {
  id: string;
  orderNumber: string;
  createdAt: string;
  items: PreOrderItemRecord[];
  subtotal: number;
  status: 'RECEIVED' | 'PREPARING' | 'SERVED';
  paid: boolean;
  paymentMethod: 'promptpay_qr' | 'on_tab' | 'cash';
  paymentReference?: string;
  notes?: string;
}

export interface CustomerReservation {
  id: string;
  branchId: string;
  branchName: string;
  tableId: string;
  tableLabel: string;
  startsAt: string;
  endsAt: string;
  checkedInAt?: string;
  completedAt?: string;
  rentalId?: string;
  partySize: number;
  status: 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
  customerName: string;
  customerPhone?: string;
  notes?: string;
  cancellationReason?: string;
  createdAt: string;
  clientType?: 'RESERVED' | 'WALK_IN';
  hourlyRate?: number;
  preOrder?: CustomerReservationPreOrder;
  inPlayOrders?: InPlayOrder[];
}

const STORAGE_KEY = 'theparlor_customer_reservations_v3';

function getInitialReservations(): CustomerReservation[] {
  const now = new Date();

  // 1. TYPE A: Reserved Client (Late Arrival Example: Booked 1 hr ago, checked in 15 mins late!)
  // Scheduled: 60 mins slot. Arrived 15 min late -> played 45 mins.
  const bookedStart = new Date(now.getTime() - 60 * 60000);
  const lateCheckIn = new Date(now.getTime() - 45 * 60000); // 15 mins late
  const bookedEnd = new Date(bookedStart.getTime() + 60 * 60000); // ends around now

  // 2. TYPE B: Walk-in Client (Started directly at counter 35 mins ago)
  const walkinCheckIn = new Date(now.getTime() - 35 * 60000);
  const walkinEnd = new Date(walkinCheckIn.getTime() + 120 * 60000); // 2 hours

  // Upcoming Tomorrow at 18:00
  const tomorrowStart = new Date(now);
  tomorrowStart.setDate(now.getDate() + 1);
  tomorrowStart.setHours(18, 0, 0, 0);
  const tomorrowEnd = new Date(tomorrowStart);
  tomorrowEnd.setHours(20, 0, 0, 0);

  // Past Completed (3 days ago)
  const pastStart = new Date(now);
  pastStart.setDate(now.getDate() - 3);
  pastStart.setHours(14, 0, 0, 0);
  const pastEnd = new Date(pastStart);
  pastEnd.setHours(17, 0, 0, 0);

  // Past Cancelled (last week)
  const lastWeekStart = new Date(now);
  lastWeekStart.setDate(now.getDate() - 7);
  lastWeekStart.setHours(19, 0, 0, 0);
  const lastWeekEnd = new Date(lastWeekStart);
  lastWeekEnd.setHours(21, 30, 0, 0);

  return [
    {
      id: 'res-thm-live',
      branchId: '00000000-0000-0000-0000-000000000001',
      branchName: 'Siam',
      tableId: 't-siam-1',
      tableLabel: 'The Emerald Salon (Table 1)',
      startsAt: bookedStart.toISOString(),
      endsAt: bookedEnd.toISOString(),
      checkedInAt: lateCheckIn.toISOString(),
      partySize: 4,
      status: 'CHECKED_IN',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      clientType: 'RESERVED',
      hourlyRate: 300,
      notes:
        "[Pre-order Paid QR (-10%)]: 2x Coca-Cola Original, 2x Lay's Classic Rock Salt",
      createdAt: new Date(now.getTime() - 3600000 * 24).toISOString(),
      preOrder: {
        items: [
          {
            item: {
              id: 'bev-coca-cola',
              name: 'Coca-Cola Original (Can 325ml)',
              nameTh: 'โคคา-โคล่า รสชาติต้นตำรับ (กระป๋อง 325 มล.)',
              price: 35,
            },
            quantity: 2,
          },
          {
            item: {
              id: 'snack-lays-classic',
              name: "Lay's Classic Rock Salt (Big Bag 73g)",
              nameTh: 'เลย์ รสมันฝรั่งแท้ คลาสสิก (ซองใหญ่ 73 กรัม)',
              price: 40,
            },
            quantity: 2,
          },
        ],
        subtotal: 150,
        discountRate: 0.1,
        discountAmount: 15,
        discountedTotal: 135,
        paid: true,
        paymentMethod: 'promptpay_qr',
        paymentReference: 'TH-PRM-LIVE',
        paidAtTimestamp: new Date(now.getTime() - 3600000 * 24).toISOString(),
      },
      inPlayOrders: [
        {
          id: 'ord-live-1',
          orderNumber: 'TAB-01',
          createdAt: new Date(now.getTime() - 25 * 60000).toISOString(),
          items: [
            {
              item: {
                id: 'bev-singha-soda',
                name: 'Singha Soda Water (Glass Bottle 325ml)',
                nameTh: 'สิงห์โซดา (ขวดแก้ว 325 มล.)',
                price: 25,
              },
              quantity: 2,
            },
            {
              item: {
                id: 'snack-pringles-sour-cream',
                name: 'Pringles Sour Cream & Onion (Canister 107g)',
                nameTh: 'พริงเกิลส์ รสซาวครีมและหัวหอม (กระป๋อง 107 กรัม)',
                price: 65,
              },
              quantity: 1,
            },
          ],
          subtotal: 115,
          status: 'SERVED',
          paid: false,
          paymentMethod: 'on_tab',
        },
      ],
    },
    {
      id: 'walkin-siam-live',
      branchId: '00000000-0000-0000-0000-000000000001',
      branchName: 'Siam',
      tableId: 't-siam-2',
      tableLabel: 'The Royal Library (Table 2)',
      startsAt: walkinCheckIn.toISOString(),
      endsAt: walkinEnd.toISOString(),
      checkedInAt: walkinCheckIn.toISOString(),
      partySize: 3,
      status: 'CHECKED_IN',
      customerName: 'Walk-in Guests',
      customerPhone: '+66 89 765 4321',
      clientType: 'WALK_IN',
      hourlyRate: 200,
      notes: 'Direct counter walk-in. Snacks added by staff.',
      createdAt: walkinCheckIn.toISOString(),
      inPlayOrders: [
        {
          id: 'ord-walkin-1',
          orderNumber: 'COUNTER-01',
          createdAt: new Date(now.getTime() - 20 * 60000).toISOString(),
          items: [
            {
              item: {
                id: 'bev-coke-zero',
                name: 'Coca-Cola Zero Sugar (Can 325ml)',
                nameTh: 'โค้กไม่มีน้ำตาล ซีโร่ (กระป๋อง 325 มล.)',
                price: 35,
              },
              quantity: 2,
            },
            {
              item: {
                id: 'snack-lays-seaweed',
                name: "Lay's Nori Seaweed (Big Bag 73g)",
                nameTh: 'เลย์ รสโนริสาหร่าย (ซองใหญ่ 73 กรัม)',
                price: 40,
              },
              quantity: 1,
            },
          ],
          subtotal: 110,
          status: 'SERVED',
          paid: false,
          paymentMethod: 'on_tab',
        },
      ],
    },
    {
      id: 'res-thm-9812',
      branchId: '00000000-0000-0000-0000-000000000001',
      branchName: 'Siam',
      tableId: 't-siam-3',
      tableLabel: 'The Grand Ballroom (Table 3)',
      startsAt: tomorrowStart.toISOString(),
      endsAt: tomorrowEnd.toISOString(),
      partySize: 6,
      status: 'CONFIRMED',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      clientType: 'RESERVED',
      hourlyRate: 250,
      notes:
        '[Pre-order Paid QR (-10%)]: 4x Oishi Green Tea Honey Lemon, 2x Glico Pocky Chocolate (Ref: TH-PRM-9812)',
      createdAt: new Date(now.getTime() - 3600000 * 4).toISOString(),
      preOrder: {
        items: [
          {
            item: {
              id: 'bev-oishi-lemon',
              name: 'Oishi Green Tea Honey Lemon (Bottle 350ml)',
              nameTh: 'ชาเขียวโออิชิ น้ำผึ้งผสมมะนาว (ขวด 350 มล.)',
              price: 30,
            },
            quantity: 4,
          },
          {
            item: {
              id: 'snack-pocky-chocolate',
              name: 'Glico Pocky Chocolate (Box 45g)',
              nameTh:
                'กูลิโกะ ป๊อกกี้ บิสกิตแท่งเคลือบช็อกโกแลต (กล่อง 45 กรัม)',
              price: 25,
            },
            quantity: 2,
          },
        ],
        subtotal: 170,
        discountRate: 0.1,
        discountAmount: 17,
        discountedTotal: 153,
        paid: true,
        paymentMethod: 'promptpay_qr',
        paymentReference: 'TH-PRM-9812',
        paidAtTimestamp: new Date(now.getTime() - 3600000 * 4).toISOString(),
      },
    },
    {
      id: 'res-thm-7401',
      branchId: '00000000-0000-0000-0000-000000000002',
      branchName: 'Ari',
      tableId: 't-ari-1',
      tableLabel: 'The Garden Conservatory (Table 1)',
      startsAt: pastStart.toISOString(),
      endsAt: pastEnd.toISOString(),
      checkedInAt: pastStart.toISOString(),
      completedAt: pastEnd.toISOString(),
      partySize: 4,
      status: 'COMPLETED',
      customerName: 'Khun Parlor Member',
      clientType: 'RESERVED',
      hourlyRate: 200,
      notes: 'Completed session. Settled at counter.',
      createdAt: new Date(now.getTime() - 3600000 * 72).toISOString(),
    },
    {
      id: 'res-thm-5102',
      branchId: '00000000-0000-0000-0000-000000000001',
      branchName: 'Siam',
      tableId: 't-siam-4',
      tableLabel: 'The Mezzanine Lounge (Table 4)',
      startsAt: lastWeekStart.toISOString(),
      endsAt: lastWeekEnd.toISOString(),
      partySize: 2,
      status: 'CANCELLED',
      customerName: 'Khun Parlor Member',
      clientType: 'RESERVED',
      hourlyRate: 150,
      createdAt: new Date(now.getTime() - 3600000 * 168).toISOString(),
    },
  ];
}

function loadReservations(): CustomerReservation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      const initial = getInitialReservations();
      saveReservations(initial);
      return initial;
    }
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : getInitialReservations();
  } catch {
    return getInitialReservations();
  }
}

function saveReservations(list: CustomerReservation[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  } catch {
    // Ignore storage quota errors
  }
}

const reservationsList = ref<CustomerReservation[]>(loadReservations());
const externalInPlayOrders = ref<Record<string, InPlayOrder[]>>({});

export function useCustomerReservationsStore() {
  function autoCheckNoShows() {
    const now = Date.now();
    let updated = false;
    for (const r of reservationsList.value) {
      if (r.status === 'CONFIRMED' && !r.checkedInAt) {
        const startMs = new Date(r.startsAt).getTime();
        // If 15 minutes past start time and customer has not checked in
        if (now - startMs > 15 * 60 * 1000) {
          r.status = 'NO_SHOW';
          const autoNote =
            '[System]: Customer did not arrive within 15 minutes. Auto-released table.';
          r.notes = r.notes ? `${r.notes}\n${autoNote}` : autoNote;
          updated = true;
        }
      }
    }
    if (updated) {
      saveReservations([...reservationsList.value]);
    }
  }

  // Check on store initialization
  autoCheckNoShows();

  const allReservations = computed(() => {
    autoCheckNoShows();
    return reservationsList.value;
  });
  const activeSession = computed(() =>
    reservationsList.value.find((r) => r.status === 'CHECKED_IN'),
  );

  function addReservation(reservation: CustomerReservation) {
    if (!reservation.clientType) {
      reservation.clientType = 'RESERVED';
    }
    reservationsList.value = [reservation, ...reservationsList.value];
    saveReservations(reservationsList.value);
  }

  function startWalkInSession(params: {
    branchId: string;
    branchName: string;
    tableId: string;
    tableLabel: string;
    partySize: number;
    customerName: string;
    customerPhone?: string;
    hourlyRate?: number;
    durationHours?: number;
  }): CustomerReservation {
    const now = new Date();
    const duration = params.durationHours || 2;
    const endsAt = new Date(now.getTime() + duration * 3600000);

    const newWalkIn: CustomerReservation = {
      id: `walkin-${Date.now().toString(36)}`,
      branchId: params.branchId,
      branchName: params.branchName,
      tableId: params.tableId,
      tableLabel: params.tableLabel,
      startsAt: now.toISOString(),
      endsAt: endsAt.toISOString(),
      checkedInAt: now.toISOString(),
      partySize: params.partySize,
      status: 'CHECKED_IN',
      customerName: params.customerName || 'Walk-in Guests',
      customerPhone: params.customerPhone,
      clientType: 'WALK_IN',
      hourlyRate: params.hourlyRate || 200,
      notes: 'Counter Walk-in Session',
      createdAt: now.toISOString(),
      inPlayOrders: [],
    };

    reservationsList.value = [newWalkIn, ...reservationsList.value];
    saveReservations(reservationsList.value);
    return newWalkIn;
  }

  function cancelReservation(reservationId: string, reason?: string) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      target.status = 'CANCELLED';
      target.cancellationReason = reason || undefined;
      saveReservations([...reservationsList.value]);
    }
  }

  function checkInReservation(reservationId: string) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      target.status = 'CHECKED_IN';
      target.checkedInAt = new Date().toISOString();
      saveReservations([...reservationsList.value]);
    }
  }

  function getInPlayOrders(reservationId: string): InPlayOrder[] {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      return target.inPlayOrders ?? [];
    }
    return externalInPlayOrders.value[reservationId] ?? [];
  }

  function addInPlayOrder(
    reservationId: string,
    order: {
      items: PreOrderItemRecord[];
      subtotal: number;
      paid: boolean;
      paymentMethod: 'promptpay_qr' | 'on_tab' | 'cash';
      paymentReference?: string;
      notes?: string;
    },
  ) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      if (!target.inPlayOrders) {
        target.inPlayOrders = [];
      }
      const orderCount = target.inPlayOrders.length + 1;
      const prefix = target.clientType === 'WALK_IN' ? 'COUNTER' : 'TAB';
      const newOrder: InPlayOrder = {
        id: `ord-${Date.now().toString(36)}`,
        orderNumber: `${prefix}-${String(orderCount).padStart(2, '0')}`,
        createdAt: new Date().toISOString(),
        items: order.items,
        subtotal: order.subtotal,
        status: 'SERVED',
        paid: order.paid,
        paymentMethod: order.paymentMethod,
        paymentReference: order.paymentReference,
        notes: order.notes,
      };
      target.inPlayOrders.push(newOrder);
      saveReservations([...reservationsList.value]);
      return newOrder;
    }
    const existing = externalInPlayOrders.value[reservationId] ?? [];
    const orderCount = existing.length + 1;
    const newOrder: InPlayOrder = {
      id: `ord-${Date.now().toString(36)}`,
      orderNumber: `TAB-${String(orderCount).padStart(2, '0')}`,
      createdAt: new Date().toISOString(),
      items: order.items,
      subtotal: order.subtotal,
      status: 'SERVED',
      paid: order.paid,
      paymentMethod: order.paymentMethod,
      paymentReference: order.paymentReference,
      notes: order.notes,
    };
    externalInPlayOrders.value = {
      ...externalInPlayOrders.value,
      [reservationId]: [...existing, newOrder],
    };
    return newOrder;
  }

  function addStaffSnackOrder(
    reservationId: string,
    items: PreOrderItemRecord[],
    notes?: string,
  ) {
    const subtotal = items.reduce(
      (sum, it) => sum + it.item.price * it.quantity,
      0,
    );
    return addInPlayOrder(reservationId, {
      items,
      subtotal,
      paid: false,
      paymentMethod: 'on_tab',
      notes: notes || 'Added by staff at counter',
    });
  }

  function updateInPlayOrderStatus(
    reservationId: string,
    orderId: string,
    status: InPlayOrder['status'],
  ) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target && target.inPlayOrders) {
      const ord = target.inPlayOrders.find((o) => o.id === orderId);
      if (ord) {
        ord.status = status;
        saveReservations([...reservationsList.value]);
      }
      return;
    }
    const external = externalInPlayOrders.value[reservationId];
    if (external) {
      const ord = external.find((o) => o.id === orderId);
      if (ord) {
        ord.status = status;
      }
    }
  }

  function settleSessionBill(reservationId: string, _paymentMethod = 'counter') {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      target.status = 'COMPLETED';
      target.completedAt = new Date().toISOString();
      if (target.inPlayOrders) {
        target.inPlayOrders.forEach((o) => {
          o.paid = true;
          o.status = 'SERVED';
          if (!o.paymentReference) {
            o.paymentReference = `SETTLED-${Date.now().toString().slice(-4)}`;
          }
        });
      }
      saveReservations([...reservationsList.value]);
    }
    const external = externalInPlayOrders.value[reservationId];
    if (external) {
      external.forEach((o) => {
        o.paid = true;
        o.status = 'SERVED';
        if (!o.paymentReference) {
          o.paymentReference = `SETTLED-${Date.now().toString().slice(-4)}`;
        }
      });
    }
  }

  function resetToDefault() {
    reservationsList.value = getInitialReservations();
    externalInPlayOrders.value = {};
    saveReservations(reservationsList.value);
  }

  return {
    reservations: allReservations,
    activeSession,
    getInPlayOrders,
    addReservation,
    startWalkInSession,
    cancelReservation,
    checkInReservation,
    addInPlayOrder,
    addStaffSnackOrder,
    updateInPlayOrderStatus,
    settleSessionBill,
    autoCheckNoShows,
    resetToDefault,
  };
}
