package com.chanakanlabs.bgstore.branches;

import java.time.LocalTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A store other modules can stock or schedule against.
 *
 * <p>The design and the UI call these branches; {@code docs/domain-model.md} calls the same thing a
 * location.
 *
 * @param address the postal address guests are shown; null until the store records one
 * @param opensAt Bangkok local time; null exactly when {@code closesAt} is
 * @param phone the public phone number; null until the store records one
 * @param status whether the branch currently accepts bookings
 * @param latitude WGS84 latitude; null until coordinates are recorded
 * @param longitude WGS84 longitude; null until coordinates are recorded
 */
public record Branch(
    UUID id,
    String name,
    @Nullable String address,
    @Nullable LocalTime opensAt,
    @Nullable LocalTime closesAt,
    @Nullable String phone,
    BranchStatus status,
    @Nullable Double latitude,
    @Nullable Double longitude) {

  /** Compatibility constructor for domain callers that do not publish directory metadata. */
  public Branch(
      UUID id,
      String name,
      @Nullable String address,
      @Nullable LocalTime opensAt,
      @Nullable LocalTime closesAt) {
    this(id, name, address, opensAt, closesAt, null, BranchStatus.ACTIVE, null, null);
  }
}
