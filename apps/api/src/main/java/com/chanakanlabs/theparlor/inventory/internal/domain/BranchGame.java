package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@DynamicUpdate
@Table(name = "branch_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchGame {
  // TODO(inventory-domain): add create/configure methods that require IDs and a nonnegative
  // hourly rate. Offering changes must preserve stock/copies and existing price snapshots.
  @EmbeddedId private BranchGameId id;

  @Column(name = "sale_enabled", nullable = false)
  private boolean saleEnabled;

  @Column(name = "rental_enabled", nullable = false)
  private boolean rentalEnabled;

  @Column(name = "staff_can_teach", nullable = false)
  private boolean staffCanTeach;

  @Column(name = "rental_hourly_rate_minor", nullable = false)
  private Long rentalHourlyRateMinor;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
