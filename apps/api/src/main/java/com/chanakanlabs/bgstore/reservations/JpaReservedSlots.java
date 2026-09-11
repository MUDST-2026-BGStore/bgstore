package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaReservedSlots implements ReservedSlots {

  private final TableReservationJpaRepository database;

  JpaReservedSlots(TableReservationJpaRepository database) {
    this.database = database;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, List<ReservedSlot>> upcomingFor(Collection<Long> tableIds, OffsetDateTime now) {
    if (tableIds.isEmpty()) {
      return Map.of();
    }
    // The query orders by start time and groupingBy keeps encounter order, so each list stays
    // soonest first.
    return database.findUpcoming(tableIds, now).stream()
        .collect(
            Collectors.groupingBy(
                TableReservationEntity::tableId,
                Collectors.mapping(TableReservationEntity::toSlot, Collectors.toList())));
  }
}
