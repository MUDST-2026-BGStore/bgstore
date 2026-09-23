package com.chanakanlabs.bgstore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentRequest;
import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentResult;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-22T06:00:00Z");

  @Mock private JpaPaymentRepository payments;

  @Test
  void settleRoutesTheChargeToTheFirstGatewaySupportingTheMethod() {
    PaymentGateway promptPayGateway = supportingGateway("promptpay", "ref-1");
    BogusPaymentGateway bogus = new BogusPaymentGateway();
    BillingService service = service(List.of(promptPayGateway, bogus));

    var settlement = service.settle("res-1", 240, PaymentMethod.PROMPT_PAY);

    assertThat(settlement.gateway()).isEqualTo("promptpay");
    assertThat(settlement.reference()).isEqualTo("ref-1");
    assertThat(settlement.paidAt()).isEqualTo(NOW);
    verify(payments)
        .save(any(PaymentEntity.class)); // The persisted row belongs to the settled reservation.
  }

  @Test
  void settleRecordsTheSuccessfulChargeWithTheGatewayReference() {
    BillingService service = service(List.of(new BogusPaymentGateway()));

    service.settle("res-1", 120, PaymentMethod.CASH);

    verify(payments).save(any(PaymentEntity.class));
  }

  @Test
  void settleFailsWhenTheGatewayDeclinesTheCharge() {
    PaymentGateway declining =
        new PaymentGateway() {
          @Override
          public boolean supports(PaymentMethod method) {
            return true;
          }

          @Override
          public PaymentResult charge(PaymentRequest request) {
            return PaymentResult.declined("declining", "insufficient funds");
          }
        };
    BillingService service = service(List.of(declining));

    assertThatThrownBy(() -> service.settle("res-1", 240, PaymentMethod.BANK_TRANSFER))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("insufficient funds");
    verify(payments, org.mockito.Mockito.never()).save(any(PaymentEntity.class));
  }

  @Test
  void settleFailsWhenNoGatewaySupportsTheMethod() {
    PaymentGateway cashOnly =
        new PaymentGateway() {
          @Override
          public boolean supports(PaymentMethod method) {
            return method == PaymentMethod.CASH;
          }

          @Override
          public PaymentResult charge(PaymentRequest request) {
            return PaymentResult.succeeded("cash-only", "ref");
          }
        };
    BillingService service = service(List.of(cashOnly));

    assertThatThrownBy(() -> service.settle("res-1", 240, PaymentMethod.PROMPT_PAY))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("PromptPay");
  }

  private BillingService service(List<PaymentGateway> gateways) {
    return new BillingService(gateways, payments, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** A minimal gateway adapter that only processes PromptPay charges. */
  private PaymentGateway supportingGateway(String name, String reference) {
    return new PaymentGateway() {
      @Override
      public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.PROMPT_PAY;
      }

      @Override
      public PaymentResult charge(PaymentRequest request) {
        return PaymentResult.succeeded(name, reference);
      }
    };
  }
}
