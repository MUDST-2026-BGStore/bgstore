package com.chanakanlabs.theparlor.rentals.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Immutable served refreshment, priced once at service rather than per play hour. */
@Entity
@Table(name = "rental_refreshments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalRefreshment {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "rental_id", nullable = false)
  private UUID rentalId;

  @Column(name = "menu_item_id", nullable = false)
  private UUID menuItemId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price_minor", nullable = false)
  private Long unitPriceMinor;

  @Column(name = "line_total_minor", nullable = false)
  private Long lineTotalMinor;

  @Column(name = "served_at", nullable = false)
  private Instant servedAt;

  @Column(name = "served_by", nullable = false)
  private String servedBy;

  public static RentalRefreshment serve(
      UUID rentalId,
      UUID menuItemId,
      String name,
      int quantity,
      long unitPriceMinor,
      Instant now,
      String staff) {
    if (name == null || name.isBlank() || quantity < 1 || quantity > 50 || unitPriceMinor < 0)
      throw new IllegalArgumentException("Invalid refreshment line");
    var line = new RentalRefreshment();
    line.rentalId = Objects.requireNonNull(rentalId);
    line.menuItemId = Objects.requireNonNull(menuItemId);
    line.name = name;
    line.quantity = quantity;
    line.unitPriceMinor = unitPriceMinor;
    line.lineTotalMinor = Math.multiplyExact(quantity, unitPriceMinor);
    line.servedAt = Objects.requireNonNull(now);
    line.servedBy = Objects.requireNonNull(staff);
    return line;
  }
}
