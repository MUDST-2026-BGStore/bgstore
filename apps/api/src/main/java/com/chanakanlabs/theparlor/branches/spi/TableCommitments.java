package com.chanakanlabs.theparlor.branches.spi;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Implemented by modules that commit branch tables to customers. Branches calls every
 * implementation inside its own transaction, holding a write lock on the branch or table row being
 * changed. A workflow that creates or moves a commitment must take the same row lock first,
 * otherwise a booking can slip in between the check and the change.
 *
 * <p>An open commitment is a visit that still needs its table: checked in and not yet checked out,
 * or confirmed and not yet over.
 */
public interface TableCommitments {
  /** Tables with a checked-in visit, which stay occupied past the planned end until checkout. */
  Set<UUID> occupiedTables(UUID branchId);

  /** Tables with a confirmed booking whose window contains the instant. */
  Set<UUID> reservedTables(UUID branchId, Instant at);

  /** Whether an open commitment at the table seats more than this; zero asks for any at all. */
  boolean hasPartyLargerThan(UUID tableId, int seats);

  boolean hasOpenCommitments(UUID branchId);

  /** Whether an open commitment at the branch would fall outside these opening hours. */
  boolean hasOpenCommitmentsOutside(UUID branchId, WeeklyHours hours);
}
