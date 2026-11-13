package com.chanakanlabs.theparlor.reservations.internal.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "reservation_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationGame {
  @EmbeddedId private ReservationGameId id;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_hourly_rate_minor", nullable = false)
  private Long unitHourlyRateMinor;

  /** One selected title with the hourly rate per copy locked at booking. */
  public static ReservationGame select(
      java.util.UUID reservationId, java.util.UUID gameId, int quantity, long unitHourlyRateMinor) {
    if (quantity < 1 || quantity > 100)
      throw new IllegalArgumentException("Game quantity must be between 1 and 100");
    if (unitHourlyRateMinor < 0) throw new IllegalArgumentException("Rate must be nonnegative");
    var game = new ReservationGame();
    game.id =
        new ReservationGameId(
            java.util.Objects.requireNonNull(reservationId),
            java.util.Objects.requireNonNull(gameId));
    game.quantity = quantity;
    game.unitHourlyRateMinor = unitHourlyRateMinor;
    return game;
  }
}
