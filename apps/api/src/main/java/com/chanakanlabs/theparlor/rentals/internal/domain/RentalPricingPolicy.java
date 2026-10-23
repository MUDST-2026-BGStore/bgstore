package com.chanakanlabs.theparlor.rentals.internal.domain;

import java.time.Instant;
import java.util.List;

/** Pure hourly charge calculations in integer THB satang; unfinished, with no database access. */
public final class RentalPricingPolicy {
  private RentalPricingPolicy() {}

  public static int billableHours(Instant startedAt, Instant endedAt) {
    // TODO(rental-pricing): reject missing/reversed timestamps; use actual server check-in/out
    // instants. Round elapsed duration UP to started hours, minimum one, without floating point
    // or losing fractional seconds. Check conversion overflow; do not use the planned end time.
    throw new UnsupportedOperationException("Rental billable hours are not implemented yet");
  }

  public static long lineTotalMinor(int quantity, long lockedHourlyRateMinor, int billableHours) {
    // TODO(rental-pricing): require quantity>=1, rate>=0, hours>=1; calculate quantity*rate*hours
    // using exact arithmetic. Inputs come from reservation snapshots, never current branch prices.
    // The table line has quantity=1; each game line uses its selected physical-copy quantity.
    throw new UnsupportedOperationException(
        "Rental charge line calculation is not implemented yet");
  }

  public static long totalMinor(List<Long> lineTotalsMinor) {
    // TODO(rental-pricing): require at least the table line; reject null/negative amounts and sum
    // using exact arithmetic. No discount, deposit, tax or take-home-rental rules are specified.
    // TODO(rental-checkout): wire this policy into a staff-authorized transactional checkout.
    // Persist final charges/total, complete the reservation and release resources atomically.
    // Repeated checkout must return persisted charges without recalculation. Resolve reservation
    // lifecycle CHECK constraints in a new migration so COMPLETED can retain check-in audit fields.
    throw new UnsupportedOperationException("Rental total calculation is not implemented yet");
  }
}
