package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Deterministic stand-in; the JPA adapter is covered by FloorOverviewApiIntegrationTest. */
final class InMemoryReservedSlots implements ReservedSlots {

  private final Map<Long, List<ReservedSlot>> slots = new HashMap<>();

  void add(long tableId, ReservedSlot slot) {
    slots.computeIfAbsent(tableId, id -> new ArrayList<>()).add(slot);
  }

  @Override
  public Map<Long, List<ReservedSlot>> upcomingFor(Collection<Long> tableIds, OffsetDateTime now) {
    return tableIds.stream()
        .filter(slots::containsKey)
        .collect(
            Collectors.toMap(
                id -> id,
                id ->
                    slots.get(id).stream()
                        .filter(slot -> slot.endsAt().isAfter(now))
                        .sorted(Comparator.comparing(ReservedSlot::startsAt))
                        .toList()));
  }
}
