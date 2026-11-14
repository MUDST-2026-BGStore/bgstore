package com.chanakanlabs.theparlor.payments.internal.application;

import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.payments.internal.domain.Payment;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayDeclined;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayFailure;
import com.chanakanlabs.theparlor.payments.internal.persistence.PaymentRepository;
import com.chanakanlabs.theparlor.shared.ProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentsService implements Payments {
  private static final Logger log = LoggerFactory.getLogger(PaymentsService.class);

  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final Clock clock;

  public PaymentsService(PaymentRepository payments, PaymentGateway gateway, Clock clock) {
    this.payments = payments;
    this.gateway = gateway;
    this.clock = clock;
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
  public PaymentSummary startPromptPay(Payable payable, long amountMinor) {
    var expiresAt = clock.instant().plus(PROMPTPAY_LIFETIME);
    var charge = calling(() -> gateway.createPromptPay(amountMinor, reference(payable), expiresAt));
    return summary(
        payments.save(
            Payment.promptPay(
                payable,
                amountMinor,
                gateway.name(),
                charge.reference(),
                charge.qr(),
                // The earlier deadline wins, so the order is never cancelled while still payable.
                charge.expiresAt() != null && charge.expiresAt().isBefore(expiresAt)
                    ? charge.expiresAt()
                    : expiresAt)));
  }

  @Override
  public PaymentSummary payOnDelivery(Payable payable, long amountMinor) {
    return summary(payments.save(Payment.cashOnDelivery(payable, amountMinor)));
  }

  @Override
  public PaymentSummary confirmReceived(Payable payable) {
    var payment = required(payable);
    if (payment.getStatus() == Status.CAPTURED) return summary(payment);
    switch (payment.getMethod()) {
      case COD -> {
        if (payment.getStatus() != Status.PENDING) throw notReceivable();
        payment.capture();
      }
      case PROMPTPAY -> {
        if (!refresh(payment)) throw notReceived();
      }
      default -> throw notReceivable();
    }
    return summary(payment);
  }

  @Override
  public boolean refreshPromptPay(Payable payable) {
    return refresh(required(payable));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void gatewayChargeChanged(String gatewayReference) {
    if (gatewayReference == null || gatewayReference.isBlank()) return;
    payments
        .lockByGatewayReference(gateway.name(), gatewayReference)
        .filter(payment -> payment.getMethod() == Method.PROMPTPAY)
        .ifPresent(
            payment -> {
              if (payment.getStatus() == Status.VOIDED) {
                if (calling(() -> gateway.isPaid(gatewayReference)))
                  log.warn(
                      "PromptPay charge {} was paid after its payment was voided; refund it at the"
                          + " provider",
                      gatewayReference);
                return;
              }
              refresh(payment);
            });
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<UUID> expiredUnpaid(Kind kind, Instant now) {
    return switch (kind) {
      case ORDER -> payments.expiredUnpaidOrders(now);
      case RESERVATION -> payments.expiredUnpaidReservations(now);
    };
  }

  @Override
  public PaymentSummary capture(Payable payable) {
    var payment = required(payable);
    switch (payment.getMethod()) {
      case CARD -> {
        if (payment.isOpen()) {
          requireSameGateway(payment);
          calling(
              () -> {
                gateway.capture(payment.getGatewayReference(), payment.getAmountMinor());
                return null;
              });
        }
        payment.capture();
      }
      case COUNTER -> payment.capture();
      case PROMPTPAY -> {
        if (payment.getStatus() == Status.PENDING && !refresh(payment)) throw notReceived();
        if (payment.getStatus() != Status.CAPTURED) payment.capture();
      }
      case COD -> {
        // Cash changes hands at the door; staff confirm it separately.
        if (payment.getStatus() == Status.VOIDED)
          throw new IllegalStateException("A voided payment cannot be captured");
      }
    }
    return summary(payment);
  }

  @Override
  public PaymentSummary cancel(Payable payable) {
    var payment = required(payable);
    switch (payment.getMethod()) {
      case CARD -> {
        if (payment.isOpen()) {
          requireSameGateway(payment);
          calling(
              () -> {
                gateway.cancel(payment.getGatewayReference());
                return null;
              });
        }
      }
      case PROMPTPAY -> {
        // A QR cannot be withdrawn at the provider, so make sure it was not paid a moment ago.
        if (payment.getStatus() == Status.PENDING) refresh(payment);
        if (payment.getStatus() == Status.CAPTURED) throw alreadyReceived();
      }
      case COD -> {
        if (payment.getStatus() == Status.CAPTURED) throw alreadyReceived();
      }
      case COUNTER -> {}
    }
    payment.cancel();
    return summary(payment);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Map<UUID, PaymentSummary> of(Kind kind, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    var found =
        switch (kind) {
          case ORDER -> payments.findByOrderIdIn(ids);
          case RESERVATION -> payments.findByReservationIdIn(ids);
        };
    return found.stream().collect(Collectors.toMap(Payment::payableId, PaymentsService::summary));
  }

  /** Captures a PENDING PromptPay payment the provider reports paid; returns whether captured. */
  private boolean refresh(Payment payment) {
    if (payment.getMethod() != Method.PROMPTPAY) return false;
    if (payment.getStatus() == Status.CAPTURED) return true;
    if (payment.getStatus() != Status.PENDING) return false;
    requireSameGateway(payment);
    if (!calling(() -> gateway.isPaid(payment.getGatewayReference()))) return false;
    payment.capture();
    return true;
  }

  /** The payable's payment, locked: every caller is about to change or settle it. */
  private Payment required(Payable payable) {
    var found =
        switch (payable.kind()) {
          case ORDER -> payments.lockByOrderId(payable.id());
          case RESERVATION -> payments.lockByReservationId(payable.id());
        };
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

  private static ProblemException notReceived() {
    return new ProblemException(
        HttpStatus.CONFLICT, "PAYMENT_NOT_RECEIVED", "Payment has not been received");
  }

  private static ProblemException notReceivable() {
    return new ProblemException(
        HttpStatus.CONFLICT,
        "PAYMENT_NOT_CONFIRMABLE",
        "Only a pending cash on delivery or PromptPay payment can be confirmed");
  }

  private static ProblemException alreadyReceived() {
    return new ProblemException(
        HttpStatus.CONFLICT,
        "PAYMENT_ALREADY_RECEIVED",
        "Payment has already been received and cannot be cancelled");
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
    boolean awaitingQr =
        payment.getMethod() == Method.PROMPTPAY && payment.getStatus() == Status.PENDING;
    return new PaymentSummary(
        payment.getMethod(),
        payment.getStatus(),
        payment.getAmountMinor(),
        payment.getCardBrand() == null ? null : payment.getCardBrand().name(),
        payment.getCardLast4(),
        awaitingQr ? payment.getPromptPayQr() : null,
        awaitingQr ? payment.getExpiresAt() : null);
  }
}
