package com.chanakanlabs.bgstore.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Application input for the future staff-created reservation workflow.
 *
 * <p>The authenticated staff subject is intentionally absent. A future implementation must derive
 * it from the server-side session through the identity module and must never trust browser input
 * for staff identity.
 */
public record CreateStaffReservationCommand(
    String clientSubject,
    UUID locationId,
    LocalDate reservationDate,
    LocalTime startTime,
    LocalTime endTime,
    int partySize,
    UUID tableId) {}
