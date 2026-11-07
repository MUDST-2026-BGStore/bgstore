import { describe, expect, it } from 'vitest';
import { calculatePreOrderDiscount, generateReservationQrCode } from './payment';

describe('Reservation Payment & Discount Logic', () => {
  it('applies a 10% discount on food & drinks pre-orders', () => {
    const calculation = calculatePreOrderDiscount(500);
    expect(calculation.discountRate).toBe(0.10);
    expect(calculation.discountAmount).toBe(50);
    expect(calculation.discountedTotal).toBe(450);
  });

  it('handles zero food order correctly', () => {
    const calculation = calculatePreOrderDiscount(0);
    expect(calculation.discountAmount).toBe(0);
    expect(calculation.discountedTotal).toBe(0);
  });

  it('rounds discounts accurately for fractional totals', () => {
    const calculation = calculatePreOrderDiscount(125);
    expect(calculation.discountAmount).toBe(13); // Math.round(12.5)
    expect(calculation.discountedTotal).toBe(112);
  });

  it('generates a valid scannable PromptPay QR code data URL', async () => {
    const qr = await generateReservationQrCode('TEST-REF-123', 650);
    expect(qr.reference).toBe('TEST-REF-123');
    expect(qr.amount).toBe(650);
    expect(qr.formattedAmount).toBe('฿650');
    expect(qr.dataUrl).toMatch(/^data:image\/png;base64,/);
    expect(new Date(qr.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });
});

