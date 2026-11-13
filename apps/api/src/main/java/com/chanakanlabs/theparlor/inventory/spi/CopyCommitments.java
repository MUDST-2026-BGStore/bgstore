package com.chanakanlabs.theparlor.inventory.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implemented by modules that commit physical rental copies to customers. Inventory calls every
 * implementation inside its own transaction while holding write locks on all of the game's copy
 * rows at the branch. A workflow that creates or moves a commitment must lock the copy rows it
 * commits first, otherwise it can race a condition change.
 */
public interface CopyCommitments {
  /** How a copy is committed at one instant. */
  enum Use {
    /** Out with an active rental, until checkout. */
    IN_USE,
    /** Held by a confirmed reservation whose window contains the instant. */
    RESERVED
  }

  /**
   * One open commitment of a copy over [from, until). An active rental's until is its planned end,
   * or now once it has overrun: it keeps its copies until checkout.
   */
  record Commitment(UUID id, Instant from, Instant until) {}

  /** Copies of the branch committed at the instant; uncommitted copies are absent. */
  Map<UUID, Use> usesAt(UUID branchId, Instant at);

  /** Commitments of the copy that are not over yet; they never overlap each other. */
  List<Commitment> openCommitments(UUID branchId, UUID copyId);

  /**
   * Copies of the branch this module has committed for any part of [from, until). An active rental
   * blocks intervals starting before its planned end, and before now once it overruns.
   */
  Set<UUID> busyCopies(UUID branchId, Instant from, Instant until);

  /** Whether this module has no open commitment of the copy overlapping [from, until). */
  default boolean isFree(UUID branchId, UUID copyId, Instant from, Instant until) {
    return !busyCopies(branchId, from, until).contains(copyId);
  }

  /**
   * Moves one of this module's commitments to another copy of the same game. The caller has checked
   * the target is free in every module and rolls back the transaction on failure.
   */
  void move(Commitment commitment, UUID branchId, UUID fromCopyId, UUID toCopyId);
}
