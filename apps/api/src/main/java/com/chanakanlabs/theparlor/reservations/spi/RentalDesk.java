package com.chanakanlabs.theparlor.reservations.spi;

import com.chanakanlabs.theparlor.contracts.model.Rental;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the rentals module. Reservations calls it inside the check-in transaction, holding
 * write locks on the reservation, its table and every copy of its games.
 */
public interface RentalDesk {
  /**
   * Starts the visit's one rental and takes over its copies. Throws a 409 when any copy is still
   * out with another active rental, such as an overdue visit.
   */
  Rental start(Arrival arrival);

  /** The rental of a reservation that has been checked in. */
  Optional<Rental> ofReservation(UUID reservationId);

  /** Rental IDs by reservation ID, for the reservations that have one. */
  Map<UUID, UUID> rentalIds(Collection<UUID> reservationIds);

  record Arrival(
      UUID reservationId,
      UUID branchId,
      Instant startedAt,
      Instant plannedEndsAt,
      String staffSubject,
      List<UUID> copyIds) {}
}
