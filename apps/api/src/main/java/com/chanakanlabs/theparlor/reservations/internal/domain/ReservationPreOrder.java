package com.chanakanlabs.theparlor.reservations.internal.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Food and drink paid for with a reservation. The discount is fixed at booking and never
 * recalculated, so later price or policy changes leave existing pre-orders as they were sold.
 */
@Entity
@Table(name = "reservation_preorders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPreOrder {
  /** Percentage taken off the subtotal of every pre-order. */
  public static final int DISCOUNT_PERCENT = 10;

  /**
   * How far ahead of the visit food can be pre-ordered. Online card authorizations usually expire
   * after seven days (Visa merchant-initiated ones sooner), and the money is captured at check-in,
   * so the visit must start well inside that window.
   */
  public static final Duration WINDOW = Duration.ofDays(6);

  @Id
  @Column(name = "reservation_id")
  private UUID reservationId;

  @Column(name = "subtotal_minor", nullable = false, updatable = false)
  private long subtotalMinor;

  @Column(name = "discount_minor", nullable = false, updatable = false)
  private long discountMinor;

  @Column(name = "total_minor", nullable = false, updatable = false)
  private long totalMinor;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Prices the lines: the discount is DISCOUNT_PERCENT of the subtotal, rounded half up. */
  public static ReservationPreOrder price(UUID reservationId, List<ReservationPreOrderItem> lines) {
    if (lines == null || lines.isEmpty())
      throw new IllegalArgumentException("A pre-order needs at least one line");
    long subtotal = 0;
    for (var line : lines) {
      if (!line.getId().reservationId().equals(reservationId))
        throw new IllegalArgumentException("Pre-order lines belong to another reservation");
      subtotal = Math.addExact(subtotal, line.getLineTotalMinor());
    }
    if (subtotal <= 0) throw new IllegalArgumentException("A pre-order must cost something");
    var preOrder = new ReservationPreOrder();
    preOrder.reservationId = Objects.requireNonNull(reservationId);
    preOrder.subtotalMinor = subtotal;
    preOrder.discountMinor = discount(subtotal);
    preOrder.totalMinor = subtotal - preOrder.discountMinor;
    return preOrder;
  }

  static long discount(long subtotalMinor) {
    return Math.addExact(Math.multiplyExact(subtotalMinor, DISCOUNT_PERCENT), 50) / 100;
  }

  /** Whether a visit starting at startsAt may carry a pre-order when booked or moved at now. */
  public static boolean allowedFor(Instant startsAt, Instant now) {
    return !startsAt.isAfter(now.plus(WINDOW));
  }
}
