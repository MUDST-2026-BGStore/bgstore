package com.chanakanlabs.bgstore.reservations.application;

import com.chanakanlabs.bgstore.reservations.domain.TableAvailability;
import java.util.List;
import java.util.UUID;

/**
 * Inbound boundary for the future staff reservation API.
 *
 * <p>The implementation will enforce {@code AccessPolicy.requireStaffOrManager()} before querying
 * operational availability or creating a reservation. No implementation is registered yet because
 * the HTTP contract and persistence model have not been finalized.
 */
public interface StaffReservationUseCase {

  List<TableAvailability> findAvailableTables(ReservationAvailabilityQuery query);

  UUID createReservation(CreateStaffReservationCommand command);
}
