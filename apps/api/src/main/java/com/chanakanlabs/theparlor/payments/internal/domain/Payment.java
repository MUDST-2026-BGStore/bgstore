package com.chanakanlabs.theparlor.payments.internal.domain;

import com.chanakanlabs.theparlor.payments.Payments.Kind;
import com.chanakanlabs.theparlor.payments.Payments.Method;
import com.chanakanlabs.theparlor.payments.Payments.Payable;
import com.chanakanlabs.theparlor.payments.Payments.Status;
import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * The one payment of an order or a reservation pre-order. Holds a gateway reference, never card
 * numbers or codes.
 */
@Entity
@DynamicUpdate
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "order_id", updatable = false, unique = true)
  private UUID orderId;

  @Column(name = "reservation_id", updatable = false, unique = true)
  private UUID reservationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = EnumStorageLengths.STATUS)
  private Method method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private Status status;

  @Column(name = "amount_minor", nullable = false, updatable = false)
  private long amountMinor;

  @Column(updatable = false, length = 40)
  private String gateway;

  @Column(name = "gateway_reference", updatable = false)
  private String gatewayReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "card_brand", updatable = false, length = EnumStorageLengths.STATUS)
  private CardBrand cardBrand;

  @Column(name = "card_last4", updatable = false, length = 4)
  private String cardLast4;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static Payment authorizedCard(
      Payable payable,
      long amountMinor,
      String gateway,
      String gatewayReference,
      CardBrand brand,
      String last4) {
    var payment = create(payable, amountMinor, Method.CARD, Status.AUTHORIZED);
    payment.gateway = Objects.requireNonNull(gateway);
    payment.gatewayReference = Objects.requireNonNull(gatewayReference);
    payment.cardBrand = Objects.requireNonNull(brand);
    if (last4 == null || !last4.matches("[0-9]{4}"))
      throw new IllegalArgumentException("Card last four digits are required");
    payment.cardLast4 = last4;
    return payment;
  }

  public static Payment atCounter(Payable payable, long amountMinor) {
    return create(payable, amountMinor, Method.COUNTER, Status.PENDING);
  }

  private static Payment create(Payable payable, long amountMinor, Method method, Status status) {
    if (amountMinor < 0) throw new IllegalArgumentException("Payment amount must be nonnegative");
    var payment = new Payment();
    if (payable.kind() == Kind.ORDER) payment.orderId = payable.id();
    else payment.reservationId = payable.id();
    payment.amountMinor = amountMinor;
    payment.method = method;
    payment.status = status;
    return payment;
  }

  /** The ID of what this payment pays for. */
  public UUID payableId() {
    return orderId != null ? orderId : reservationId;
  }

  /** Whether money is promised but not yet taken or released. */
  public boolean isOpen() {
    return status == Status.AUTHORIZED || status == Status.PENDING;
  }

  public void capture() {
    if (status == Status.CAPTURED) return;
    if (!isOpen()) throw new IllegalStateException("A voided payment cannot be captured");
    status = Status.CAPTURED;
  }

  /** Refunds are outside this model, so a captured payment cannot be cancelled. */
  public void cancel() {
    if (status == Status.VOIDED) return;
    if (!isOpen()) throw new IllegalStateException("A captured payment cannot be voided");
    status = Status.VOIDED;
  }
}
