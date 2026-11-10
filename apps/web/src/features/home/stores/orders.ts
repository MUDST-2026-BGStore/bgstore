import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export type ShippingCarrier = 'flash' | 'kerry' | 'ems' | 'sameday';

export interface CarrierInfo {
  id: ShippingCarrier;
  name: string;
  nameTh: string;
  fee: number;
  freeThreshold: number | null; // e.g. 1500 THB for free shipping
  deliveryDays: string;
  deliveryDaysTh: string;
  badge?: string;
  badgeTh?: string;
}

export const CARRIERS: Record<ShippingCarrier, CarrierInfo> = {
  flash: {
    id: 'flash',
    name: 'Flash Express',
    nameTh: 'Flash Express',
    fee: 50,
    freeThreshold: null,
    deliveryDays: '1-2 business days',
    deliveryDaysTh: '1-2 วันทำการ',
    badge: 'Popular',
    badgeTh: 'ยอดนิยม',
  },
  kerry: {
    id: 'kerry',
    name: 'Kerry Express',
    nameTh: 'Kerry Express',
    fee: 60,
    freeThreshold: null,
    deliveryDays: '1-2 business days',
    deliveryDaysTh: '1-2 วันทำการ',
  },
  ems: {
    id: 'ems',
    name: 'Thailand Post EMS',
    nameTh: 'ไปรษณีย์ไทย EMS',
    fee: 55,
    freeThreshold: null,
    deliveryDays: '1-3 business days',
    deliveryDaysTh: '1-3 วันทำการ',
  },
  sameday: {
    id: 'sameday',
    name: 'Same-Day Courier',
    nameTh: 'ส่งด่วนภายในวัน (กทม. & ปริมณฑล)',
    fee: 120,
    freeThreshold: null,
    deliveryDays: 'Within today (order before 14:00)',
    deliveryDaysTh: 'ได้รับภายในวันนี้ (สั่งก่อน 14:00)',
    badge: 'Fastest',
    badgeTh: 'ด่วนที่สุด',
  },
};

export interface OrderItem {
  variantId: string;
  gameId: string;
  gameName: string;
  edition: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  imageUrl: string;
}

export interface ShippingAddress {
  name: string;
  phone: string;
  email: string;
  address: string;
  extra?: string;
  city: string;
  region: string;
  postal: string;
  country: string;
  note?: string;
}

export type OrderFulfillmentStatus = 'PROCESSING' | 'SHIPPED' | 'DELIVERED';

export interface RetailOrder {
  id: string; // e.g. "ORD-2026-4821"
  createdAt: string; // ISO
  items: OrderItem[];
  itemSubtotal: number;
  fulfillmentMethod: 'shipping' | 'pickup';
  pickupBranchName?: string;
  carrier?: ShippingCarrier;
  carrierName?: string;
  shippingFee: number;
  totalAmount: number;
  shippingAddress: ShippingAddress;
  paymentStatus: 'PAID' | 'PENDING';
  paymentReference: string;
  paidAtTimestamp: string;
  fulfillmentStatus: OrderFulfillmentStatus;
  trackingNumber?: string;
  shippedAt?: string;
  deliveredAt?: string;
  estimatedDeliveryDate: string;
}

const ORDERS_STORAGE_KEY = 'theparlor.retail_orders.v2';

