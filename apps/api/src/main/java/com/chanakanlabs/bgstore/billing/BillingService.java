package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Settles a confirmed fee through the first gateway adapter that supports the chosen payment
 * method, and records the successful charge. A declined charge fails the caller's transaction so
 * the session stays open for a retry; only successful charges are recorded.
 */
@Service
public class BillingService {

  private final List<PaymentGateway> gateways;
  private final JpaPaymentRepository payments;
  private final Clock clock;

  BillingService(List<PaymentGateway> gateways, JpaPaymentRepository payments, Clock clock) {
    this.gateways = gateways;
    this.payments = payments;
    this.clock = clock;
  }

  @Transactional
  public Settlement settle(String reservationId, int amount, PaymentMethod method) {
    PaymentGateway gateway =
        gateways.stream()
            .filter(candidate -> candidate.supports(method))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "No payment gateway supports " + method.getValue() + "."));
    PaymentGateway.PaymentResult result =
        gateway.charge(new PaymentGateway.PaymentRequest(reservationId, amount, method));
    if (!result.succeeded()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "The payment gateway declined the charge: "
              + (result.declineReason() == null ? "unknown reason" : result.declineReason()));
    }
    if (result.reference() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "The payment gateway returned no reference for the charge.");
    }
    payments.save(
        new PaymentEntity(
            reservationId, amount, "THB", method, result.gateway(), result.reference()));
    return new Settlement(result.gateway(), result.reference(), clock.instant());
  }

  /** The successful settlement a caller may show on a receipt. */
  public record Settlement(String gateway, String reference, Instant paidAt) {}
}
