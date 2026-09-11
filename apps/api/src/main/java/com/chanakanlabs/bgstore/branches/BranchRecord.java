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

/** JPA mapping for the migration-owned {@code branch} table. */
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

  Branch toBranch() {
    return new Branch(id, name, address, opensAt, closesAt);
  }
}
