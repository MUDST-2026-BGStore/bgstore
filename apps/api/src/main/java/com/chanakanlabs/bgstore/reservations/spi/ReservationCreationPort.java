package com.chanakanlabs.bgstore.reservations.spi;

import com.chanakanlabs.bgstore.reservations.application.CreateStaffReservationCommand;
import java.util.UUID;

/** Outbound persistence boundary for the future create-reservation application service. */
public interface ReservationCreationPort {

  UUID create(CreateStaffReservationCommand command, String authenticatedStaffSubject);
}
