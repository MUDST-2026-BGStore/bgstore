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
  partySize: number;
  status: 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED';
  customerName: string;
  customerPhone?: string;
  notes?: string;
  cancellationReason?: string;
  createdAt: string;
  hourlyRate?: number;
  preOrder?: CustomerReservationPreOrder;
  inPlayOrders?: InPlayOrder[];
}

const STORAGE_KEY = 'theparlor_customer_reservations_v2';

function getInitialReservations(): CustomerReservation[] {
  const now = new Date();

  // Active Ongoing Session (Checked-in 1 hour ago, currently playing!)
  const activeStart = new Date(now.getTime() - 3600000 * 1.25);
  const activeEnd = new Date(now.getTime() + 3600000 * 1.75);

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
      startsAt: activeStart.toISOString(),
      endsAt: activeEnd.toISOString(),
      checkedInAt: activeStart.toISOString(),
      partySize: 4,
      status: 'CHECKED_IN',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      hourlyRate: 200,
      notes: '[Pre-order Paid QR (-10%)]: 2x Belgian Craft Truffle Fries, 2x Midnight Cold Brew Cascara',
      createdAt: new Date(now.getTime() - 3600000 * 24).toISOString(),
      preOrder: {
        items: [
          {
            item: { id: 'snack-1', name: 'Belgian Craft Truffle Fries', nameTh: 'เฟรนช์ฟรายส์ทรัฟเฟิลสไตล์เบลเยียม', price: 180 },
            quantity: 2,
          },
          {
            item: { id: 'drink-1', name: 'Midnight Cold Brew Cascara', nameTh: 'โคลด์บรูว์มิดไนท์ คาสคารา', price: 140 },
            quantity: 2,
          },
        ],
        subtotal: 640,
        discountRate: 0.1,
        discountAmount: 64,
        discountedTotal: 576,
        paid: true,
        paymentMethod: 'promptpay_qr',
        paymentReference: 'TH-PRM-LIVE',
        paidAtTimestamp: new Date(now.getTime() - 3600000 * 24).toISOString(),
      },
      inPlayOrders: [
        {
          id: 'ord-live-1',
          orderNumber: 'TAB-01',
          createdAt: new Date(now.getTime() - 3600000 * 0.75).toISOString(),
          items: [
            {
              item: { id: 'snack-3', name: 'Crispy Calamari & Citrus Dip', nameTh: 'ปลาหมึกทอดกรอบซอสดิปซิตรัส', price: 210 },
              quantity: 2,
            },
          ],
          subtotal: 420,
          status: 'SERVED',
          paid: false,
          paymentMethod: 'on_tab',
          notes: 'Extra lime wedge',
        },
        {
          id: 'ord-live-2',
          orderNumber: 'TAB-02',
          createdAt: new Date(now.getTime() - 3600000 * 0.2).toISOString(),
          items: [
            {
              item: { id: 'drink-3', name: 'Lychee Rose Sparkling Elixir', nameTh: 'ลิ้นจี่กุหลาบ สปาร์กลิ้ง เอลิกเซอร์', price: 150 },
              quantity: 2,
            },
          ],
          subtotal: 300,
          status: 'PREPARING',
          paid: false,
          paymentMethod: 'on_tab',
        },
      ],
    },
    {
      id: 'res-thm-9812',
      branchId: '00000000-0000-0000-0000-000000000001',
      branchName: 'Siam',
      tableId: 't-siam-2',
      tableLabel: 'The Royal Library (Table 3)',
      startsAt: tomorrowStart.toISOString(),
      endsAt: tomorrowEnd.toISOString(),
      partySize: 4,
      status: 'CONFIRMED',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      hourlyRate: 200,
      notes: '[Pre-order Paid QR (-10%)]: 2x Belgian Craft Truffle Fries, 4x Midnight Cold Brew Cascara (Ref: TH-PRM-9812)',
      createdAt: new Date(now.getTime() - 3600000 * 4).toISOString(),
      preOrder: {
        items: [
          {
            item: { id: 'snack-1', name: 'Belgian Craft Truffle Fries', nameTh: 'เฟรนช์ฟรายส์ทรัฟเฟิลสไตล์เบลเยียม', price: 180 },
            quantity: 2,
          },
          {
            item: { id: 'drink-1', name: 'Midnight Cold Brew Cascara', nameTh: 'โคลด์บรูว์มิดไนท์ คาสคารา', price: 140 },
            quantity: 4,
          },
        ],
        subtotal: 920,
        discountRate: 0.1,
        discountAmount: 92,
        discountedTotal: 828,
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
      tableLabel: 'Ari Garden Veranda (Table 1)',
      startsAt: pastStart.toISOString(),
      endsAt: pastEnd.toISOString(),
      checkedInAt: pastStart.toISOString(),
      completedAt: pastEnd.toISOString(),
      partySize: 2,
      status: 'COMPLETED',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      hourlyRate: 180,
      notes: '[Pre-order Paid QR (-10%)]: 1x Artisan Matcha Basque Cheesecake, 2x Lychee Rose Sparkling Elixir (Ref: TH-PRM-7401)',
      createdAt: pastStart.toISOString(),
      preOrder: {
        items: [
          {
            item: { id: 'dessert-1', name: 'Artisan Matcha Basque Cheesecake', nameTh: 'มัทฉะบาสก์ชีสเค้กพรีเมียม', price: 195 },
            quantity: 1,
          },
          {
            item: { id: 'drink-3', name: 'Lychee Rose Sparkling Elixir', nameTh: 'ลิ้นจี่กุหลาบ สปาร์กลิ้ง เอลิกเซอร์', price: 150 },
            quantity: 2,
          },
        ],
        subtotal: 495,
        discountRate: 0.1,
        discountAmount: 49.5,
        discountedTotal: 445.5,
        paid: true,
        paymentMethod: 'promptpay_qr',
        paymentReference: 'TH-PRM-7401',
        paidAtTimestamp: pastStart.toISOString(),
      },
    },
    {
      id: 'res-thm-6120',
      branchId: '00000000-0000-0000-0000-000000000003',
      branchName: 'Riverside',
      tableId: 't-riv-2',
      tableLabel: 'Chao Phraya Lounge (Table 2)',
      startsAt: lastWeekStart.toISOString(),
      endsAt: lastWeekEnd.toISOString(),
      partySize: 6,
      status: 'CANCELLED',
      cancellationReason: 'Schedule conflict with work event',
      customerName: 'Khun Parlor Member',
      customerPhone: '+66 81 234 5678',
      hourlyRate: 220,
      notes: 'Initial booking cancelled by customer.',
      createdAt: lastWeekStart.toISOString(),
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
    return JSON.parse(raw);
  } catch {
    return getInitialReservations();
  }
}

