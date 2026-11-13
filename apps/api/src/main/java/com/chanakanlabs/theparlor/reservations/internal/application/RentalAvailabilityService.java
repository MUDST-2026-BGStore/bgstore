package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.branches.BranchFloor;
import com.chanakanlabs.theparlor.contracts.model.BranchAvailability;
import com.chanakanlabs.theparlor.contracts.model.GameAvailability;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.TableAvailability;
import com.chanakanlabs.theparlor.contracts.model.TableAvailability.UnavailableReasonsEnum;
import com.chanakanlabs.theparlor.inventory.RentalLibrary;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What could be booked at a branch for an interval, as of now. Advisory only: it takes no locks and
 * holds nothing, so a booking revalidates everything. It exposes no customer details.
 */
@Service
@Transactional(readOnly = true)
public class RentalAvailabilityService {
  private final BranchAccess branches;
  private final BranchFloor floor;
  private final RentalLibrary library;
  private final BookingPlanner planner;
  private final ReservationRepository reservations;
  private final Clock clock;

  RentalAvailabilityService(
      BranchAccess branches,
      BranchFloor floor,
      RentalLibrary library,
      BookingPlanner planner,
      ReservationRepository reservations,
      Clock clock) {
    this.branches = branches;
    this.floor = floor;
    this.library = library;
    this.planner = planner;
    this.reservations = reservations;
    this.clock = clock;
  }

  public BranchAvailability getBranchAvailability(
      UUID branchId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      Integer partySize,
      @Nullable Set<UUID> gameIds) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
      throw new IllegalArgumentException("endsAt must be after startsAt");
    if (partySize == null || partySize < 1)
      throw new IllegalArgumentException("partySize must be at least 1");
    branches.requireActive(branchId);
    Instant now = clock.instant();
    Instant from = startsAt.toInstant();
    Instant until = endsAt.toInstant();

    boolean open = floor.openingHours(branchId).covers(from, until);
    Set<UUID> booked =
        reservations.tablesBookedDuring(
            branchId, ReservationStatus.CONFIRMED, from, until, now, UUID.randomUUID());
    Set<UUID> occupied = planner.occupiedTables(branchId, from, now);
    var tables =
        floor.tables(branchId).stream()
            .map(
                table -> {
                  var reasons = new LinkedHashSet<UnavailableReasonsEnum>();
                  if (!open) reasons.add(UnavailableReasonsEnum.OUTSIDE_OPENING_HOURS);
                  if (!table.inService()) reasons.add(UnavailableReasonsEnum.OUT_OF_SERVICE);
                  if (table.seats() < partySize)
                    reasons.add(UnavailableReasonsEnum.CAPACITY_EXCEEDED);
                  if (booked.contains(table.id())) reasons.add(UnavailableReasonsEnum.RESERVED);
                  if (occupied.contains(table.id())) reasons.add(UnavailableReasonsEnum.OCCUPIED);
                  var result =
                      new TableAvailability(
                          table.id(),
                          table.label(),
                          table.seats(),
                          new Money(table.hourlyRateMinor()),
                          reasons.isEmpty());
                  if (!reasons.isEmpty()) result.setUnavailableReasons(reasons);
                  return result;
                })
            .toList();

    var shelves =
        gameIds == null || gameIds.isEmpty()
            ? library.allShelves(branchId)
            : library.shelves(branchId, gameIds);
    Set<UUID> busy = planner.busyCopies(branchId, from, until);
    var games =
        shelves.stream()
            .map(
                shelf -> {
                  long free =
                      open && shelf.rentalEnabled()
                          ? shelf.usableCopyIds().stream()
                              .filter(copy -> !busy.contains(copy))
                              .count()
                          : 0;
                  var result =
                      new GameAvailability(
                          shelf.gameId(),
                          shelf.rentalEnabled(),
                          shelf.usableCopyIds().size(),
                          (int) free);
                  if (shelf.offered()) result.setHourlyRate(new Money(shelf.hourlyRateMinor()));
                  return result;
                })
            .toList();

    return new BranchAvailability(
        branchId, startsAt, endsAt, partySize, open, now.atOffset(ZoneOffset.UTC), tables, games);
  }
}
