package com.chanakanlabs.theparlor.reservations.internal.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases unpaid table, game and pantry holds; each booking has its own transaction. */
@Component
public class UnpaidReservationExpiry {
  private static final Logger log = LoggerFactory.getLogger(UnpaidReservationExpiry.class);
  private final ReservationsService reservations;

  UnpaidReservationExpiry(ReservationsService reservations) {
    this.reservations = reservations;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  public int expireUnpaidReservations() {
    int cancelled = 0;
    for (var id : reservations.unpaidPastDeadline()) {
      try {
        if (reservations.expireUnpaidReservation(id)) cancelled++;
      } catch (RuntimeException failure) {
        log.warn("Could not expire unpaid reservation {}: {}", id, failure.getMessage());
      }
    }
    return cancelled;
  }
}
