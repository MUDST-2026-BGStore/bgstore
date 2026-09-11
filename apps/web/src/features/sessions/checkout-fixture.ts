export interface CheckoutReceipt {
  orderNumber: string;
  partySize: number;
  hours: number;
  hourlyRatePerPerson: number;
  totalDue: number;
  currency: 'THB';
}

/** Display-only receipt until staff-confirmed billing is available. */
export const checkoutFixture: CheckoutReceipt = {
  orderNumber: '88902',
  partySize: 2,
  hours: 2,
  hourlyRatePerPerson: 50,
  totalDue: 200,
  currency: 'THB',
};

export type PaymentMethod = 'promptpay' | 'bank' | 'cash';
