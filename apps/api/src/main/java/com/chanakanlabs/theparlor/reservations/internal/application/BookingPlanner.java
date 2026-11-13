package com.chanakanlabs.theparlor.reservations.internal.application;

import static com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CHECKED_IN;
import static com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED;

import com.chanakanlabs.theparlor.branches.BranchFloor;
import com.chanakanlabs.theparlor.inventory.RentalLibrary;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Decides whether a table and a set of game copies can be committed for an interval, and which
 * copies. It write-locks the table and every copy of the selected games first, so two plans for the
 * same resources never run at once; the caller persists the result in the same transaction.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class BookingPlanner {
  /** Maximum distinct games in one booking, matching the API contract. */
  static final int MAX_GAMES = 50;

  record Selection(UUID gameId, int quantity) {}

  record GameHold(UUID gameId, int quantity, long unitHourlyRateMinor, List<UUID> copyIds) {}

  record Booking(Reservation.Plan plan, List<GameHold> games) {}

  private final BranchFloor floor;
  private final RentalLibrary library;
  private final List<CopyCommitments> copyCommitments;
  private final ReservationRepository reservations;
  private final Clock clock;

  BookingPlanner(
      BranchFloor floor,
      RentalLibrary library,
      List<CopyCommitments> copyCommitments,
      ReservationRepository reservations,
      Clock clock) {
    this.floor = floor;
    this.library = library;
    this.copyCommitments = copyCommitments;
    this.reservations = reservations;
    this.clock = clock;
  }

  /**
   * rescheduling is the reservation being replanned, whose own booking must not block it; the
   * caller has already released its copy holds. A walk-in starts now; everything else must start in
   * the future.
   */
  Booking secure(
      UUID branchId,
      UUID tableId,
      Instant startsAt,
      Instant endsAt,
      int partySize,
      List<Selection> games,
      UUID rescheduling,
      boolean walkIn) {
    Instant now = clock.instant();
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt))
      throw new IllegalArgumentException("A booking must end after it starts");
    if (!walkIn && !startsAt.isAfter(now))
      throw new IllegalArgumentException("A booking must start in the future");
    if (partySize < 1 || partySize > Reservation.MAX_PARTY_SIZE)
      throw new IllegalArgumentException(
          "Party size must be between 1 and " + Reservation.MAX_PARTY_SIZE);
    if (games == null || games.size() > MAX_GAMES)
      throw new IllegalArgumentException("At most " + MAX_GAMES + " games can be booked");
    var gameIds = games.stream().map(Selection::gameId).collect(Collectors.toSet());
    if (gameIds.size() != games.size() || gameIds.contains(null))
      throw new IllegalArgumentException("Each game can be selected only once");

    var table = floor.lockTable(branchId, tableId).orElseThrow(BookingPlanner::missingTable);
    if (!table.inService()) throw conflict("Table is out of service");
    if (table.seats() < partySize) throw conflict("Table is too small for the party");
    if (!floor.openingHours(branchId).covers(startsAt, endsAt))
      throw conflict("The branch is not open for the whole booking");
    if (busyTables(branchId, startsAt, endsAt, now, rescheduling).contains(tableId))
      throw conflict("Table is not free for the whole booking");

    var holds = new ArrayList<GameHold>();
    if (!games.isEmpty()) {
      var shelves =
          library.lockShelves(branchId, gameIds).stream()
              .collect(Collectors.toMap(shelf -> shelf.gameId(), shelf -> shelf));
      Set<UUID> busy = busyCopies(branchId, startsAt, endsAt);
      for (Selection selection : games) {
        var shelf = shelves.get(selection.gameId());
        if (!shelf.offered() || !shelf.rentalEnabled())
          throw conflict("A selected game is not offered for play at this branch");
        var free =
            shelf.usableCopyIds().stream()
                .filter(copy -> !busy.contains(copy))
                .limit(selection.quantity())
                .toList();
        if (free.size() < selection.quantity())
          throw conflict("Not enough copies of a selected game are free for the whole booking");
        holds.add(
            new GameHold(selection.gameId(), selection.quantity(), shelf.hourlyRateMinor(), free));
      }
    }
    return new Booking(
        new Reservation.Plan(tableId, startsAt, endsAt, partySize, table.hourlyRateMinor()), holds);
  }

  /**
   * Tables committed for any part of [from, until): confirmed bookings that overlap it, and
   * checked-in visits, which keep their table until their planned end and, once overdue, until
   * checkout.
   */
  Set<UUID> busyTables(UUID branchId, Instant from, Instant until, Instant now, UUID except) {
    var busy =
        new HashSet<>(
            reservations.tablesBookedDuring(
                branchId,
                CONFIRMED,
                from,
                until,
                now,
                except == null ? UUID.randomUUID() : except));
    busy.addAll(occupiedTables(branchId, from, now));
    return busy;
  }

  Set<UUID> occupiedTables(UUID branchId, Instant from, Instant now) {
    return from.isAfter(now)
        ? reservations.tablesOccupiedPast(branchId, CHECKED_IN, from)
        : reservations.tablesWithStatus(branchId, CHECKED_IN);
  }

  /** Copies any module has committed for part of [from, until). */
  Set<UUID> busyCopies(UUID branchId, Instant from, Instant until) {
    var busy = new HashSet<UUID>();
    for (CopyCommitments source : copyCommitments)
      busy.addAll(source.busyCopies(branchId, from, until));
    return busy;
  }

  private static ResponseStatusException missingTable() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}
