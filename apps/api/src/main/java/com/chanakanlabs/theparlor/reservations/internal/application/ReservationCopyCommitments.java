package com.chanakanlabs.theparlor.reservations.internal.application;

import static com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED;

import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationCopyHold;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationCopyHoldId;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationCopyHoldRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells inventory which copies confirmed reservations hold, and moves a hold on request. Holds
 * count only while their reservation is confirmed; check-in hands the copies to the rental.
 */
@Service
@Transactional(readOnly = true)
class ReservationCopyCommitments implements CopyCommitments {
  private final ReservationCopyHoldRepository holds;
  private final Clock clock;

  ReservationCopyCommitments(ReservationCopyHoldRepository holds, Clock clock) {
    this.holds = holds;
    this.clock = clock;
  }

  @Override
  public Map<UUID, Use> usesAt(UUID branchId, Instant at) {
    return holds.copiesHeldAt(branchId, CONFIRMED, at).stream()
        .collect(Collectors.toMap(Function.identity(), copy -> Use.RESERVED));
  }

  @Override
  public List<Commitment> openCommitments(UUID branchId, UUID copyId) {
    return holds.holdersEndingAfter(branchId, copyId, CONFIRMED, clock.instant()).stream()
        .map(
            reservation ->
                new Commitment(
                    reservation.getId(), reservation.getStartsAt(), reservation.getEndsAt()))
        .toList();
  }

  @Override
  public Set<UUID> busyCopies(UUID branchId, Instant from, Instant until) {
    // A confirmed booking that is already over no longer needs its copies.
    Instant now = clock.instant();
    return holds.copiesHeldDuring(branchId, CONFIRMED, from.isAfter(now) ? from : now, until);
  }

  @Override
  @Transactional
  public void move(Commitment commitment, UUID branchId, UUID fromCopyId, UUID toCopyId) {
    holds.deleteById(new ReservationCopyHoldId(commitment.id(), fromCopyId));
    holds.saveAndFlush(ReservationCopyHold.of(commitment.id(), branchId, toCopyId));
  }
}
