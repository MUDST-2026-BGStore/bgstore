package com.chanakanlabs.bgstore.billing;

/**
 * The card network a charge is routed through, detected from a card number's issuer identification
 * number (its leading digits). The table covers the networks printed on cards a Bangkok counter
 * plausibly sees; anything else reports {@link #UNKNOWN} and the bogus gateway still charges it.
 */
public enum CardNetwork {
  VISA("Visa"),
  MASTERCARD("Mastercard"),
  AMEX("Amex"),
  JCB("JCB"),
  DINERS_CLUB("DinersClub"),
  DISCOVER("Discover"),
  UNION_PAY("UnionPay"),
  UNKNOWN("Unknown");

  private final String value;

  CardNetwork(String value) {
    this.value = value;
  }

  /** The contract-facing brand name. */
  public String value() {
    return value;
  }

  /** How many security-code digits cards of this network print on the back (or front). */
  public int cvvLength() {
    return this == AMEX ? 4 : 3;
  }

  /** Detects the network from a card number's digits alone. */
  public static CardNetwork detect(String digits) {
    if (digits.startsWith("4")) {
      return VISA;
    }
    if (inRange(digits, 51, 55) || inRange(digits, 2221, 2720)) {
      return MASTERCARD;
    }
    if (digits.startsWith("34") || digits.startsWith("37")) {
      return AMEX;
    }
    if (inRange(digits, 3528, 3589)) {
      return JCB;
    }
    if (inRange(digits, 300, 305) || digits.startsWith("36") || digits.startsWith("38")) {
      return DINERS_CLUB;
    }
    if (digits.startsWith("6011") || inRange(digits, 644, 649) || digits.startsWith("65")) {
      return DISCOVER;
    }
    if (digits.startsWith("62")) {
      return UNION_PAY;
    }
    return UNKNOWN;
  }

  /** Whether the number's first digits fall in an inclusive IIN range. */
  private static boolean inRange(String digits, int low, int high) {
    int prefixLength = String.valueOf(low).length();
    if (digits.length() < prefixLength) {
      return false;
    }
    int prefix = Integer.parseInt(digits.substring(0, prefixLength));
    return prefix >= low && prefix <= high;
  }
}
