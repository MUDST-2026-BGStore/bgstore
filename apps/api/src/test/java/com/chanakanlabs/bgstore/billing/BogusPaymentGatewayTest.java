package com.chanakanlabs.bgstore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.bgstore.billing.PaymentGateway.CardDetails;
import com.chanakanlabs.bgstore.billing.PaymentGateway.Failure;
import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentRequest;
import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentResult;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BogusPaymentGatewayTest {

  private static final Instant NOW = Instant.parse("2026-09-22T06:00:00Z");

  private final BogusPaymentGateway gateway =
      new BogusPaymentGateway(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void supportsEveryMethodExceptWaived() {
    assertThat(gateway.supports(PaymentMethod.CASH)).isTrue();
    assertThat(gateway.supports(PaymentMethod.PROMPT_PAY)).isTrue();
    assertThat(gateway.supports(PaymentMethod.BANK_TRANSFER)).isTrue();
    assertThat(gateway.supports(PaymentMethod.CARD)).isTrue();
    // A waived fee closes the session without charging, so no gateway may claim it.
    assertThat(gateway.supports(PaymentMethod.WAIVED)).isFalse();
  }

  @Test
  void chargeAlwaysSucceedsWithABogusReference() {
    PaymentResult first = gateway.charge(request(PaymentMethod.PROMPT_PAY, null));
    PaymentResult second = gateway.charge(request(PaymentMethod.PROMPT_PAY, null));

    assertThat(first.succeeded()).isTrue();
    assertThat(first.gateway()).isEqualTo("bogus");
    assertThat(first.reference()).startsWith("bogus-");
    // Every charge gets its own reference so receipts stay distinguishable.
    assertThat(first.reference()).isNotEqualTo(second.reference());
    assertThat(first.declineReason()).isNull();
    assertThat(first.cardBrand()).isNull();
  }

  @Test
  void chargeCardApprovesALuhnValidNumberAndReportsBrandAndLast4() {
    PaymentResult result =
        gateway.charge(request(PaymentMethod.CARD, card("4242 4242 4242 4242", "424", "12/29")));

    assertThat(result.succeeded()).isTrue();
    assertThat(result.reference()).startsWith("bogus-");
    assertThat(result.cardBrand()).isEqualTo("Visa");
    assertThat(result.cardLast4()).isEqualTo("4242");
    assertThat(result.declineReason()).isNull();
  }

  @Test
  void chargeCardRejectsANumberThatFailsTheLuhnChecksum() {
    // One changed digit turns the real Visa test number into a Luhn failure.
    PaymentResult result =
        gateway.charge(request(PaymentMethod.CARD, card("4242424242424241", "424", "12/29")));

    assertThat(result.succeeded()).isFalse();
    assertThat(result.declineReason()).isEqualTo("invalid_card_number");
    assertThat(result.failure()).isEqualTo(Failure.CARD_DECLINED);
  }

  @Test
  void chargeCardRejectsNumbersOutsideThePrintedLengths() {
    PaymentResult shortNumber =
        gateway.charge(request(PaymentMethod.CARD, card("422222222222", "424", "12/29")));
    PaymentResult longNumber =
        gateway.charge(request(PaymentMethod.CARD, card("42424242424242420060", "424", "12/29")));

    assertThat(shortNumber.declineReason()).isEqualTo("invalid_card_number");
    assertThat(longNumber.declineReason()).isEqualTo("invalid_card_number");
  }

  @Test
  void chargeCardApprovesTheShortestPrintedLength() {
    // 4222222222222 is the classic 13-digit Visa test number.
    PaymentResult result =
        gateway.charge(request(PaymentMethod.CARD, card("4222222222222", "424", "12/29")));

    assertThat(result.succeeded()).isTrue();
    assertThat(result.cardBrand()).isEqualTo("Visa");
    assertThat(result.cardLast4()).isEqualTo("2222");
  }

  @Test
  void chargeCardDetectsTheBrandFromTheLeadingDigits() {
    assertThat(brandOf("5555555555554444", "424")).isEqualTo("Mastercard");
    assertThat(brandOf("378282246310005", "1234")).isEqualTo("Amex");
    assertThat(brandOf("3566002020360505", "424")).isEqualTo("JCB");
    assertThat(brandOf("30569309025904", "424")).isEqualTo("DinersClub");
    assertThat(brandOf("6011111111111117", "424")).isEqualTo("Discover");
    assertThat(brandOf("6200000000000005", "424")).isEqualTo("UnionPay");
    // A card outside every known issuer range still charges, as Unknown.
    assertThat(brandOf("9999999999999995", "424")).isEqualTo("Unknown");
  }

  @Test
  void chargeCardWantsFourCvvDigitsOnAmexAndThreeElsewhere() {
    PaymentResult amexWithThree =
        gateway.charge(request(PaymentMethod.CARD, card("378282246310005", "123", "12/29")));
    PaymentResult amexWithFour =
        gateway.charge(request(PaymentMethod.CARD, card("378282246310005", "1234", "12/29")));
    PaymentResult visaWithFour =
        gateway.charge(request(PaymentMethod.CARD, card("4242424242424242", "1234", "12/29")));

    assertThat(amexWithThree.declineReason()).isEqualTo("invalid_cvv");
    assertThat(amexWithFour.succeeded()).isTrue();
    assertThat(visaWithFour.declineReason()).isEqualTo("invalid_cvv");
  }

  @Test
  void chargeCardRejectsAnExpiredCard() {
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "424", "08/26")))
                .declineReason())
        .isEqualTo("card_expired");
    // A card is chargeable through its expiry month, not the day before it.
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "424", "09/26")))
                .succeeded())
        .isTrue();
  }

  @Test
  void chargeCardRejectsAMalformedExpiry() {
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "424", "13/29")))
                .declineReason())
        .isEqualTo("invalid_expiry");
  }

  @Test
  void theCvvDigitsScriptTheCounterOutcome() {
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "111", "12/29")))
                .declineReason())
        .isEqualTo("insufficient_funds");
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "222", "12/29")))
                .declineReason())
        .isEqualTo("stolen_card");
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "333", "12/29")))
                .declineReason())
        .isEqualTo("cvv_mismatch");
    assertThat(
            gateway
                .charge(request(PaymentMethod.CARD, card("4242424242424242", "424", "12/29")))
                .succeeded())
        .isTrue();
  }

  @Test
  void aRepeatedNineCvvSimulatesAGatewayErrorRatherThanACardDecline() {
    PaymentResult result =
        gateway.charge(request(PaymentMethod.CARD, card("4242424242424242", "999", "12/29")));

    assertThat(result.succeeded()).isFalse();
    assertThat(result.failure()).isEqualTo(Failure.GATEWAY_ERROR);
    assertThat(result.declineReason()).isEqualTo("gateway_unavailable");
  }

  @Test
  void aCardChargeWithoutCardDetailsIsDeclined() {
    PaymentResult result = gateway.charge(request(PaymentMethod.CARD, null));

    assertThat(result.succeeded()).isFalse();
    assertThat(result.declineReason()).isEqualTo("invalid_card_number");
  }

  private PaymentRequest request(PaymentMethod method, CardDetails card) {
    return new PaymentRequest("res-1", 240, method, card);
  }

  /** A printed card as the counter would key it in. */
  private static CardDetails card(String number, String cvv, String expiry) {
    String[] parts = expiry.split("/");
    return new CardDetails(
        number, cvv, Integer.valueOf(parts[0]), Integer.valueOf("20" + parts[1]));
  }

  private String brandOf(String number, String cvv) {
    return gateway.charge(request(PaymentMethod.CARD, card(number, cvv, "12/29"))).cardBrand();
  }
}
