package com.chanakanlabs.theparlor.rentals.internal.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pure hourly charge calculations in integer THB satang. Inputs are the actual server check-in and
 * checkout instants, capped at the booked end (see {@link #billingEnd}), and the rates locked on
 * the reservation, never the branch's current prices. No discount, deposit or tax rule exists.
 */
public final class RentalPricingPolicy {
  private RentalPricingPolicy() {}

  private static final long NANOS_PER_HOUR = Duration.ofHours(1).toNanos();

  /**
   * The instant play stops counting: checkout time, but never past the booked end and never before
   * check-in. A late checkout or automatic closure adds no overtime.
   */
  public static Instant billingEnd(Instant startedAt, Instant plannedEndsAt, Instant checkoutAt) {
    Instant end = checkoutAt.isAfter(plannedEndsAt) ? plannedEndsAt : checkoutAt;
    return end.isBefore(startedAt) ? startedAt : end;
  }

  /** Every started hour is billed, with a one-hour minimum: max(1, ceil(elapsed / 1h)). */
  public static int billableHours(Instant startedAt, Instant endedAt) {
    if (startedAt == null || endedAt == null)
      throw new IllegalArgumentException("Check-in and checkout times are required");
    if (endedAt.isBefore(startedAt))
      throw new IllegalArgumentException("Checkout cannot be before check-in");
    // Nanosecond precision keeps a visit one instant over the hour in the next hour.
    long hours = Math.ceilDiv(Duration.between(startedAt, endedAt).toNanos(), NANOS_PER_HOUR);
    return Math.toIntExact(Math.max(1, hours));
  }

  /** quantity x rate x hours. A table line has quantity one; a game line its copy count. */
  public static long lineTotalMinor(int quantity, long lockedHourlyRateMinor, int billableHours) {
    if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
    if (lockedHourlyRateMinor < 0) throw new IllegalArgumentException("Rate must be nonnegative");
    if (billableHours < 1) throw new IllegalArgumentException("Billable hours must be at least 1");
    return Math.multiplyExact(
        Math.multiplyExact(lockedHourlyRateMinor, (long) quantity), (long) billableHours);
  }

  /** The sum of the table line and every game line. */
  public static long totalMinor(List<Long> lineTotalsMinor) {
    if (lineTotalsMinor == null || lineTotalsMinor.isEmpty())
      throw new IllegalArgumentException("A rental has at least its table charge");
    long total = 0;
    for (Long line : lineTotalsMinor) {
      if (line == null || line < 0)
        throw new IllegalArgumentException("Charge lines must be nonnegative");
      total = Math.addExact(total, line);
    }
    return total;
  }
}
