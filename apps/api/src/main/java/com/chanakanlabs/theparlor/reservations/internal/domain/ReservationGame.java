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
}
