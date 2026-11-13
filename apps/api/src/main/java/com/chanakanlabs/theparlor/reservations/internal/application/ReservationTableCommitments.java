package com.chanakanlabs.theparlor.reservations.internal.application;

import static com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CHECKED_IN;
import static com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED;

import com.chanakanlabs.theparlor.branches.spi.TableCommitments;
import com.chanakanlabs.theparlor.branches.spi.WeeklyHours;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells branches which tables reservations have committed. A checked-in reservation is the active
 * rental's table, so rentals need no implementation of their own.
 */
@Service
@Transactional(readOnly = true)
class ReservationTableCommitments implements TableCommitments {
  private final ReservationRepository reservations;
  private final Clock clock;

  ReservationTableCommitments(ReservationRepository reservations, Clock clock) {
    this.reservations = reservations;
    this.clock = clock;
  }

  @Override
  public Set<UUID> occupiedTables(UUID branchId) {
    return reservations.tablesWithStatus(branchId, CHECKED_IN);
  }

  @Override
  public Set<UUID> reservedTables(UUID branchId, Instant at) {
    return reservations.tablesBookedAt(branchId, CONFIRMED, at);
  }

  @Override
  public boolean hasPartyLargerThan(UUID tableId, int seats) {
    return reservations.hasOpenPartyLargerThan(
        tableId, seats, CONFIRMED, CHECKED_IN, clock.instant());
  }

  @Override
  public boolean hasOpenCommitments(UUID branchId) {
    return !reservations.openAtBranch(branchId, CONFIRMED, CHECKED_IN, clock.instant()).isEmpty();
  }

  @Override
  public boolean hasOpenCommitmentsOutside(UUID branchId, WeeklyHours hours) {
    return reservations.openAtBranch(branchId, CONFIRMED, CHECKED_IN, clock.instant()).stream()
        .anyMatch(reservation -> !hours.covers(reservation.getStartsAt(), reservation.getEndsAt()));
  }
}
