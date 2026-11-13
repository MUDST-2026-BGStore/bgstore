package com.chanakanlabs.theparlor.payments.internal.application;

import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.internal.domain.Payment;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayDeclined;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayFailure;
import com.chanakanlabs.theparlor.payments.internal.persistence.PaymentRepository;
import com.chanakanlabs.theparlor.shared.ProblemException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentsService implements Payments {
  private final PaymentRepository payments;
  private final PaymentGateway gateway;

  public PaymentsService(PaymentRepository payments, PaymentGateway gateway) {
    this.payments = payments;
    this.gateway = gateway;
  }

  @Override
  public PaymentSummary authorizeCard(Payable payable, long amountMinor, String paymentToken) {
    var authorization =
        calling(() -> gateway.authorize(paymentToken, amountMinor, reference(payable)));
    return summary(
        payments.save(
            Payment.authorizedCard(
                payable,
                amountMinor,
                gateway.name(),
                authorization.reference(),
                authorization.brand(),
                authorization.last4())));
  }

  @Override
  public PaymentSummary settleAtCounter(Payable payable, long amountMinor) {
    return summary(payments.save(Payment.atCounter(payable, amountMinor)));
  }

  @Override
  public PaymentSummary capture(Payable payable) {
    var payment = required(payable);
    if (payment.getMethod() == Method.CARD && payment.isOpen()) {
      requireSameGateway(payment);
      calling(
          () -> {
            gateway.capture(payment.getGatewayReference(), payment.getAmountMinor());
            return null;
          });
    }
    payment.capture();
    return summary(payment);
  }

  @Override
  public PaymentSummary cancel(Payable payable) {
    var payment = required(payable);
    if (payment.getMethod() == Method.CARD && payment.isOpen()) {
      requireSameGateway(payment);
      calling(
          () -> {
            gateway.cancel(payment.getGatewayReference());
            return null;
          });
    }
    payment.cancel();
    return summary(payment);
  }

  @Override
  public Map<UUID, PaymentSummary> of(Kind kind, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    var found =
        kind == Kind.ORDER ? payments.findByOrderIdIn(ids) : payments.findByReservationIdIn(ids);
    return found.stream().collect(Collectors.toMap(Payment::payableId, PaymentsService::summary));
  }

  private Payment required(Payable payable) {
    var found =
        payable.kind() == Kind.ORDER
            ? payments.findByOrderId(payable.id())
            : payments.findByReservationId(payable.id());
    return found.orElseThrow(() -> new IllegalStateException(payable + " has no payment"));
  }

  /** The reference sent to the provider, readable in its dashboard. */
  private static String reference(Payable payable) {
    return payable.kind().name().toLowerCase() + ":" + payable.id();
  }

  /** A reference only means something to the provider that issued it. */
  private void requireSameGateway(Payment payment) {
    if (!gateway.name().equals(payment.getGateway()))
      throw new ProblemException(
          HttpStatus.BAD_GATEWAY,
          "PAYMENT_GATEWAY_ERROR",
          "This payment was made through a provider that is no longer configured");
  }

  /** Translates the gateway port's failures into the API's payment problems. */
  static <T> T calling(Supplier<T> gatewayCall) {
    try {
      return gatewayCall.get();
    } catch (GatewayDeclined declined) {
      throw new ProblemException(
          HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", declined.getMessage());
    } catch (GatewayFailure failure) {
      throw new ProblemException(
          HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", failure.getMessage());
    }
  }

  private static PaymentSummary summary(Payment payment) {
    return new PaymentSummary(
        payment.getMethod(),
        payment.getStatus(),
        payment.getAmountMinor(),
        payment.getCardBrand() == null ? null : payment.getCardBrand().name(),
        payment.getCardLast4());
  }
}
