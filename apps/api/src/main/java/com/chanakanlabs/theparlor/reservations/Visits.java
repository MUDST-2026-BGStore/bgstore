package com.chanakanlabs.theparlor.reservations;

import com.chanakanlabs.theparlor.contracts.model.CustomerReference;
import com.chanakanlabs.theparlor.contracts.model.Reservation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Module interface the rentals module uses to bill and finish a checked-in visit. */
public interface Visits {
  /** What each visit booked and the rates locked for it, by reservation ID. */
  Map<UUID, Booked> booked(Collection<UUID> reservationIds);

  /**
   * Completes the checked-in reservation at checkout, inside the caller's transaction, and returns
   * it. Completing it again changes nothing.
   */
  Reservation complete(UUID reservationId, Instant at);

  Reservation get(UUID reservationId);

  /**
   * customerSubject is null for a guest. Captured PromptPay play credits set the booked-hour
   * minimum; legacy and staff bookings have a one-hour minimum and no prepaid credit.
   */
  record Booked(
      UUID reservationId,
      UUID tableId,
      CustomerReference customer,
      String customerSubject,
      long tableHourlyRateMinor,
      List<GameLine> games,
      int minimumBillableHours,
      long prepaidPlayMinor) {}

  record GameLine(UUID gameId, int quantity, long unitHourlyRateMinor) {}
}
