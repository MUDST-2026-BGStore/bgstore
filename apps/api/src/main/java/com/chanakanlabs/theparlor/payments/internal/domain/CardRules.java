package com.chanakanlabs.theparlor.payments.internal.domain;

import java.time.YearMonth;

/**
 * Card checks that need no gateway: digits, Luhn checksum, brand, expiry and security code. Failure
 * messages never contain card data.
 */
public final class CardRules {
  private CardRules() {}

  /** Strips the spaces and dashes people type, and requires 12 to 19 digits. */
  public static String digits(String number) {
    if (number == null) throw new IllegalArgumentException("Card number is required");
    String digits = number.replace(" ", "").replace("-", "");
    if (!digits.matches("[0-9]{12,19}"))
      throw new IllegalArgumentException("Card number must be 12 to 19 digits");
    return digits;
  }

  /** Luhn mod-10: double every second digit from the right and sum the digits. */
  public static boolean passesLuhn(String digits) {
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubled) {
        digit *= 2;
        if (digit > 9) digit -= 9;
      }
      sum += digit;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }

  /** Brand by issuer prefix and length; unsupported networks are rejected. */
  public static CardBrand brand(String digits) {
    int length = digits.length();
    int two = Integer.parseInt(digits, 0, 2, 10);
    int four = Integer.parseInt(digits, 0, 4, 10);
    if (digits.charAt(0) == '4' && (length == 13 || length == 16 || length == 19))
      return CardBrand.VISA;
    if (length == 16 && ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)))
      return CardBrand.MASTERCARD;
    if (length == 15 && (two == 34 || two == 37)) return CardBrand.AMEX;
    if (length >= 16 && four >= 3528 && four <= 3589) return CardBrand.JCB;
    if (length >= 16 && two == 62) return CardBrand.UNIONPAY;
    throw new IllegalArgumentException("Card network is not supported");
  }

  /** Validates a whole card as of the current month and returns its brand. */
  public static CardBrand validate(
      String digits, int expMonth, int expYear, String cvc, YearMonth now) {
    if (!passesLuhn(digits)) throw new IllegalArgumentException("Card number is not valid");
    CardBrand brand = brand(digits);
    if (expMonth < 1 || expMonth > 12) throw new IllegalArgumentException("Invalid expiry month");
    if (YearMonth.of(expYear, expMonth).isBefore(now))
      throw new IllegalArgumentException("Card has expired");
    int cvcLength = brand == CardBrand.AMEX ? 4 : 3;
    if (cvc == null || !cvc.matches("[0-9]{" + cvcLength + "}"))
      throw new IllegalArgumentException("Security code must be " + cvcLength + " digits");
    return brand;
  }
}
