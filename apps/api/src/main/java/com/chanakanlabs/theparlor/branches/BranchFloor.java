package com.chanakanlabs.theparlor.branches;

import com.chanakanlabs.theparlor.branches.spi.WeeklyHours;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Module interface for booking against a branch's tables and opening hours. */
public interface BranchFloor {
  /** Every table of the branch, in a stable order. */
  List<TableInfo> tables(UUID branchId);

  /**
   * Write-locks the table for the caller's transaction. Every workflow that commits a table takes
   * this lock first, so bookings of one table and changes to it happen one at a time.
   */
  Optional<TableInfo> lockTable(UUID branchId, UUID tableId);

  WeeklyHours openingHours(UUID branchId);

  record TableInfo(UUID id, String label, int seats, long hourlyRateMinor, boolean inService) {}
}
