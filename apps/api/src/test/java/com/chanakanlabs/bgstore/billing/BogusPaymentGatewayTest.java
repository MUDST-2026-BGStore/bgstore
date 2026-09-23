package com.chanakanlabs.bgstore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentResult;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import org.junit.jupiter.api.Test;

class BogusPaymentGatewayTest {

  private final BogusPaymentGateway gateway = new BogusPaymentGateway();

  @Test
  void supportsEveryMethodExceptWaived() {
    assertThat(gateway.supports(PaymentMethod.CASH)).isTrue();
    assertThat(gateway.supports(PaymentMethod.PROMPT_PAY)).isTrue();
    assertThat(gateway.supports(PaymentMethod.BANK_TRANSFER)).isTrue();
    // A waived fee closes the session without charging, so no gateway may claim it.
    assertThat(gateway.supports(PaymentMethod.WAIVED)).isFalse();
  }

  @Test
  void chargeAlwaysSucceedsWithABogusReference() {
    PaymentResult first =
        gateway.charge(new PaymentGateway.PaymentRequest("res-1", 240, PaymentMethod.PROMPT_PAY));
    PaymentResult second =
        gateway.charge(new PaymentGateway.PaymentRequest("res-1", 240, PaymentMethod.PROMPT_PAY));

    assertThat(first.succeeded()).isTrue();
    assertThat(first.gateway()).isEqualTo("bogus");
    assertThat(first.reference()).startsWith("bogus-");
    // Every charge gets its own reference so receipts stay distinguishable.
    assertThat(first.reference()).isNotEqualTo(second.reference());
    assertThat(first.declineReason()).isNull();
  }
}
