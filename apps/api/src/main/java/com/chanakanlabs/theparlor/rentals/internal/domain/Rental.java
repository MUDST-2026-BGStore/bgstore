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

  /** Starts the one rental of a checked-in reservation at the server's check-in time. */
  public static Rental start(
      UUID branchId,
      UUID reservationId,
      Instant startedAt,
      Instant plannedEndsAt,
      String checkedInBy) {
    if (checkedInBy == null || checkedInBy.isBlank())
      throw new IllegalArgumentException("Rentals are started by staff");
    var rental = new Rental();
    rental.branchId = java.util.Objects.requireNonNull(branchId);
    rental.reservationId = java.util.Objects.requireNonNull(reservationId);
    rental.status = RentalStatus.ACTIVE;
    rental.startedAt = java.util.Objects.requireNonNull(startedAt);
    rental.plannedEndsAt = java.util.Objects.requireNonNull(plannedEndsAt);
    rental.checkedInBy = checkedInBy;
    return rental;
  }

  public boolean isActive() {
    return status == RentalStatus.ACTIVE;
  }

  /** Ends the rental with its final total; only an active rental can be completed. */
  public void complete(Instant endedAt, String checkedOutBy, long totalMinor) {
    if (!isActive()) throw new IllegalStateException("Rental is already completed");
    if (endedAt.isBefore(startedAt))
      throw new IllegalArgumentException("Checkout cannot be before check-in");
    if (checkedOutBy == null || checkedOutBy.isBlank() || totalMinor < 0)
      throw new IllegalArgumentException("Checkout needs a staff member and a total");
    status = RentalStatus.COMPLETED;
    actualEndsAt = endedAt;
    this.checkedOutBy = checkedOutBy;
    this.totalMinor = totalMinor;
  }
}
