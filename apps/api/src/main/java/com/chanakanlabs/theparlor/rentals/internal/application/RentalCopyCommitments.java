package com.chanakanlabs.theparlor.rentals.internal.application;

import static com.chanakanlabs.theparlor.rentals.internal.domain.RentalStatus.ACTIVE;

import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocation;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocationId;
import com.chanakanlabs.theparlor.rentals.internal.persistence.RentalCopyAllocationRepository;
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

/** Tells inventory which copies are out with an active rental, and swaps them on request. */
@Service
@Transactional(readOnly = true)
class RentalCopyCommitments implements CopyCommitments {
  private final RentalCopyAllocationRepository allocations;
  private final Clock clock;

  RentalCopyCommitments(RentalCopyAllocationRepository allocations, Clock clock) {
    this.allocations = allocations;
    this.clock = clock;
  }

  @Override
  public Map<UUID, Use> usesAt(UUID branchId, Instant at) {
    return allocations.copiesOfRentalsWithStatus(branchId, ACTIVE).stream()
        .collect(Collectors.toMap(Function.identity(), copy -> Use.IN_USE));
  }

  @Override
  public List<Commitment> openCommitments(UUID branchId, UUID copyId) {
    return allocations.rentalsOfCopyWithStatus(branchId, copyId, ACTIVE).stream()
        .map(
            rental ->
                new Commitment(
                    rental.getId(),
                    rental.getStartedAt(),
                    rental.getPlannedEndsAt().isAfter(clock.instant())
                        ? rental.getPlannedEndsAt()
                        : clock.instant()))
        .toList();
  }

  @Override
  public Set<UUID> busyCopies(UUID branchId, Instant from, Instant until) {
    // An interval starting now or earlier meets every active rental, overdue ones included.
    return from.isAfter(clock.instant())
        ? allocations.copiesOfRentalsPlannedPast(branchId, ACTIVE, from)
        : allocations.copiesOfRentalsWithStatus(branchId, ACTIVE);
  }

  @Override
  @Transactional
  public void move(Commitment commitment, UUID branchId, UUID fromCopyId, UUID toCopyId) {
    allocations.deleteById(new RentalCopyAllocationId(commitment.id(), fromCopyId));
    allocations.saveAndFlush(RentalCopyAllocation.of(commitment.id(), branchId, toCopyId));
  }
}
