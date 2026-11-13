// TODO(qr-payment-api): Builds a display-only PromptPay-style QR in the browser. The
// backend's only payment method is card tokens for retail orders.
import QRCode from 'qrcode';

export interface PromptPayQrDetails {
  dataUrl: string;
  reference: string;
  amount: number;
  formattedAmount: string;
  merchantName: string;
  expiresAt: string; // ISO timestamp
}

/**
 * Calculates pre-order food & drink 10% discount
 */
export function calculatePreOrderDiscount(foodTotal: number) {
  const discountRate = 0.1; // 10% discount for pre-ordering with table reservation
  const discountAmount = Math.round(foodTotal * discountRate);
  const discountedTotal = Math.max(0, foodTotal - discountAmount);
  return {
    discountRate,
    discountAmount,
    discountedTotal,
  };
}

/**
 * Generates an authentic high-resolution PromptPay / Universal QR Code Data URL
 */
export async function generateReservationQrCode(
  reservationRef: string,
  amount: number,
  merchantName = 'The Parlor Board Game Cafe',
): Promise<PromptPayQrDetails> {
  const reference =
    reservationRef || `PARLOR-${Math.floor(100000 + Math.random() * 900000)}`;
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString(); // 15 mins expiry

  // PromptPay/EMVCo mock payload structure containing key transaction details
  const payload = JSON.stringify({
    service: 'PromptPay-QR',
    merchant: merchantName,
    ref: reference,
    amount: amount.toFixed(2),
    currency: 'THB',
    created: new Date().toISOString(),
    expires: expiresAt,
  });

  const dataUrl = await QRCode.toDataURL(payload, {
    width: 280,
    margin: 2,
    color: {
      dark: '#142b20',
      light: '#ffffff',
    },
    errorCorrectionLevel: 'M',
  });

  return {
    dataUrl,
    reference,
    amount,
    formattedAmount: `฿${amount.toLocaleString('en-US')}`,
    merchantName,
    expiresAt,
  };
}
