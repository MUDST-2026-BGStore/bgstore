export interface FoodMenuItem {
  id: string;
  name: string;
  nameTh?: string;
  description: string;
  descriptionTh?: string;
  category: 'beverages' | 'snacks';
  price: number; // in THB
  dietary?: string;
  popular?: boolean;
}

export interface FoodOrderItem {
  item: FoodMenuItem;
  quantity: number;
}

export interface ReservationPreOrder {
  items: FoodOrderItem[];
  subtotal: number;
  discountRate: number; // 0.10 = 10%
  discountAmount: number;
  discountedTotal: number;
  paid: boolean;
  paymentMethod: 'promptpay_qr';
  paymentReference?: string;
  paidAtTimestamp?: string;
}

export interface ClientReservationSubmission {
  branchId: string;
  tableId: string;
  tableLabel: string;
  startsAt: string; // ISO-8601 timestamp
  endsAt: string; // ISO-8601 timestamp
  partySize: number;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  specialNotes?: string;
  preOrder?: ReservationPreOrder;
  tableHourlyRate: number;
  tableTotal: number;
  grandTotal: number;
}
