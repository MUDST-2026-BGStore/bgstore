package com.chanakanlabs.bgstore.tables;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TableRecordData(
    @Nullable Long id,
    String name,
    String branch,
    int capacity,
    String shape,
    String status,
    boolean active,
    OffsetDateTime lastUpdated,
    @Nullable UUID branchId) {

  /** Compatibility constructor for in-memory adapters and older module tests. */
  public TableRecordData(
      @Nullable Long id,
      String name,
      String branch,
      int capacity,
      String shape,
      String status,
      boolean active,
      OffsetDateTime lastUpdated) {
    this(id, name, branch, capacity, shape, status, active, lastUpdated, null);
  }
}
