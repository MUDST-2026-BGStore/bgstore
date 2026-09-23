package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A fake payment gateway in the style of a "Bogus Gateway". PromptPay, cash and bank-transfer
 * charges are approved instantly. Card charges simulate a real card system: any card number that
 * passes the Luhn checksum (13–19 digits) is chargeable, its {@link CardNetwork} is detected from
 * the leading digits, an expired card is declined, and the security code doubles as the counter's
 * outcome knob — repeated digits script a failure (111 insufficient funds, 222 stolen card, 333
 * wrong security code, 999 a gateway error); any other valid code approves. It exists so the full
 * billing flow is testable end to end before a real adapter is written: it implements the same
 * {@link PaymentGateway} seam a real adapter will use, so the initial implementation swaps out
 * rather than rewires.
 */
@Component
public class BogusPaymentGateway implements PaymentGateway {

  public static final String NAME = "bogus";

  private final Clock clock;

  public BogusPaymentGateway(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean supports(PaymentMethod method) {
    return method != PaymentMethod.WAIVED;
  }

  @Override
  public PaymentResult charge(PaymentRequest request) {
    if (request.method() == PaymentMethod.CARD) {
      return chargeCard(request.card());
    }
    return PaymentResult.succeeded(NAME, reference());
  }

  private PaymentResult chargeCard(@Nullable CardDetails card) {
    if (card == null) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "invalid_card_number");
    }
    String digits = card.number() == null ? "" : card.number().replaceAll("\\D", "");
    if (digits.length() < 13 || digits.length() > 19 || !luhnValid(digits)) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "invalid_card_number");
    }
    CardNetwork network = CardNetwork.detect(digits);
    String cvv = card.cvv() == null ? "" : card.cvv();
    if (!cvv.matches("\\d{%d}".formatted(network.cvvLength()))) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "invalid_cvv");
    }
    if (card.expiryMonth() == null
        || card.expiryMonth() < 1
        || card.expiryMonth() > 12
        || card.expiryYear() == null) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "invalid_expiry");
    }
    if (YearMonth.of(card.expiryYear(), card.expiryMonth()).isBefore(YearMonth.now(clock))) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "card_expired");
    }
    if (sameDigit(cvv, '1')) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "insufficient_funds");
    }
    if (sameDigit(cvv, '2')) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "stolen_card");
    }
    if (sameDigit(cvv, '3')) {
      return PaymentResult.declined(NAME, Failure.CARD_DECLINED, "cvv_mismatch");
    }
    if (sameDigit(cvv, '9')) {
      return PaymentResult.declined(NAME, Failure.GATEWAY_ERROR, "gateway_unavailable");
    }
    return PaymentResult.succeeded(NAME, reference(), network.value(), last4(digits));
  }

  private String reference() {
    return "bogus-" + UUID.randomUUID();
  }

  /** The last four digits of a card number, the only part worth recording. */
  private static String last4(String digits) {
    return digits.substring(digits.length() - 4);
  }

  /** The Luhn checksum every real card network prints into its card numbers. */
  private static boolean luhnValid(String digits) {
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int value = digits.charAt(i) - '0';
      if (doubled) {
        value *= 2;
        if (value > 9) {
          value -= 9;
        }
      }
      sum += value;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }

  /** Whether every character of the value is the same digit. */
  private static boolean sameDigit(String value, char digit) {
    return !value.isEmpty() && value.chars().allMatch(c -> c == digit);
  }
}
