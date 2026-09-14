package com.chanakanlabs.bgstore.reservations.spi;

import com.chanakanlabs.bgstore.reservations.application.ReservationAvailabilityQuery;
import com.chanakanlabs.bgstore.reservations.domain.TableAvailability;
import java.util.List;

/** Outbound boundary for a future table-availability adapter. */
public interface ReservationAvailabilityPort {

  List<TableAvailability> findAvailableTables(ReservationAvailabilityQuery query);
}
