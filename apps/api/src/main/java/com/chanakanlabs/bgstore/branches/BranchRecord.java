package com.chanakanlabs.bgstore.branches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

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

  @Nullable
  @Column(name = "phone", length = 32)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private BranchStatus status;

  @Nullable
  @Column(name = "latitude")
  private Double latitude;

  @Nullable
  @Column(name = "longitude")
  private Double longitude;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected BranchRecord() {}

  BranchRecord(
      UUID id,
      String name,
      @Nullable String address,
      @Nullable LocalTime opensAt,
      @Nullable LocalTime closesAt) {
    this(id, name, address, opensAt, closesAt, null, BranchStatus.ACTIVE, null, null);
  }

  BranchRecord(
      UUID id,
      String name,
      @Nullable String address,
      @Nullable LocalTime opensAt,
      @Nullable LocalTime closesAt,
      @Nullable String phone,
      BranchStatus status,
      @Nullable Double latitude,
      @Nullable Double longitude) {
    this.id = id;
    this.name = name;
    this.address = address;
    this.opensAt = opensAt;
    this.closesAt = closesAt;
    this.phone = phone;
    this.status = status;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  Branch toBranch() {
    return new Branch(id, name, address, opensAt, closesAt, phone, status, latitude, longitude);
  }
}
