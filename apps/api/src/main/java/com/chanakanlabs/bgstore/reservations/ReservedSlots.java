package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Repository port for the slots reservations hold on tables. */
interface ReservedSlots {

  /**
   * The slots of each table that have not ended by {@code now}, soonest first. A table with none is
   * left out of the map.
   */
  Map<Long, List<ReservedSlot>> upcomingFor(Collection<Long> tableIds, OffsetDateTime now);

  /** Releases any table holds belonging to a cancelled reservation. */
  void releaseFor(String reservationId);
}
