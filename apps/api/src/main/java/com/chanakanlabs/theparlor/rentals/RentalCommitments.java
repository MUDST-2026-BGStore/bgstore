package com.chanakanlabs.theparlor.rentals;

import com.chanakanlabs.theparlor.rentals.internal.persistence.RentalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only module interface for catalog retirement checks. */
@Service
@Transactional(readOnly = true)
public class RentalCommitments {
  private final RentalRepository repository;

  public RentalCommitments(RentalRepository repository) {
    this.repository = repository;
  }

  public boolean hasActiveRental(java.util.Set<UUID> reservationIds) {
    return !reservationIds.isEmpty() && repository.hasActiveReservation(reservationIds);
  }
}
