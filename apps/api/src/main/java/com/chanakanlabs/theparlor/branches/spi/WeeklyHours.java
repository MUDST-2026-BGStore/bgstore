package com.chanakanlabs.theparlor.branches.spi;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * A branch's weekly opening periods in local shop time. Periods on one day never overlap; days
 * without a period are closed. Overnight opening is two periods meeting at midnight.
 */
public record WeeklyHours(List<Period> periods) {
  /** Every branch is in Thailand, which has no daylight saving time. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

  /** Maximum periods per week, matching the API contract. */
  public static final int MAX_PERIODS = 28;

  private static final int MINUTES_PER_DAY = 24 * 60;
  private static final long NANOS_PER_MINUTE = 60_000_000_000L;

  /** Minutes from local midnight; the end is exclusive and may be 1440 (24:00). */
  public record Period(DayOfWeek day, int opensMinute, int closesMinute) {
    public Period {
      if (day == null) throw new IllegalArgumentException("Opening period needs a day");
      if (opensMinute < 0 || closesMinute > MINUTES_PER_DAY || opensMinute >= closesMinute)
        throw new IllegalArgumentException("Opening period must close after it opens");
    }

    /** Parses contract times: HH:mm, with 24:00 allowed only as a closing time. */
    public static Period parse(DayOfWeek day, String opensAt, String closesAt) {
      return new Period(day, minutes(opensAt, false), minutes(closesAt, true));
    }

    public String opensAt() {
      return format(opensMinute);
    }

    public String closesAt() {
      return format(closesMinute);
    }

    private static int minutes(String value, boolean allowEndOfDay) {
      if (value == null || !value.matches("\\d{2}:\\d{2}(:00(\\.0+)?)?"))
        throw new IllegalArgumentException("Opening times use HH:mm");
      int hours = Integer.parseInt(value, 0, 2, 10);
      int minutes = Integer.parseInt(value, 3, 5, 10);
      int total = hours * 60 + minutes;
      if (minutes > 59 || total > MINUTES_PER_DAY || (total == MINUTES_PER_DAY && !allowEndOfDay))
        throw new IllegalArgumentException("Opening time is out of range");
      return total;
    }

    private static String format(int minute) {
      return "%02d:%02d".formatted(minute / 60, minute % 60);
    }
  }

  public WeeklyHours {
    if (periods == null || periods.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Opening periods cannot be null");
    if (periods.size() > MAX_PERIODS)
      throw new IllegalArgumentException("At most " + MAX_PERIODS + " opening periods are allowed");
    periods =
        periods.stream()
            .sorted(Comparator.comparing(Period::day).thenComparingInt(Period::opensMinute))
            .toList();
    for (int i = 1; i < periods.size(); i++) {
      Period previous = periods.get(i - 1);
      Period current = periods.get(i);
      if (previous.day() == current.day() && current.opensMinute() < previous.closesMinute())
        throw new IllegalArgumentException("Opening periods must not overlap");
    }
  }

  /** Whether the branch is open for the whole of [start, end), across midnight if contiguous. */
  public boolean covers(Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) return false;
    var cursor = start.atZone(ZONE);
    var until = end.atZone(ZONE);
    // Each step consumes a whole period, so a week of periods bounds the walk per week covered.
    while (cursor.isBefore(until)) {
      var day = cursor.getDayOfWeek();
      long nanoOfDay = cursor.toLocalTime().toNanoOfDay();
      Period open = null;
      for (Period period : periods) {
        if (period.day() == day
            && period.opensMinute() * NANOS_PER_MINUTE <= nanoOfDay
            && nanoOfDay < period.closesMinute() * NANOS_PER_MINUTE) {
          open = period;
          break;
        }
      }
      if (open == null) return false;
      cursor = cursor.toLocalDate().atStartOfDay(ZONE).plusMinutes(open.closesMinute());
    }
    return true;
  }
}
