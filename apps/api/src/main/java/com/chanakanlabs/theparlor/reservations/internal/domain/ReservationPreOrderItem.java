package com.chanakanlabs.theparlor.reservations.internal.domain;

import com.chanakanlabs.theparlor.shared.ContactStorageLimits;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One pre-ordered line with its name and unit price as sold at booking. */
@Entity
@Table(name = "reservation_preorder_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPreOrderItem {
  @EmbeddedId private ReservationPreOrderItemId id;

  @Column(
      nullable = false,
      updatable = false,
      length = ContactStorageLimits.MAX_SNAPSHOT_NAME_LENGTH)
  private String name;

  @Column(nullable = false, updatable = false)
  private int quantity;

  @Column(name = "unit_price_minor", nullable = false, updatable = false)
  private long unitPriceMinor;

  @Column(name = "line_total_minor", nullable = false, updatable = false)
  private long lineTotalMinor;

  public static ReservationPreOrderItem line(
      UUID reservationId, UUID menuItemId, String name, int quantity, long unitPriceMinor) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Line name required");
    if (quantity < 1 || quantity > 50)
      throw new IllegalArgumentException("Quantity must be between 1 and 50");
    if (unitPriceMinor < 0) throw new IllegalArgumentException("Price must be nonnegative");
    var item = new ReservationPreOrderItem();
    item.id =
        new ReservationPreOrderItemId(
            Objects.requireNonNull(reservationId), Objects.requireNonNull(menuItemId));
    item.name = name;
    item.quantity = quantity;
    item.unitPriceMinor = unitPriceMinor;
    item.lineTotalMinor = Math.multiplyExact(unitPriceMinor, quantity);
    return item;
  }
}
