package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.catalog.spi.GameCommitments;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells catalog whether a game is committed to a confirmed or checked-in reservation. */
@Service
@Transactional(readOnly = true)
class ReservationGameCommitments implements GameCommitments {
  private final ReservationRepository reservations;

  ReservationGameCommitments(ReservationRepository reservations) {
    this.reservations = reservations;
  }

  @Override
  public boolean hasOpenCommitments(UUID gameId) {
    return reservations.hasOpenGame(gameId);
  }
}
