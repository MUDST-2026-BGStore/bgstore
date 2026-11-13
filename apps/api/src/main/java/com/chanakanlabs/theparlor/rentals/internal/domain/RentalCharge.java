package com.chanakanlabs.theparlor.rentals.internal.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "rental_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalCharge {
  /** Column width for rental charge type names; independent of fulfillment method storage. */
  public static final int CHARGE_TYPE_STORAGE_LENGTH = 10;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "rental_id", nullable = false)
  private UUID rentalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "charge_type", nullable = false, length = CHARGE_TYPE_STORAGE_LENGTH)
  private RentalChargeType chargeType;

  @Column(name = "game_id")
  private UUID gameId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_hourly_rate_minor", nullable = false)
  private Long unitHourlyRateMinor;

  @Column(name = "billable_hours", nullable = false)
  private int billableHours;

  @Column(name = "line_total_minor", nullable = false)
  private Long lineTotalMinor;

  /** The final charge for the table (gameId null) or for the copies of one game. */
  public static RentalCharge bill(
      UUID rentalId, UUID gameId, int quantity, long unitHourlyRateMinor, int billableHours) {
    var charge = new RentalCharge();
    charge.rentalId = java.util.Objects.requireNonNull(rentalId);
    charge.chargeType = gameId == null ? RentalChargeType.TABLE : RentalChargeType.GAME;
    charge.gameId = gameId;
    charge.quantity = quantity;
    charge.unitHourlyRateMinor = unitHourlyRateMinor;
    charge.billableHours = billableHours;
    charge.lineTotalMinor =
        RentalPricingPolicy.lineTotalMinor(quantity, unitHourlyRateMinor, billableHours);
    return charge;
  }
}
