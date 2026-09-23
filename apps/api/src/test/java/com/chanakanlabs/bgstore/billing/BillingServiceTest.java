package com.chanakanlabs.bgstore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.chanakanlabs.bgstore.billing.PaymentGateway.CardDetails;
import com.chanakanlabs.bgstore.billing.PaymentGateway.Failure;
import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentRequest;
import com.chanakanlabs.bgstore.billing.PaymentGateway.PaymentResult;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    BillingService service = service(List.of(promptPayGateway, bogusGateway()));

    var settlement = service.settle("res-1", 240, PaymentMethod.PROMPT_PAY, null);

    assertThat(settlement.gateway()).isEqualTo("promptpay");
    assertThat(settlement.reference()).isEqualTo("ref-1");
    assertThat(settlement.paidAt()).isEqualTo(NOW);
    assertThat(settlement.cardBrand()).isNull();
    verify(payments)
        .save(any(PaymentEntity.class)); // The persisted row belongs to the settled reservation.
  }

  @Test
  void settleRecordsTheSuccessfulChargeWithTheGatewayReference() {
    BillingService service = service(List.of(bogusGateway()));

    service.settle("res-1", 120, PaymentMethod.CASH, null);

    verify(payments).save(any(PaymentEntity.class));
  }

  @Test
  void settleRecordsTheCardBrandAndLastFourDigitsForACardCharge() {
    BillingService service = service(List.of(bogusGateway()));

    var settlement =
        service.settle(
            "res-1",
            240,
            PaymentMethod.CARD,
            new CardDetails("4242 4242 4242 4242", "424", 12, 2029));

    assertThat(settlement.cardBrand()).isEqualTo("Visa");
    assertThat(settlement.cardLast4()).isEqualTo("4242");
    ArgumentCaptor<PaymentEntity> saved = ArgumentCaptor.forClass(PaymentEntity.class);
    verify(payments).save(saved.capture());
    assertThat(saved.getValue().cardBrand()).isEqualTo("Visa");
    assertThat(saved.getValue().cardLast4()).isEqualTo("4242");
  }

  @Test
  void settleFailsWithPaymentRequiredAndTheDeclineReasonWhenTheCardIsDeclined() {
    for (String reason :
        new String[] {
          "insufficient_funds",
          "stolen_card",
          "cvv_mismatch",
          "invalid_card_number",
          "invalid_cvv",
          "invalid_expiry",
          "card_expired"
        }) {
      BillingService service =
          service(
              List.of(
                  charging(PaymentResult.declined("declining", Failure.CARD_DECLINED, reason))));

      assertThatThrownBy(() -> service.settle("res-1", 240, PaymentMethod.BANK_TRANSFER, null))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                assertThat(e.getBody().getTitle()).isEqualTo("Payment Required");
                assertThat(e.getBody().getProperties().get("code")).isEqualTo(reason);
              })
          .hasMessageContaining("The card was declined: " + reason + ".");
    }
    verify(payments, org.mockito.Mockito.never()).save(any(PaymentEntity.class));
  }

  @Test
  void settleFailsWithTheDeclineReasonWhenTheGatewayItselfFails() {
    BillingService service =
        service(
            List.of(
                charging(
                    PaymentResult.declined(
                        "flaky", Failure.GATEWAY_ERROR, "gateway_unavailable"))));

    assertThatThrownBy(() -> service.settle("res-1", 240, PaymentMethod.PROMPT_PAY, null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(e.getBody().getTitle()).isEqualTo("Bad Gateway");
              assertThat(e.getBody().getProperties().get("code")).isEqualTo("gateway_unavailable");
            })
        .hasMessageContaining("could not process the charge");
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

    assertThatThrownBy(() -> service.settle("res-1", 240, PaymentMethod.PROMPT_PAY, null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
        .hasMessageContaining("PromptPay");
  }

  private BillingService service(List<PaymentGateway> gateways) {
    return new BillingService(gateways, payments, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private BogusPaymentGateway bogusGateway() {
    return new BogusPaymentGateway(Clock.fixed(NOW, ZoneOffset.UTC));
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

  /** A gateway adapter that answers every charge with the given result. */
  private PaymentGateway charging(PaymentResult answer) {
    return new PaymentGateway() {
      @Override
      public boolean supports(PaymentMethod method) {
        return true;
      }

      @Override
      public PaymentResult charge(PaymentRequest request) {
        return answer;
      }
    };
  }
}
