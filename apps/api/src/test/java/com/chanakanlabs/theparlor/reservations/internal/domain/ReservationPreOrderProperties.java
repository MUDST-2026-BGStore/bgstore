package com.chanakanlabs.theparlor.reservations.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/** Pre-order pricing and booking-window invariants, over generated amounts and times. */
class ReservationPreOrderProperties {

  @Property
  void discountsTenPercentRoundedHalfUpAndNeverExceedsTheSubtotal(
      @ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long subtotalMinor) {
    long discount = ReservationPreOrder.discount(subtotalMinor);
    // Same rule the storefront previews: floor((subtotal * 10 + 50) / 100).
    long tenths = subtotalMinor; // ten percent in tenths of a satang
    long expected = tenths / 10 + (tenths % 10 >= 5 ? 1 : 0);
    assertThat(discount).isEqualTo(expected).isBetween(0L, subtotalMinor);
  }

  @Property
  void allowsExactlyTheVisitsStartingWithinTheWindow(
      @ForAll @LongRange(min = -10L * 86_400, max = 30L * 86_400) long offsetSeconds) {
    Instant now = Instant.parse("2026-06-01T12:00:00Z");
    Instant startsAt = now.plusSeconds(offsetSeconds);
    assertThat(ReservationPreOrder.allowedFor(startsAt, now))
        .isEqualTo(
            !Duration.ofSeconds(offsetSeconds).minus(ReservationPreOrder.WINDOW).isPositive());
  }
}
