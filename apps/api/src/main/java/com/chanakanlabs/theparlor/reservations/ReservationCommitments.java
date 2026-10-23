package com.chanakanlabs.theparlor.reservations;

import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only module interface for catalog retirement checks. */
@Service
@Transactional(readOnly = true)
public class ReservationCommitments {
  private final ReservationRepository repository;

  public ReservationCommitments(ReservationRepository repository) {
    this.repository = repository;
  }

  public boolean hasConfirmedGame(UUID gameId) {
    return repository.hasConfirmedGame(gameId);
  }

  public java.util.Set<UUID> reservationsUsingGame(UUID gameId) {
    return repository.reservationsUsingGame(gameId);
  }
}
