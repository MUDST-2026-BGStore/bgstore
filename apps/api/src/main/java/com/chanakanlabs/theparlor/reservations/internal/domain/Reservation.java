package com.chanakanlabs.theparlor.reservations.internal.domain;

import com.chanakanlabs.theparlor.shared.ContactStorageLimits;
import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import com.chanakanlabs.theparlor.shared.WorkflowTextLimits;
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
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "customer_subject")
  private String customerSubject;

  @Column(name = "guest_name", length = ContactStorageLimits.MAX_SNAPSHOT_NAME_LENGTH)
  private String guestName;

  @Column(name = "guest_phone", length = ContactStorageLimits.MAX_PHONE_LENGTH)
  private String guestPhone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.SOURCE)
  private ReservationSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private ReservationStatus status;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "party_size", nullable = false)
  private int partySize;

  @Column(name = "table_id", nullable = false)
  private UUID tableId;

  @Column(name = "table_hourly_rate_minor", nullable = false)
  private Long tableHourlyRateMinor;

  @Column(length = WorkflowTextLimits.MAX_NOTES_LENGTH)
  private String notes;

  @Column(name = "created_by_subject", nullable = false)
  private String createdBySubject;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "checked_in_by")
  private String checkedInBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by")
  private String cancelledBy;

  @Column(name = "cancellation_reason", length = WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH)
  private String cancellationReason;

  @Column(name = "no_show_at")
  private Instant noShowAt;

  @Column(name = "no_show_by")
  private String noShowBy;

  @Column(name = "no_show_reason", length = WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH)
  private String noShowReason;

  @Column(name = "completed_at")
  private Instant completedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Largest party one booking may seat, matching the storage constraint. */
  public static final int MAX_PARTY_SIZE = 20;

  /**
   * Who the visit is for: a registered customer (customerId and subject) or a guest (guestName,
   * optional guestPhone), never both.
   */
  public record Party(UUID customerId, String subject, String guestName, String guestPhone) {
    public Party {
      guestName = text(guestName, ContactStorageLimits.MAX_SNAPSHOT_NAME_LENGTH, "Guest name");
      guestPhone = text(guestPhone, ContactStorageLimits.MAX_PHONE_LENGTH, "Guest phone");
      if ((customerId == null) != (subject == null))
        throw new IllegalArgumentException("A registered customer needs a record and a subject");
      if ((customerId == null) == (guestName == null))
        throw new IllegalArgumentException("A booking is for a registered customer or a guest");
    }
  }

  /** The planned part of a booking, replaced as a whole by rescheduling. */
  public record Plan(
      UUID tableId, Instant startsAt, Instant endsAt, int partySize, long tableHourlyRateMinor) {
    public Plan {
      java.util.Objects.requireNonNull(tableId);
      // PostgreSQL keeps microseconds; never answer with precision that is not stored.
      if (startsAt != null) startsAt = startsAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      if (endsAt != null) endsAt = endsAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
        throw new IllegalArgumentException("A booking must end after it starts");
      if (partySize < 1 || partySize > MAX_PARTY_SIZE)
        throw new IllegalArgumentException("Party size must be between 1 and " + MAX_PARTY_SIZE);
      if (tableHourlyRateMinor < 0)
        throw new IllegalArgumentException("Table rate must be nonnegative");
    }
  }

  /** Confirms a booking whose table and copies the caller has already secured. */
  public static Reservation book(
      UUID branchId,
      Party party,
      ReservationSource source,
      Plan plan,
      String notes,
      String createdBySubject) {
    if (createdBySubject == null || createdBySubject.isBlank())
      throw new IllegalArgumentException("Bookings need a creator");
    var reservation = new Reservation();
    reservation.branchId = java.util.Objects.requireNonNull(branchId);
    reservation.customerId = party.customerId();
    reservation.customerSubject = party.subject();
    reservation.guestName = party.guestName();
    reservation.guestPhone = party.guestPhone();
    reservation.source = java.util.Objects.requireNonNull(source);
    reservation.status = ReservationStatus.CONFIRMED;
    reservation.createdBySubject = createdBySubject;
    reservation.apply(plan, notes);
    return reservation;
  }

  /** Replaces the plan and captures the table's current rate; only while still confirmed. */
  public void reschedule(Plan plan, String notes) {
    requireConfirmed("rescheduled");
    apply(plan, notes);
  }

  /** Returns false when already cancelled; any other state than confirmed cannot be. */
  public boolean cancel(String actor, String reason, Instant now) {
    if (status == ReservationStatus.CANCELLED) return false;
    requireConfirmed("cancelled");
    // Validate everything before changing anything, so a rejected call leaves no trace.
    String stated = reason(reason);
    status = ReservationStatus.CANCELLED;
    cancelledAt = java.util.Objects.requireNonNull(now);
    cancelledBy = java.util.Objects.requireNonNull(actor);
    cancellationReason = stated;
    return true;
  }

  /** Returns false when already a no-show. A booking cannot be missed before it starts. */
  public boolean markNoShow(String actor, String reason, Instant now) {
    if (status == ReservationStatus.NO_SHOW) return false;
    requireConfirmed("marked as a no-show");
    if (now.isBefore(startsAt))
      throw new IllegalStateException("A reservation cannot be a no-show before it starts");
    String stated = reason(reason);
    status = ReservationStatus.NO_SHOW;
    noShowAt = now;
    noShowBy = java.util.Objects.requireNonNull(actor);
    noShowReason = stated;
    return true;
  }

  /** The party arrived within its window. Early arrivals are rescheduled by staff first. */
  public void checkIn(String actor, Instant now) {
    requireConfirmed("checked in");
    if (now.isBefore(startsAt) || !now.isBefore(endsAt))
      throw new IllegalStateException("Check-in is only possible during the booked window");
    status = ReservationStatus.CHECKED_IN;
    checkedInAt = now;
    checkedInBy = java.util.Objects.requireNonNull(actor);
  }

  /** Staff checked the visit out; repeating it changes nothing. */
  public void complete(Instant now) {
    if (status == ReservationStatus.COMPLETED) return;
    if (status != ReservationStatus.CHECKED_IN)
      throw new IllegalStateException("Only a checked-in reservation can be completed");
    status = ReservationStatus.COMPLETED;
    completedAt = java.util.Objects.requireNonNull(now);
  }

  public boolean isConfirmed() {
    return status == ReservationStatus.CONFIRMED;
  }

  private void apply(Plan plan, String notes) {
    tableId = plan.tableId();
    startsAt = plan.startsAt();
    endsAt = plan.endsAt();
    partySize = plan.partySize();
    tableHourlyRateMinor = plan.tableHourlyRateMinor();
    this.notes = text(notes, WorkflowTextLimits.MAX_NOTES_LENGTH, "Notes");
  }

  private void requireConfirmed(String action) {
    if (status != ReservationStatus.CONFIRMED)
      throw new IllegalStateException(
          "A "
              + status.name().toLowerCase().replace('_', ' ')
              + " reservation cannot be "
              + action);
  }

  private static String reason(String reason) {
    String stripped = text(reason, WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH, "Reason");
    if (stripped == null) throw new IllegalArgumentException("A reason is required");
    return stripped;
  }

  private static String text(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) return null;
    String stripped = value.strip();
    if (stripped.length() > maxLength)
      throw new IllegalArgumentException(field + " is longer than " + maxLength + " characters");
    return stripped;
  }
}
