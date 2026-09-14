package com.chanakanlabs.bgstore.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Criteria for the future staff table-availability query. */
public record ReservationAvailabilityQuery(
    UUID locationId,
    LocalDate reservationDate,
    LocalTime startTime,
    LocalTime endTime,
    int partySize) {}
