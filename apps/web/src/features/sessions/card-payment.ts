import type { CardBrand, CardInput } from '../../generated/api/types.gen';

import visaLogo from '../../assets/card-networks/visa.png';
import mastercardLogo from '../../assets/card-networks/mastercard.png';
import amexLogo from '../../assets/card-networks/amex.png';
import jcbLogo from '../../assets/card-networks/jcb.png';
import dinersClubLogo from '../../assets/card-networks/diners-club.png';
import discoverLogo from '../../assets/card-networks/discover.png';
import unionPayLogo from '../../assets/card-networks/union-pay.png';
import unknownLogo from '../../assets/card-networks/unknown.png';

/**
 * Card-entry helpers for the bogus gateway's demo card system.
 *
 * These mirror the gateway rules on the API side: any number that passes the
 * Luhn checksum (13–19 digits) is chargeable, the network is detected from the
 * leading digits, and the security code is the outcome knob. The full number
 * never leaves the counter's form except as the charge request body.
 */

/** Keeps only the printed digits of a card number. */
export const cardDigits = (number: string): string => number.replace(/\D/g, '');

/** Luhn checksum, as used by every real card network. */
export const luhnValid = (digits: string): boolean => {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let value = Number(digits[i]);
    if (double) {
      value *= 2;
      if (value > 9) {
        value -= 9;
      }
    }
    sum += value;
    double = !double;
  }
  return sum % 10 === 0;
};

/** True when the digits have a chargeable length and pass the checksum. */
export const cardNumberValid = (digits: string): boolean =>
  digits.length >= 13 && digits.length <= 19 && luhnValid(digits);

/**
 * The card network from the leading digits, matching the gateway's IIN table.
 * An unknown range still charges — as `Unknown`.
 */
export const detectCardBrand = (number: string): CardBrand => {
  const d = cardDigits(number).slice(0, 4);
  const starts = (prefix: string) => d.startsWith(prefix);
  // Mirrors the gateway's rule: compare only as many digits as the range
  // bounds have, so `3056…` matches Diners' 300–305 and `6445…` matches
  // Discover's 644–649. A shorter input never falls inside a range.
  const inRange = (low: number, high: number) => {
    const prefixLength = String(low).length;
    if (d.length < prefixLength) {
      return false;
    }
    const value = Number(d.slice(0, prefixLength));
    return value >= low && value <= high;
  };

  if (starts('4')) {
    return 'Visa';
  }
  if (inRange(2221, 2720) || (Number(d.slice(0, 2)) >= 51 && Number(d.slice(0, 2)) <= 55)) {
    return 'Mastercard';
  }
  if (starts('34') || starts('37')) {
    return 'Amex';
  }
  if (inRange(3528, 3589)) {
    return 'JCB';
  }
  if (inRange(300, 305) || starts('36') || starts('38')) {
    return 'DinersClub';
  }
  if (starts('6011') || inRange(644, 649) || starts('65')) {
    return 'Discover';
  }
  if (starts('62')) {
    return 'UnionPay';
  }
  return 'Unknown';
};

/** Card network logos for the detected brand, keyed exactly like `CardBrand`. */
export const cardBrandLogos: Record<CardBrand, string> = {
  Visa: visaLogo,
  Mastercard: mastercardLogo,
  Amex: amexLogo,
  JCB: jcbLogo,
  DinersClub: dinersClubLogo,
  Discover: discoverLogo,
  UnionPay: unionPayLogo,
  Unknown: unknownLogo,
};

/** Printed grouping: Amex 4-6-5, everything else 4-4-4-4…, at most 19 digits. */
export const formatCardNumber = (number: string): string => {
  const digits = cardDigits(number).slice(0, 19);
  if (detectCardBrand(number) === 'Amex') {
    return [digits.slice(0, 4), digits.slice(4, 10), digits.slice(10, 15)]
      .filter((part) => part.length > 0)
      .join(' ');
  }
  return digits.replace(/(.{4})/g, '$1 ').trim();
};

/** Parses a printed `MM/YY` expiry into the API's month + full year. */
export const parseCardExpiry = (expiry: string): { month: number; year: number } | null => {
  const match = /^(\d{2})\s*\/?\s*(\d{2})$/.exec(expiry.trim());
  if (!match) {
    return null;
  }
  const month = Number(match[1]);
  if (month < 1 || month > 12) {
    return null;
  }
  return { month, year: 2000 + Number(match[2]) };
};

/**
 * Everything the gateway needs before a card charge may be submitted. The
 * expiry must not already have passed — the gateway declines expired cards,
 * so refusing them earlier reads better at the counter.
 */
export const cardInputReady = (input: {
  number: string;
  cvv: string;
  expiry: string;
}): CardInput | null => {
  const digits = cardDigits(input.number);
  const expiry = parseCardExpiry(input.expiry);
  if (!cardNumberValid(digits) || expiry === null) {
    return null;
  }
  const expectedCvvLength = detectCardBrand(input.number) === 'Amex' ? 4 : 3;
  if (!/^\d+$/.test(input.cvv.trim()) || input.cvv.trim().length !== expectedCvvLength) {
    return null;
  }
  return {
    number: digits,
    cvv: input.cvv.trim(),
    expiryMonth: expiry.month,
    expiryYear: expiry.year,
  };
};
