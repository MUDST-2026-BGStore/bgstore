package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.contracts.model.BranchAvailability;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Advisory interval availability; booking must revalidate and hold resources atomically. */
@Service
@Transactional(readOnly = true)
public class RentalAvailabilityService {
  public BranchAvailability getBranchAvailability(
      UUID branchId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      Integer partySize,
      @Nullable Set<UUID> gameIds) {
    // TODO(rental-availability): validate branch, game IDs, party size and [startsAt,endsAt).
    // Use Asia/Bangkok opening hours and a server Clock; reject invalid bounds and unknown IDs.
    // Read branch tables, offering rates and usable copy IDs through public module interfaces;
    // add allowedDependencies when those interfaces exist, without importing other internals.
    // Exclude overlapping confirmed reservations and active rentals; active rentals retain their
    // resources until checkout regardless of planned end. Disabled offerings have zero
    // availability.
    // Return no customer details; distinguish "available now" from "free for the entire interval".
    // TODO(rental-holds): booking must persist physical copy holds and serialize allocation across
    // booking, rescheduling, copy-condition changes and check-in. Current rental_copy_allocations
    // references rentals only: add reservation hold storage in a NEW migration before implementing
    // that workflow. Never treat this read as a guarantee or substitute a quantity sum for copy
    // IDs.
    throw new UnsupportedOperationException("Rental interval availability is not implemented yet");
  }
}
