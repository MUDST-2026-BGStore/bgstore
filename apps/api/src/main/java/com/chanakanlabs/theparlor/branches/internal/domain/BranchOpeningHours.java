package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "branch_opening_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchOpeningHours {
  @EmbeddedId private BranchOpeningHoursId id;

  @Column(name = "closes_at", nullable = false)
  private LocalTime closesAt;
}
