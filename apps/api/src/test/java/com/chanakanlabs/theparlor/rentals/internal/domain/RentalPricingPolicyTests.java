package com.chanakanlabs.theparlor.rentals.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RentalPricingPolicyTests {
  static final Instant START = Instant.parse("2026-11-12T05:00:00Z");

  @ParameterizedTest
  @CsvSource({
    "PT0S,1",
    "PT1S,1",
    "PT59M59.999999999S,1",
    "PT1H,1",
    "PT1H0.000000001S,2",
    "PT1H1M,2",
    "PT2H,2",
    "PT2H0.5S,3",
    "PT26H,26"
  })
  void billsEveryStartedHourWithAOneHourMinimum(Duration played, int hours) {
    assertThat(RentalPricingPolicy.billableHours(START, START.plus(played))).isEqualTo(hours);
  }

  @Test
  void rejectsMissingOrReversedTimes() {
    assertThatThrownBy(() -> RentalPricingPolicy.billableHours(START, START.minusNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.billableHours(null, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void multipliesQuantityRateAndHoursExactly() {
    assertThat(RentalPricingPolicy.lineTotalMinor(1, 5000, 2)).isEqualTo(10000);
    assertThat(RentalPricingPolicy.lineTotalMinor(3, 4000, 2)).isEqualTo(24000);
    assertThat(RentalPricingPolicy.lineTotalMinor(2, 0, 5)).isZero();
    assertThatThrownBy(() -> RentalPricingPolicy.lineTotalMinor(0, 5000, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.lineTotalMinor(1, -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.lineTotalMinor(1, 5000, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.lineTotalMinor(2, Long.MAX_VALUE, 1))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void sumsLinesExactlyAndNeedsAtLeastTheTable() {
    assertThat(RentalPricingPolicy.totalMinor(List.of(10000L, 24000L, 0L))).isEqualTo(34000);
    assertThatThrownBy(() -> RentalPricingPolicy.totalMinor(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.totalMinor(Arrays.asList(1L, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.totalMinor(List.of(1L, -1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RentalPricingPolicy.totalMinor(List.of(Long.MAX_VALUE, 1L)))
        .isInstanceOf(ArithmeticException.class);
  }
}
