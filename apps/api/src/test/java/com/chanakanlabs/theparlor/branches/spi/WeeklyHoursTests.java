package com.chanakanlabs.theparlor.branches.spi;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.branches.spi.WeeklyHours.Period;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyHoursTests {
  // 2026-11-09 is a Monday.
  private static Instant bangkok(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 11, day, hour, minute).atZone(WeeklyHours.ZONE).toInstant();
  }

  @Test
  void parsesContractTimesAndKeepsEndOfDay() {
    var period = Period.parse(MONDAY, "10:00", "24:00");
    assertThat(period.opensMinute()).isEqualTo(600);
    assertThat(period.closesMinute()).isEqualTo(1440);
    assertThat(period.opensAt()).isEqualTo("10:00");
    assertThat(period.closesAt()).isEqualTo("24:00");
    assertThat(Period.parse(MONDAY, "09:30:00", "24:00:00"))
        .isEqualTo(Period.parse(MONDAY, "09:30", "24:00"));
  }

  @Test
  void rejectsMalformedReversedAndOutOfRangeTimes() {
    for (String[] times :
        new String[][] {
          {"24:00", "24:00"},
          {"12:00", "12:00"},
          {"13:00", "12:00"},
          {"10:60", "12:00"},
          {"10:00", "24:01"},
          {"9:00", "12:00"},
          {"10:00", null}
        })
      assertThatThrownBy(() -> Period.parse(MONDAY, times[0], times[1]))
          .as("%s-%s", times[0], times[1])
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Period.parse(null, "10:00", "12:00"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOverlapsButAllowsAdjacentPeriodsAndSortsThem() {
    assertThatThrownBy(
            () ->
                new WeeklyHours(
                    List.of(
                        Period.parse(MONDAY, "10:00", "14:00"),
                        Period.parse(MONDAY, "13:59", "18:00"))))
        .isInstanceOf(IllegalArgumentException.class);
    var hours =
        new WeeklyHours(
            List.of(
                Period.parse(TUESDAY, "10:00", "12:00"),
                Period.parse(MONDAY, "14:00", "18:00"),
                Period.parse(MONDAY, "10:00", "14:00")));
    assertThat(hours.periods())
        .extracting(Period::day, Period::opensAt)
        .containsExactly(tuple(MONDAY, "10:00"), tuple(MONDAY, "14:00"), tuple(TUESDAY, "10:00"));
  }

  @Test
  void coversOnlyIntervalsInsideContiguousOpeningTime() {
    var hours =
        new WeeklyHours(
            List.of(
                Period.parse(MONDAY, "10:00", "14:00"),
                Period.parse(MONDAY, "14:00", "24:00"),
                Period.parse(TUESDAY, "00:00", "02:00"),
                Period.parse(TUESDAY, "12:00", "21:00")));
    assertThat(hours.covers(bangkok(9, 10, 0), bangkok(9, 12, 0))).isTrue();
    // Adjacent periods and the midnight boundary are contiguous.
    assertThat(hours.covers(bangkok(9, 13, 0), bangkok(10, 2, 0))).isTrue();
    assertThat(hours.covers(bangkok(9, 9, 59), bangkok(9, 12, 0))).isFalse();
    assertThat(hours.covers(bangkok(9, 23, 0), bangkok(10, 2, 1))).isFalse();
    assertThat(hours.covers(bangkok(10, 1, 0), bangkok(10, 13, 0))).isFalse();
    assertThat(hours.covers(bangkok(11, 12, 0), bangkok(11, 13, 0))).isFalse();
    assertThat(hours.covers(bangkok(9, 12, 0), bangkok(9, 12, 0))).isFalse();
  }
}