function saveReservations(items: CustomerReservation[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch (e) {
    console.error('Failed to save customer reservations to localStorage', e);
  }
}

const reservationsList = ref<CustomerReservation[]>(loadReservations());

export function useCustomerReservationsStore() {
  const allReservations = computed(() => reservationsList.value);
  const activeSession = computed(() =>
    reservationsList.value.find((r) => r.status === 'CHECKED_IN')
  );

  function addReservation(reservation: CustomerReservation) {
    reservationsList.value = [reservation, ...reservationsList.value];
    saveReservations(reservationsList.value);
  }

  function cancelReservation(reservationId: string, reason?: string) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      target.status = 'CANCELLED';
      target.cancellationReason = reason || 'Cancelled by customer';
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

  function addInPlayOrder(
    reservationId: string,
    order: {
      items: PreOrderItemRecord[];
      subtotal: number;
      paid: boolean;
      paymentMethod: 'promptpay_qr' | 'on_tab' | 'cash';
      paymentReference?: string;
      notes?: string;
    }
  ) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      if (!target.inPlayOrders) {
        target.inPlayOrders = [];
      }
      const orderCount = target.inPlayOrders.length + 1;
      const newOrder: InPlayOrder = {
        id: `ord-${Date.now().toString(36)}`,
        orderNumber: `TAB-${String(orderCount).padStart(2, '0')}`,
        createdAt: new Date().toISOString(),
        items: order.items,
        subtotal: order.subtotal,
        status: 'RECEIVED',
        paid: order.paid,
        paymentMethod: order.paymentMethod,
        paymentReference: order.paymentReference,
        notes: order.notes,
      };
      target.inPlayOrders.push(newOrder);
      saveReservations([...reservationsList.value]);
      return newOrder;
    }
    return null;
  }

  function updateInPlayOrderStatus(
    reservationId: string,
    orderId: string,
    status: InPlayOrder['status']
  ) {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target && target.inPlayOrders) {
      const ord = target.inPlayOrders.find((o) => o.id === orderId);
      if (ord) {
        ord.status = status;
        saveReservations([...reservationsList.value]);
      }
    }
  }

  function settleSessionBill(reservationId: string, paymentMethod = 'promptpay_qr') {
    const target = reservationsList.value.find((r) => r.id === reservationId);
    if (target) {
      target.status = 'COMPLETED';
      target.completedAt = new Date().toISOString();
      if (target.inPlayOrders) {
        target.inPlayOrders.forEach((o) => {
          o.paid = true;
          o.status = 'SERVED';
          if (!o.paymentReference) {
            o.paymentReference = `TAB-SETTLED-${Date.now().toString().slice(-4)}`;
          }
        });
      }
      saveReservations([...reservationsList.value]);
    }
  }

  function resetToDefault() {
    reservationsList.value = getInitialReservations();
    saveReservations(reservationsList.value);
  }

  return {
    reservations: allReservations,
    activeSession,
    addReservation,
    cancelReservation,
    checkInReservation,
    addInPlayOrder,
    updateInPlayOrderStatus,
    settleSessionBill,
    resetToDefault,
  };
}
