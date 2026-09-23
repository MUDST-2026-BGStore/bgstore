package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A fake payment gateway that approves every non-waived charge, in the style of a "Bogus Gateway".
 * It exists so the full billing flow is testable end to end before a real PromptPay or bank adapter
 * is written: it implements the same {@link PaymentGateway} seam a real adapter will use, so the
 * initial implementation swaps out rather than rewires.
 */
@Component
public class BogusPaymentGateway implements PaymentGateway {

  public static final String NAME = "bogus";

  @Override
  public boolean supports(PaymentMethod method) {
    return method != PaymentMethod.WAIVED;
  }

  @Override
  public PaymentResult charge(PaymentRequest request) {
    return PaymentResult.succeeded(NAME, "bogus-" + UUID.randomUUID());
  }
}
