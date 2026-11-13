package com.chanakanlabs.theparlor.branches.internal.domain;

import com.chanakanlabs.theparlor.branches.spi.WeeklyHours;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "branch_opening_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchOpeningHours {
  @EmbeddedId private BranchOpeningHoursId id;

  // PostgreSQL stores a 24:00 close as TIME '24:00:00', which java.time.LocalTime and
  // java.sql.Time cannot represent (the driver reads it back as midnight). Exchange the column
  // as text so the end of day survives the round trip.
  @Column(name = "closes_at", nullable = false, columnDefinition = "time")
  @ColumnTransformer(read = "cast(closes_at as text)", write = "cast(? as time)")
  private String closesAt;

  public static BranchOpeningHours of(UUID branchId, WeeklyHours.Period period) {
    var hours = new BranchOpeningHours();
    hours.id =
        new BranchOpeningHoursId(
            java.util.Objects.requireNonNull(branchId),
            period.day(),
            LocalTime.ofSecondOfDay(period.opensMinute() * 60L));
    hours.closesAt = period.closesAt();
    return hours;
  }

  public void closeAt(WeeklyHours.Period period) {
    closesAt = period.closesAt();
  }

  public WeeklyHours.Period toPeriod() {
    return WeeklyHours.Period.parse(
        id.dayOfWeek(), id.opensAt().toString().substring(0, 5), closesAt);
  }
}
