package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import org.jspecify.annotations.Nullable;

/**
 * The seam a payment provider plugs into. Each gateway adapter declares which payment methods it
 * can process and how a confirmed fee is charged; the {@link BogusPaymentGateway} is the default
 * stand-in so the reservation → play → billing flow runs before a real PromptPay or bank adapter
 * exists. Adding a real gateway means adding an adapter bean, not changing call sites.
 */
public interface PaymentGateway {

  /** Whether this gateway can charge the given payment method. */
  boolean supports(PaymentMethod method);

  /** Charges the confirmed fee and reports what the gateway did with it. */
  PaymentResult charge(PaymentRequest request);

  /** The card a guest hands over the counter for a {@code Card} charge. */
  record CardDetails(
      String number, String cvv, @Nullable Integer expiryMonth, @Nullable Integer expiryYear) {}

  /** A confirmed fee a caller wants settled. */
  record PaymentRequest(
      String reservationId, int amount, PaymentMethod method, @Nullable CardDetails card) {}

  /**
   * Why a charge failed: the card itself was rejected, or the gateway could not process the charge
   * at all (a retry may succeed).
   */
  enum Failure {
    CARD_DECLINED,
    GATEWAY_ERROR
  }

  /**
   * What a gateway reports after attempting a charge. A declined charge carries a failure kind, a
   * reason and no reference; a succeeded charge carries the gateway's transaction reference, and
   * for card charges the detected brand and the number's last four digits.
   */
  record PaymentResult(
      boolean succeeded,
      String gateway,
      @Nullable String reference,
      @Nullable String declineReason,
      @Nullable Failure failure,
      @Nullable String cardBrand,
      @Nullable String cardLast4) {

    public static PaymentResult succeeded(String gateway, String reference) {
      return new PaymentResult(true, gateway, reference, null, null, null, null);
    }

    public static PaymentResult succeeded(
        String gateway, String reference, String cardBrand, String cardLast4) {
      return new PaymentResult(true, gateway, reference, null, null, cardBrand, cardLast4);
    }

    public static PaymentResult declined(String gateway, Failure failure, String declineReason) {
      return new PaymentResult(false, gateway, null, declineReason, failure, null, null);
    }
  }
}