const INITIAL_DEMO_ORDERS: RetailOrder[] = [
  {
    id: 'ORD-2026-1024',
    createdAt: new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString(),
    items: [
      {
        variantId: 'azul-th',
        gameId: 'azul',
        gameName: 'Azul',
        edition: 'Thai edition',
        quantity: 1,
        unitPrice: 1490,
        totalPrice: 1490,
        imageUrl: '/images/games/azul.jpg',
      },
    ],
    itemSubtotal: 1490,
    fulfillmentMethod: 'shipping',
    carrier: 'flash',
    carrierName: 'Flash Express',
    shippingFee: 50,
    totalAmount: 1540,
    shippingAddress: {
      name: 'Somchai Prasert',
      phone: '081-456-7890',
      email: 'developer@theparlor.example',
      address: '128/4 Sukhumvit Soi 21, Khlong Toei Nuea',
      city: 'Watthana',
      region: 'Bangkok',
      postal: '10110',
      country: 'Thailand',
      note: 'Please leave with condo security juristic office.',
    },
    paymentStatus: 'PAID',
    paymentReference: 'PP-ORD-948123',
    paidAtTimestamp: new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString(),
    fulfillmentStatus: 'SHIPPED',
    trackingNumber: 'TH0109283741F',
    shippedAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
    estimatedDeliveryDate: 'Tomorrow, by 18:00',
  },
  {
    id: 'ORD-2026-0988',
    createdAt: new Date(Date.now() - 120 * 60 * 60 * 1000).toISOString(),
    items: [
      {
        variantId: 'catan-en',
        gameId: 'catan',
        gameName: 'Catan',
        edition: 'English edition',
        quantity: 1,
        unitPrice: 1890,
        totalPrice: 1890,
        imageUrl: '/images/games/catan.jpg',
      },
      {
        variantId: 'love-letter-th',
        gameId: 'love-letter',
        gameName: 'Love Letter',
        edition: 'Thai edition',
        quantity: 1,
        unitPrice: 690,
        totalPrice: 690,
        imageUrl: '/images/games/love-letter.jpg',
      },
    ],
    itemSubtotal: 2580,
    fulfillmentMethod: 'shipping',
    carrier: 'kerry',
    carrierName: 'Kerry Express',
    shippingFee: 60,
    totalAmount: 2640,
    shippingAddress: {
      name: 'Somchai Prasert',
      phone: '081-456-7890',
      email: 'developer@theparlor.example',
      address: '128/4 Sukhumvit Soi 21, Khlong Toei Nuea',
      city: 'Watthana',
      region: 'Bangkok',
      postal: '10110',
      country: 'Thailand',
    },
    paymentStatus: 'PAID',
    paymentReference: 'PP-ORD-882194',
    paidAtTimestamp: new Date(Date.now() - 120 * 60 * 60 * 1000).toISOString(),
    fulfillmentStatus: 'DELIVERED',
    trackingNumber: 'KEX9482710382TH',
    shippedAt: new Date(Date.now() - 96 * 60 * 60 * 1000).toISOString(),
    deliveredAt: new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString(),
    estimatedDeliveryDate: 'Delivered',
  },
];

function loadSavedOrders(): RetailOrder[] {
  try {
    const raw = localStorage.getItem(ORDERS_STORAGE_KEY);
    if (!raw) return INITIAL_DEMO_ORDERS;
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : INITIAL_DEMO_ORDERS;
  } catch {
    return INITIAL_DEMO_ORDERS;
  }
}

function persistOrders(orders: RetailOrder[]) {
  try {
    localStorage.setItem(ORDERS_STORAGE_KEY, JSON.stringify(orders));
  } catch {
    /* Ignore storage error */
  }
}

