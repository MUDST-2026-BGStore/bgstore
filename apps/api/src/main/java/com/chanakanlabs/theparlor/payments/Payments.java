package com.chanakanlabs.theparlor.payments;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Module interface for paying orders and reservation pre-orders. Call inside the payable's
 * transaction so a declined or failed payment rolls it and its stock holds back. One payment exists
 * per payable.
 *
 * <p>A gateway call cannot be rolled back with the database. If the transaction fails after a
 * successful authorization, that authorization is orphaned at the gateway and expires there.
 */
public interface Payments {
  /** How long a customer has to pay a PromptPay QR before the payable is cancelled. */
  Duration PROMPTPAY_LIFETIME = Duration.ofMinutes(15);

  /** Authorizes the single-use token for the amount, or throws a 402/502 ProblemException. */
  PaymentSummary authorizeCard(Payable payable, long amountMinor, String paymentToken);

  /** Records a sale that staff settle at the counter, outside any gateway. */
  PaymentSummary settleAtCounter(Payable payable, long amountMinor);

  /**
   * Creates a PromptPay QR charge at the gateway, payable for {@link #PROMPTPAY_LIFETIME}. The
   * payment is PENDING until the provider reports it paid. Throws a 402/502 ProblemException.
   */
  PaymentSummary startPromptPay(Payable payable, long amountMinor);

  /** Records cash on delivery: PENDING until staff confirm the cash was received. */
  PaymentSummary payOnDelivery(Payable payable, long amountMinor);

  /**
   * Marks money as received: cash on delivery on staff's word, PromptPay only once the provider
   * says it is paid. Throws a 409 ProblemException otherwise. Repeating it changes nothing.
   */
  PaymentSummary confirmReceived(Payable payable);

  /**
   * Asks the provider whether a PENDING PromptPay charge has been paid and, if so, records it
   * CAPTURED. Returns whether the payment is now captured. Any other payment is left as it is.
   */
  boolean refreshPromptPay(Payable payable);

  /**
   * Records a provider's notice that a charge changed. The notice is only a hint: the provider is
   * asked for the charge's real state. Unknown references are ignored.
   */
  void gatewayChargeChanged(String gatewayReference);

  /** Payables of this kind whose PromptPay payment is still PENDING after its deadline. */
  List<UUID> expiredUnpaid(Kind kind, Instant now);

  /**
   * Takes the money when the goods are handed over; repeating it changes nothing. An unpaid
   * PromptPay payment cannot be taken (409); cash on delivery stays PENDING until confirmed.
   */
  PaymentSummary capture(Payable payable);

  /**
   * Releases an uncaptured payment when the payable is cancelled; repeating it changes nothing. A
   * received PromptPay or cash payment cannot be released (409); refunds are not supported.
   */
  PaymentSummary cancel(Payable payable);

  /** Payments of these payables of one kind, by payable ID. */
  Map<UUID, PaymentSummary> of(Kind kind, Collection<UUID> ids);

  /** What a payment pays for. */
  enum Kind {
    ORDER,
    RESERVATION
  }

  record Payable(Kind kind, UUID id) {
    public Payable {
      Objects.requireNonNull(kind);
      Objects.requireNonNull(id);
    }

    public static Payable order(UUID orderId) {
      return new Payable(Kind.ORDER, orderId);
    }

    public static Payable reservation(UUID reservationId) {
      return new Payable(Kind.RESERVATION, reservationId);
    }
  }

  enum Method {
    CARD,
    COUNTER,
    PROMPTPAY,
    COD
  }

  enum Status {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    VOIDED
  }

  /**
   * cardBrand and cardLast4 are set only for card payments. promptPayQr (an image URL the browser
   * can display) and expiresAt are set only while a PromptPay payment is PENDING.
   */
  record PaymentSummary(
      Method method,
      Status status,
      long amountMinor,
      String cardBrand,
      String cardLast4,
      String promptPayQr,
      Instant expiresAt) {}
}
