package com.chanakanlabs.bgstore.branches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * JPA mapping for the {@code branch} table. The address and hours columns come from this entity
 * ({@code ddl-auto=update}), not from a migration.
 */
@Entity
@Table(name = "branch")
@SuppressWarnings("NullAway.Init")
class BranchRecord {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 120, unique = true)
  private String name;

  @Nullable
  @Column(name = "address", length = 300)
  private String address;

  @Nullable
  @Column(name = "opens_at")
  private LocalTime opensAt;

  @Nullable
  @Column(name = "closes_at")
  private LocalTime closesAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected BranchRecord() {}

  BranchRecord(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  /** Records the details the branch is missing, keeping any it already has. */
  void fillMissingDetails(
      @Nullable String address, @Nullable LocalTime opensAt, @Nullable LocalTime closesAt) {
    if (this.address == null) {
      this.address = address;
    }
    // The hours are one fact, so they are only ever recorded as a pair.
    if (this.opensAt == null && this.closesAt == null) {
      this.opensAt = opensAt;
      this.closesAt = closesAt;
    }
  }

  Branch toBranch() {
    return new Branch(id, name, address, opensAt, closesAt);
  }
}
