package com.chanakanlabs.theparlor.inventory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Module interface for booking a branch's rental copies. */
public interface RentalLibrary {
  /**
   * The shelf of each requested game at the branch. A game the branch does not offer has an empty,
   * disabled shelf. Unknown or inactive catalog games are a 404.
   */
  List<Shelf> shelves(UUID branchId, Collection<UUID> gameIds);

  /** The shelf of every active game the branch offers. */
  List<Shelf> allShelves(UUID branchId);

  /**
   * As shelves(), and write-locks every copy of those games at the branch for the caller's
   * transaction. Every workflow that commits copies takes this lock first, so bookings, check-ins
   * and condition changes of one game happen one at a time.
   */
  List<Shelf> lockShelves(UUID branchId, Collection<UUID> gameIds);

  /** usableCopyIds are in a stable order; whether each is free for an interval is not known. */
  record Shelf(
      UUID gameId,
      boolean offered,
      boolean rentalEnabled,
      long hourlyRateMinor,
      List<UUID> usableCopyIds) {}
}
