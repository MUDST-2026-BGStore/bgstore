package com.chanakanlabs.theparlor.payments.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CardRulesTests {
  static final YearMonth NOW = YearMonth.of(2026, 11);

  @ParameterizedTest
  @CsvSource({
    "4242424242424242,VISA,123",
    "4000000000000002,VISA,123",
    "5555555555554444,MASTERCARD,123",
    "2223003122003222,MASTERCARD,123",
    "378282246310005,AMEX,1234",
    "3566002020360505,JCB,123",
    "6200000000000005,UNIONPAY,123"
  })
  void acceptsValidCardsAndDetectsTheirBrand(String number, CardBrand brand, String cvc) {
    assertThat(CardRules.validate(CardRules.digits(number), 11, 2026, cvc, NOW)).isEqualTo(brand);
  }

  @Test
  void ignoresTheSeparatorsPeopleType() {
    assertThat(CardRules.digits("4242 4242-4242 4242")).isEqualTo("4242424242424242");
  }

  @ParameterizedTest
  @ValueSource(strings = {"4242424242424241", "4242424242424243", "5555555555554445"})
  void rejectsNumbersFailingTheLuhnChecksum(String number) {
    assertThat(CardRules.passesLuhn(number)).isFalse();
    assertThatThrownBy(() -> CardRules.validate(number, 12, 2030, "123", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining(number);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "42424242", "4242424242424242424242", "4242x24242424242"})
  void rejectsNumbersThatAreNotTwelveToNineteenDigits(String number) {
    assertThatThrownBy(() -> CardRules.digits(number)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsExpiredCardsWrongCodeLengthsAndUnsupportedNetworks() {
    String visa = "4242424242424242";
    assertThatThrownBy(() -> CardRules.validate(visa, 10, 2026, "123", NOW))
        .hasMessageContaining("expired");
    assertThatThrownBy(() -> CardRules.validate(visa, 13, 2030, "123", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CardRules.validate(visa, 12, 2030, "1234", NOW))
        .hasMessageContaining("Security code");
    assertThatThrownBy(() -> CardRules.validate("378282246310005", 12, 2030, "123", NOW))
        .hasMessageContaining("Security code");
    // Luhn-valid Diners Club number: a network this shop does not accept.
    assertThatThrownBy(() -> CardRules.validate("30569309025904", 12, 2030, "123", NOW))
        .hasMessageContaining("not supported");
  }
}