export const useOrdersStore = defineStore('retail-orders', () => {
  const orders = ref<RetailOrder[]>(loadSavedOrders());
  const isOrdersModalOpen = ref(false);
  const activeOrderToTrack = ref<RetailOrder | null>(null);

  const pendingFulfillmentCount = computed(() => {
    return orders.value.filter((o) => o.fulfillmentStatus === 'PROCESSING').length;
  });

  const inTransitCount = computed(() => {
    return orders.value.filter((o) => o.fulfillmentStatus === 'SHIPPED').length;
  });

  const deliveredCount = computed(() => {
    return orders.value.filter((o) => o.fulfillmentStatus === 'DELIVERED').length;
  });

  const totalRetailRevenue = computed(() => {
    return orders.value.reduce((sum, o) => sum + o.totalAmount, 0);
  });

  function generateRandomTrackingNumber(carrier: ShippingCarrier): string {
    const randomDigits = Math.floor(100000000 + Math.random() * 900000000);
    switch (carrier) {
      case 'flash':
        return `TH${randomDigits}F`;
      case 'kerry':
        return `KEX${randomDigits}TH`;
      case 'ems':
        return `ED${randomDigits}TH`;
      case 'sameday':
        return `PARLOR-SD-${Math.floor(1000 + Math.random() * 9000)}`;
    }
  }

  function createOrder(payload: {
    items: OrderItem[];
    itemSubtotal: number;
    fulfillmentMethod: 'shipping' | 'pickup';
    pickupBranchName?: string;
    carrier?: ShippingCarrier;
    shippingFee: number;
    shippingAddress: ShippingAddress;
    paymentReference: string;
  }): RetailOrder {
    const carrierId = payload.carrier ?? 'flash';
    const carrierObj = CARRIERS[carrierId];
    const trackingNo = payload.fulfillmentMethod === 'shipping'
      ? generateRandomTrackingNumber(carrierId)
      : undefined;

    const randNum = Math.floor(1000 + Math.random() * 9000);
    const orderId = `ORD-2026-${randNum}`;

    const newOrder: RetailOrder = {
      id: orderId,
      createdAt: new Date().toISOString(),
      items: payload.items,
      itemSubtotal: payload.itemSubtotal,
      fulfillmentMethod: payload.fulfillmentMethod,
      pickupBranchName: payload.pickupBranchName,
      carrier: payload.carrier,
      carrierName: payload.fulfillmentMethod === 'shipping' ? ((payload.carrier === 'sameday' ? 'ส่งด่วนภายในวันนี้' : carrierObj?.name) ?? 'Flash Express') : undefined,
      shippingFee: payload.shippingFee,
      totalAmount: payload.itemSubtotal + payload.shippingFee,
      shippingAddress: payload.shippingAddress,
      paymentStatus: 'PAID',
      paymentReference: payload.paymentReference,
      paidAtTimestamp: new Date().toISOString(),
      fulfillmentStatus: 'PROCESSING',
      trackingNumber: trackingNo,
      estimatedDeliveryDate: payload.fulfillmentMethod === 'shipping'
        ? (carrierId === 'sameday' ? 'ได้รับภายในวันนี้ (ก่อน 21:00 น.)' : 'จัดส่งถึงภายใน 1-2 วันทำการ')
        : 'พร้อมรับสินค้าที่หน้าร้านภายใน 2 ชั่วโมง',
    };

    orders.value.unshift(newOrder);
    persistOrders(orders.value);
    return newOrder;
  }

  function updateFulfillmentStatus(
    orderId: string,
    status: OrderFulfillmentStatus,
    trackingNumber?: string
  ) {
    const order = orders.value.find((o) => o.id === orderId);
    if (!order) return;

    order.fulfillmentStatus = status;
    if (trackingNumber) {
      order.trackingNumber = trackingNumber;
    }
    if (status === 'SHIPPED') {
      order.shippedAt = new Date().toISOString();
      if (!order.trackingNumber && order.carrier) {
        order.trackingNumber = generateRandomTrackingNumber(order.carrier);
      }
    } else if (status === 'DELIVERED') {
      order.deliveredAt = new Date().toISOString();
      order.estimatedDeliveryDate = 'Delivered';
    }
    persistOrders(orders.value);
  }

  function openOrdersModal(order?: RetailOrder) {
    if (order) {
      activeOrderToTrack.value = order;
    }
    isOrdersModalOpen.value = true;
  }

  function closeOrdersModal() {
    isOrdersModalOpen.value = false;
    activeOrderToTrack.value = null;
  }

  return {
    orders,
    isOrdersModalOpen,
    activeOrderToTrack,
    pendingFulfillmentCount,
    inTransitCount,
    deliveredCount,
    totalRetailRevenue,
    createOrder,
    updateFulfillmentStatus,
    openOrdersModal,
    closeOrdersModal,
  };
});
