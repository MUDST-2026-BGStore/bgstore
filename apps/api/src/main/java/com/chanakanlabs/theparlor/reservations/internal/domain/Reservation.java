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

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
