package com.chanakanlabs.theparlor.rentals.internal.domain;

import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "rentals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rental {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "reservation_id", nullable = false, unique = true)
  private UUID reservationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private RentalStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "planned_ends_at", nullable = false)
  private Instant plannedEndsAt;

  @Column(name = "actual_ends_at")
  private Instant actualEndsAt;

  @Column(name = "checked_in_by", nullable = false)
  private String checkedInBy;

  @Column(name = "checked_out_by")
  private String checkedOutBy;

  @Column(name = "total_minor")
  private Long totalMinor;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
