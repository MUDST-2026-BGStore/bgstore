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
}
