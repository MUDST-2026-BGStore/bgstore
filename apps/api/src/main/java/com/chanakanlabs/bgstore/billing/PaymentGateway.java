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

  /** A confirmed fee a caller wants settled. */
  record PaymentRequest(String reservationId, int amount, PaymentMethod method) {}

  /**
   * What a gateway reports after attempting a charge. A declined charge carries a reason and no
   * reference; a succeeded charge carries the gateway's transaction reference.
   */
  record PaymentResult(
      boolean succeeded,
      String gateway,
      @Nullable String reference,
      @Nullable String declineReason) {

    public static PaymentResult succeeded(String gateway, String reference) {
      return new PaymentResult(true, gateway, reference, null);
    }

    public static PaymentResult declined(String gateway, String declineReason) {
      return new PaymentResult(false, gateway, null, declineReason);
    }
  }
}
