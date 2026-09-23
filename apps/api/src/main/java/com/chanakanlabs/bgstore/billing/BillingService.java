package com.chanakanlabs.bgstore.billing;

import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Settles a confirmed fee through the first gateway adapter that supports the chosen payment
 * method, and records the successful charge. A declined charge fails the caller's transaction so
 * the session stays open for a retry; only successful charges are recorded, carrying the detected
 * card brand and last four digits for card charges — never the full card number.
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
  public Settlement settle(
      String reservationId,
      int amount,
      PaymentMethod method,
      PaymentGateway.@Nullable CardDetails card) {
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
        gateway.charge(new PaymentGateway.PaymentRequest(reservationId, amount, method, card));
    if (!result.succeeded()) {
      throw declined(result);
    }
    if (result.reference() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "The payment gateway returned no reference for the charge.");
    }
    payments.save(
        new PaymentEntity(
            reservationId,
            amount,
            "THB",
            method,
            result.gateway(),
            result.reference(),
            result.cardBrand(),
            result.cardLast4()));
    return new Settlement(
        result.gateway(),
        result.reference(),
        clock.instant(),
        result.cardBrand(),
        result.cardLast4());
  }

  /**
   * Turns a declined charge into a 502 whose problem detail carries the machine-readable decline
   * reason as a {@code code} property, so a client can show a localized message for it.
   */
  private static ResponseStatusException declined(PaymentGateway.PaymentResult result) {
    String reason = result.declineReason() == null ? "unknown" : result.declineReason();
    ResponseStatusException exception;
    if (result.failure() == PaymentGateway.Failure.GATEWAY_ERROR) {
      exception =
          new ResponseStatusException(
              HttpStatus.BAD_GATEWAY,
              "The payment gateway could not process the charge; please retry.");
    } else {
      exception =
          new ResponseStatusException(
              HttpStatus.BAD_GATEWAY, "The card was declined: " + reason + ".");
    }
    exception.getBody().setProperty("code", reason);
    return exception;
  }

  /** The successful settlement a caller may show on a receipt. */
  public record Settlement(
      String gateway,
      String reference,
      Instant paidAt,
      @Nullable String cardBrand,
      @Nullable String cardLast4) {}
}
