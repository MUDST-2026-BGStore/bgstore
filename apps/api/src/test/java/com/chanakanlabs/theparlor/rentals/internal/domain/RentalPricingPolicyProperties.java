package com.chanakanlabs.theparlor.rentals.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/** Invariants of hourly billing, checked over generated visits. */
class RentalPricingPolicyProperties {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final long HOUR = Duration.ofHours(1).toNanos();

  @Property
  void billsEveryStartedHourWithAOneHourMinimum(
      @ForAll @LongRange(min = 0, max = 30L * 24 * 3600 * 1_000_000_000L) long elapsedNanos) {
    int hours = RentalPricingPolicy.billableHours(START, START.plusNanos(elapsedNanos));
    assertThat(hours).isGreaterThanOrEqualTo(1);
    // The billed hours cover the visit, and one hour fewer would not.
    assertThat((long) hours * HOUR).isGreaterThanOrEqualTo(elapsedNanos);
    if (hours > 1) assertThat((long) (hours - 1) * HOUR).isLessThan(elapsedNanos);
  }

  @Property
  void neverBillsLessForALongerVisit(
      @ForAll @LongRange(min = 0, max = 1_000_000_000_000_000L) long shorter,
      @ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long extra) {
    assertThat(RentalPricingPolicy.billableHours(START, START.plusNanos(shorter + extra)))
        .isGreaterThanOrEqualTo(RentalPricingPolicy.billableHours(START, START.plusNanos(shorter)));
  }

  @Property
  void chargesQuantityTimesRateTimesHours(
      @ForAll @IntRange(min = 1, max = 50) int quantity,
      @ForAll @LongRange(min = 0, max = 10_000_000) long rateMinor,
      @ForAll @IntRange(min = 1, max = 720) int hours) {
    assertThat(RentalPricingPolicy.lineTotalMinor(quantity, rateMinor, hours))
        .isEqualTo(quantity * rateMinor * hours);
  }

  @Property
  void totalsEveryLine(
      @ForAll @Size(min = 1, max = 20) List<@LongRange(min = 0, max = 1_000_000_000) Long> lines) {
    assertThat(RentalPricingPolicy.totalMinor(lines))
        .isEqualTo(lines.stream().mapToLong(Long::longValue).sum());
  }
}
