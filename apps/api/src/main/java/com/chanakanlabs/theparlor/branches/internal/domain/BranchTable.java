package com.chanakanlabs.theparlor.branches.internal.domain;

import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "branch_tables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTable {
  /** Maximum table label length within a branch. */
  public static final int MAX_LABEL_LENGTH = 80;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(nullable = false, length = MAX_LABEL_LENGTH)
  private String label;

  @Column(nullable = false)
  private int seats;

  @Column(name = "hourly_rate_minor", nullable = false)
  private Long hourlyRateMinor;

  @Enumerated(EnumType.STRING)
  @Column(name = "operational_status", nullable = false, length = EnumStorageLengths.STATUS)
  private TableOperationalStatus operationalStatus;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static BranchTable create(
      UUID branchId,
      String label,
      int seats,
      long hourlyRateMinor,
      TableOperationalStatus operationalStatus) {
    var table = new BranchTable();
    table.branchId = java.util.Objects.requireNonNull(branchId);
    table.configure(label, seats, hourlyRateMinor);
    table.changeOperationalStatus(operationalStatus);
    return table;
  }

  /**
   * Changes label, capacity and rate. Callers must first confirm that a smaller capacity still
   * seats every open commitment; a new rate applies only to later bookings, which snapshot it.
   */
  public void configure(String label, int seats, long hourlyRateMinor) {
    if (label == null || label.isBlank())
      throw new IllegalArgumentException("Table label is required");
    String stripped = label.strip();
    if (stripped.length() > MAX_LABEL_LENGTH)
      throw new IllegalArgumentException(
          "Table label is longer than " + MAX_LABEL_LENGTH + " characters");
    if (seats < 1) throw new IllegalArgumentException("Table capacity must be at least 1");
    if (hourlyRateMinor < 0) throw new IllegalArgumentException("Hourly rate must be nonnegative");
    this.label = stripped;
    this.seats = seats;
    this.hourlyRateMinor = hourlyRateMinor;
  }

  /** Callers must first confirm that no open commitment needs a table going out of service. */
  public void changeOperationalStatus(TableOperationalStatus operationalStatus) {
    this.operationalStatus = java.util.Objects.requireNonNull(operationalStatus);
  }

  public boolean isInService() {
    return operationalStatus == TableOperationalStatus.IN_SERVICE;
  }
}
