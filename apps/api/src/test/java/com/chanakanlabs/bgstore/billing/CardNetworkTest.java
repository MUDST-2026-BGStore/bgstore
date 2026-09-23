package com.chanakanlabs.bgstore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardNetworkTest {

  @Test
  void detectsEachNetworkFromItsIssuerRanges() {
    assertThat(CardNetwork.detect("4111111111111111")).isEqualTo(CardNetwork.VISA);
    assertThat(CardNetwork.detect("51")).isEqualTo(CardNetwork.MASTERCARD);
    assertThat(CardNetwork.detect("5500005555555559")).isEqualTo(CardNetwork.MASTERCARD);
    assertThat(CardNetwork.detect("2221000000000000")).isEqualTo(CardNetwork.MASTERCARD);
    assertThat(CardNetwork.detect("2720999999999999")).isEqualTo(CardNetwork.MASTERCARD);
    assertThat(CardNetwork.detect("34")).isEqualTo(CardNetwork.AMEX);
    assertThat(CardNetwork.detect("378282246310005")).isEqualTo(CardNetwork.AMEX);
    assertThat(CardNetwork.detect("3528000000000000")).isEqualTo(CardNetwork.JCB);
    assertThat(CardNetwork.detect("3589000000000000")).isEqualTo(CardNetwork.JCB);
    assertThat(CardNetwork.detect("300000000000000")).isEqualTo(CardNetwork.DINERS_CLUB);
    assertThat(CardNetwork.detect("30569309025904")).isEqualTo(CardNetwork.DINERS_CLUB);
    assertThat(CardNetwork.detect("36148900647913")).isEqualTo(CardNetwork.DINERS_CLUB);
    assertThat(CardNetwork.detect("38520000023237")).isEqualTo(CardNetwork.DINERS_CLUB);
    assertThat(CardNetwork.detect("6011111111111117")).isEqualTo(CardNetwork.DISCOVER);
    assertThat(CardNetwork.detect("6440000000000005")).isEqualTo(CardNetwork.DISCOVER);
    assertThat(CardNetwork.detect("6499990000000000")).isEqualTo(CardNetwork.DISCOVER);
    assertThat(CardNetwork.detect("6500000000000003")).isEqualTo(CardNetwork.DISCOVER);
    assertThat(CardNetwork.detect("6200000000000005")).isEqualTo(CardNetwork.UNION_PAY);
  }

  @Test
  void aNumberOutsideEveryIssuerRangeIsUnknown() {
    // Just below and above each range's edges, plus a bin no network claims.
    assertThat(CardNetwork.detect("50")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("56")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("2220999999999999")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("2721")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("3527")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("3590")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("299")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("306")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("6012")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("61")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("7210928749182")).isEqualTo(CardNetwork.UNKNOWN);
  }

  @Test
  void aShortPrefixStaysUnknownRatherThanThrowing() {
    assertThat(CardNetwork.detect("")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("2")).isEqualTo(CardNetwork.UNKNOWN);
    assertThat(CardNetwork.detect("6")).isEqualTo(CardNetwork.UNKNOWN);
  }

  @Test
  void amexPrintsFourCvvDigitsAndTheOthersPrintThree() {
    assertThat(CardNetwork.AMEX.cvvLength()).isEqualTo(4);
    assertThat(CardNetwork.VISA.cvvLength()).isEqualTo(3);
    assertThat(CardNetwork.MASTERCARD.cvvLength()).isEqualTo(3);
    assertThat(CardNetwork.UNKNOWN.cvvLength()).isEqualTo(3);
  }

  @Test
  void valuesMatchTheContractBrandNames() {
    assertThat(CardNetwork.VISA.value()).isEqualTo("Visa");
    assertThat(CardNetwork.MASTERCARD.value()).isEqualTo("Mastercard");
    assertThat(CardNetwork.AMEX.value()).isEqualTo("Amex");
    assertThat(CardNetwork.JCB.value()).isEqualTo("JCB");
    assertThat(CardNetwork.DINERS_CLUB.value()).isEqualTo("DinersClub");
    assertThat(CardNetwork.DISCOVER.value()).isEqualTo("Discover");
    assertThat(CardNetwork.UNION_PAY.value()).isEqualTo("UnionPay");
    assertThat(CardNetwork.UNKNOWN.value()).isEqualTo("Unknown");
  }
}
