package com.chanakanlabs.theparlor.payments;

import java.util.Collection;
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
  /** Authorizes the single-use token for the amount, or throws a 402/502 ProblemException. */
  PaymentSummary authorizeCard(Payable payable, long amountMinor, String paymentToken);

  /** Records a sale that staff settle at the counter, outside any gateway. */
  PaymentSummary settleAtCounter(Payable payable, long amountMinor);

  /** Takes the money when the goods are handed over; repeating it changes nothing. */
  PaymentSummary capture(Payable payable);

  /** Releases an uncaptured payment when the payable is cancelled; repeating it changes nothing. */
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
    COUNTER
  }

  enum Status {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    VOIDED
  }

  /** cardBrand and cardLast4 are null for counter payments. */
  record PaymentSummary(
      Method method, Status status, long amountMinor, String cardBrand, String cardLast4) {}
}
