import { describe, expect, it } from 'vitest';
import {
  cardInputReady,
  cardNumberValid,
  detectCardBrand,
  formatCardNumber,
  luhnValid,
  parseCardExpiry,
} from './card-payment';

describe('luhnValid', () => {
  it('accepts the checksum every network prints on real cards', () => {
    expect(luhnValid('4242424242424242')).toBe(true);
    expect(luhnValid('4222222222222')).toBe(true);
  });

  it('rejects a number one digit away from the checksum', () => {
    expect(luhnValid('4242424242424241')).toBe(false);
  });
});

describe('cardNumberValid', () => {
  it('accepts the 13–19 digit chargeable length', () => {
    expect(cardNumberValid('4222222222222')).toBe(true);
    expect(cardNumberValid('4242424242424242')).toBe(true);
    expect(cardNumberValid('4242424242424242006')).toBe(true);
  });

  it('rejects short, long, and checksum-failing numbers', () => {
    expect(cardNumberValid('42424242424')).toBe(false);
    expect(cardNumberValid('42424242424242420060')).toBe(false);
    expect(cardNumberValid('4242424242424241')).toBe(false);
    expect(cardNumberValid('')).toBe(false);
  });
});

describe('detectCardBrand', () => {
  it('detects each network in the gateway’s IIN table', () => {
    expect(detectCardBrand('4242424242424242')).toBe('Visa');
    expect(detectCardBrand('5555555555554444')).toBe('Mastercard');
    expect(detectCardBrand('378282246310005')).toBe('Amex');
    expect(detectCardBrand('3566002020360505')).toBe('JCB');
    expect(detectCardBrand('30569309025904')).toBe('DinersClub');
    expect(detectCardBrand('6011111111111117')).toBe('Discover');
    expect(detectCardBrand('6200000000000005')).toBe('UnionPay');
    expect(detectCardBrand('9999999999999995')).toBe('Unknown');
  });

  it('honors the inclusive range edges on both sides', () => {
    expect(detectCardBrand('2221000000000000')).toBe('Mastercard');
    expect(detectCardBrand('2220000000000000')).toBe('Unknown');
    expect(detectCardBrand('2720000000000000')).toBe('Mastercard');
    expect(detectCardBrand('2721000000000000')).toBe('Unknown');
    expect(detectCardBrand('5100000000000000')).toBe('Mastercard');
    expect(detectCardBrand('5000000000000000')).toBe('Unknown');
    expect(detectCardBrand('5600000000000000')).toBe('Unknown');
    expect(detectCardBrand('3528000000000000')).toBe('JCB');
    expect(detectCardBrand('3527000000000000')).toBe('Unknown');
    expect(detectCardBrand('3590000000000000')).toBe('Unknown');
    expect(detectCardBrand('3050000000000')).toBe('DinersClub');
    expect(detectCardBrand('3060000000000')).toBe('Unknown');
    expect(detectCardBrand('6445000000000000')).toBe('Discover');
    expect(detectCardBrand('6439000000000000')).toBe('Unknown');
    expect(detectCardBrand('6490000000000000')).toBe('Discover');
    expect(detectCardBrand('6500000000000000')).toBe('Discover');
  });
});

describe('formatCardNumber', () => {
  it('prints 4-4-4-4 groups', () => {
    expect(formatCardNumber('4242424242424242')).toBe('4242 4242 4242 4242');
  });

  it('prints Amex 4-6-5 groups', () => {
    expect(formatCardNumber('378282246310005')).toBe('3782 822463 10005');
  });

  it('keeps punctuation out and stops at 19 digits', () => {
    expect(formatCardNumber('4242-4242-4242-4242')).toBe('4242 4242 4242 4242');
    // The printed field caps at the chargeable 19 digits.
    expect(formatCardNumber('42424242424242420060')).toBe(
      '4242 4242 4242 4242 006',
    );
  });
});

describe('parseCardExpiry', () => {
  it('reads MM/YY and loose variants into month and full year', () => {
    expect(parseCardExpiry('12/29')).toEqual({ month: 12, year: 2029 });
    expect(parseCardExpiry('12 29')).toEqual({ month: 12, year: 2029 });
    expect(parseCardExpiry('0129')).toEqual({ month: 1, year: 2029 });
  });

  it('rejects months and shapes no real card prints', () => {
    expect(parseCardExpiry('13/29')).toBeNull();
    expect(parseCardExpiry('00/29')).toBeNull();
    expect(parseCardExpiry('1/29')).toBeNull();
    expect(parseCardExpiry('12/2')).toBeNull();
    expect(parseCardExpiry('12/2029')).toBeNull();
    expect(parseCardExpiry('soon')).toBeNull();
  });
});

describe('cardInputReady', () => {
  const base = {
    number: '4242 4242 4242 4242',
    cvv: '123',
    expiry: '12/29',
  };

  it('builds the contract payload from a complete entry', () => {
    expect(cardInputReady(base)).toEqual({
      number: '4242424242424242',
      cvv: '123',
      expiryMonth: 12,
      expiryYear: 2029,
    });
  });

  it('wants four security digits from Amex and three elsewhere', () => {
    const amex = { number: '378282246310005', cvv: '1234', expiry: '12/29' };
    expect(cardInputReady(amex)).toEqual({
      number: '378282246310005',
      cvv: '1234',
      expiryMonth: 12,
      expiryYear: 2029,
    });
    expect(cardInputReady({ ...amex, cvv: '123' })).toBeNull();
    expect(cardInputReady({ ...base, cvv: '1234' })).toBeNull();
  });

  it('refuses incomplete or checksum-failing entries', () => {
    expect(cardInputReady({ ...base, cvv: '' })).toBeNull();
    expect(cardInputReady({ ...base, cvv: '12a' })).toBeNull();
    expect(cardInputReady({ ...base, number: '4242424242424241' })).toBeNull();
    expect(cardInputReady({ ...base, expiry: '13/29' })).toBeNull();
    expect(cardInputReady({ ...base, expiry: '' })).toBeNull();
  });
});
