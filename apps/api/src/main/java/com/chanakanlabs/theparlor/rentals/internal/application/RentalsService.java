package com.chanakanlabs.theparlor.rentals.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.contracts.model.CheckOutResult;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.Rental;
import com.chanakanlabs.theparlor.contracts.model.RentalPage;
import com.chanakanlabs.theparlor.contracts.model.RentalStatus;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCharge;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalChargeType;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalCopyAllocation;
import com.chanakanlabs.theparlor.rentals.internal.domain.RentalPricingPolicy;
import com.chanakanlabs.theparlor.rentals.internal.persistence.RentalChargeRepository;
import com.chanakanlabs.theparlor.rentals.internal.persistence.RentalCopyAllocationRepository;
import com.chanakanlabs.theparlor.rentals.internal.persistence.RentalRepository;
import com.chanakanlabs.theparlor.reservations.Visits;
import com.chanakanlabs.theparlor.reservations.Visits.Booked;
import com.chanakanlabs.theparlor.reservations.spi.RentalDesk;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hourly in-store play sessions. A rental exists only between staff check-in and staff checkout; it
 * bills the rates the reservation locked, for the hours actually played. Payment is taken at the
 * counter, outside this system.
 */
@Service
@Transactional(readOnly = true)
public class RentalsService implements RentalDesk {
  /** Recorded as the checkout operator when the server closes a rental at its booked end. */
  static final String AUTO_CLOSE_SUBJECT = "system:auto-close";

  private static final List<String> STAFF_ROLES = List.of("staff", "manager", "admin");

  private final RentalRepository rentals;
  private final RentalChargeRepository charges;
  private final RentalCopyAllocationRepository allocations;
  private final Visits visits;
  private final BranchAccess branches;
  private final CurrentUser currentUser;
  private final Clock clock;

  RentalsService(
      RentalRepository rentals,
      RentalChargeRepository charges,
      RentalCopyAllocationRepository allocations,
      Visits visits,
      BranchAccess branches,
      CurrentUser currentUser,
      Clock clock) {
    this.rentals = rentals;
    this.charges = charges;
    this.allocations = allocations;
    this.visits = visits;
    this.branches = branches;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Rental start(Arrival arrival) {
    // The caller holds the copy locks, so nothing can take these copies between this check and
    // the allocations below. An overdue visit that still has one blocks the new arrival.
    var inUse =
        allocations.copiesOfRentalsWithStatus(
            arrival.branchId(),
            com.chanakanlabs.theparlor.rentals.internal.domain.RentalStatus.ACTIVE);
    if (arrival.copyIds().stream().anyMatch(inUse::contains))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A reserved copy is still out with an earlier visit");
    var rental =
        rentals.saveAndFlush(
            com.chanakanlabs.theparlor.rentals.internal.domain.Rental.start(
                arrival.branchId(),
                arrival.reservationId(),
                arrival.startedAt(),
                arrival.plannedEndsAt(),
                arrival.staffSubject()));
    allocations.saveAllAndFlush(
        arrival.copyIds().stream()
            .map(copy -> RentalCopyAllocation.of(rental.getId(), arrival.branchId(), copy))
            .toList());
    return view(List.of(rental)).getFirst();
  }

  @Override
  public Optional<Rental> ofReservation(UUID reservationId) {
    return rentals
        .findByReservationId(reservationId)
        .map(rental -> view(List.of(rental)).getFirst());
  }

  @Override
  public Map<UUID, UUID> rentalIds(Collection<UUID> reservationIds) {
    if (reservationIds.isEmpty()) return Map.of();
    return rentals.findByReservationIdIn(reservationIds).stream()
        .collect(Collectors.toMap(rental -> rental.getReservationId(), rental -> rental.getId()));
  }

  /** The registered customer the visit belongs to, or a branch operator. */
  public Rental get(UUID branchId, UUID rentalId) {
    var rental =
        rentals.findByIdAndBranchId(rentalId, branchId).orElseThrow(RentalsService::missing);
    var booked = visits.booked(List.of(rental.getReservationId())).get(rental.getReservationId());
    var user = currentUser.require();
    if (!user.subject().equals(booked.customerSubject())) {
      if (user.roles().stream().noneMatch(STAFF_ROLES::contains)) throw missing();
      branches.requireOperator(branchId);
    }
    return view(List.of(rental)).getFirst();
  }

  public RentalPage list(
      UUID branchId,
      Integer requestedLimit,
      @Nullable String cursor,
      @Nullable RentalStatus status) {
    branches.requireOperator(branchId);
    int limit = KeysetCursor.limit(requestedLimit);
    String filters = branchId + "|" + (status == null ? "" : status.name());
    UUID after = KeysetCursor.afterId(cursor, "rentals", filters);
    Specification<com.chanakanlabs.theparlor.rentals.internal.domain.Rental> matching =
        (root, query, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get("branchId"), branchId));
          if (status != null)
            predicates.add(
                cb.equal(
                    root.get("status"),
                    com.chanakanlabs.theparlor.rentals.internal.domain.RentalStatus.valueOf(
                        status.name())));
          if (after != null) predicates.add(cb.greaterThan(root.get("id"), after));
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var rows =
        rentals.findBy(matching, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var page = rows.stream().limit(limit).toList();
    var result = new RentalPage(view(page));
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next("rentals", filters, page.getLast().getId()));
    return result;
  }

  /**
   * Staff confirm every game is back, which ends the rental at server time (capped at the booked
   * end), bills each started hour at the locked rates and completes the reservation. Repeating a
   * checkout returns the stored result without billing again.
   */
  @Transactional
  public CheckOutResult checkOut(UUID branchId, UUID rentalId) {
    String staff = branches.requireOperator(branchId);
    var rental =
        rentals.lockByIdAndBranchId(rentalId, branchId).orElseThrow(RentalsService::missing);
    return close(rental, staff);
  }

  /**
   * Completes one rental whose booked end has passed, as the system, billing exactly to the booked
   * end. A no-op when it was already closed or is not yet due, so replicas and staff cannot race.
   */
  @Transactional
  public void autoClose(UUID rentalId) {
    var rental = rentals.lockById(rentalId).orElse(null);
    if (rental == null || !rental.isActive() || rental.getPlannedEndsAt().isAfter(clock.instant()))
      return;
    close(rental, AUTO_CLOSE_SUBJECT);
  }

  List<UUID> expiredActiveIds() {
    return rentals.findExpiredActiveIds(clock.instant());
  }

  private CheckOutResult close(
      com.chanakanlabs.theparlor.rentals.internal.domain.Rental rental, String staff) {
    var rentalId = rental.getId();
    if (!rental.isActive())
      return new CheckOutResult(
          visits.get(rental.getReservationId()), view(List.of(rental)).getFirst());

    var now =
        RentalPricingPolicy.billingEnd(
            rental.getStartedAt(), rental.getPlannedEndsAt(), clock.instant());
    var booked = visits.booked(List.of(rental.getReservationId())).get(rental.getReservationId());
    int hours = RentalPricingPolicy.billableHours(rental.getStartedAt(), now);
    var lines = new ArrayList<RentalCharge>();
    lines.add(RentalCharge.bill(rentalId, null, 1, booked.tableHourlyRateMinor(), hours));
    for (var game : booked.games())
      lines.add(
          RentalCharge.bill(
              rentalId, game.gameId(), game.quantity(), game.unitHourlyRateMinor(), hours));
    charges.saveAllAndFlush(lines);
    rental.complete(
        now,
        staff,
        RentalPricingPolicy.totalMinor(
            lines.stream().map(RentalCharge::getLineTotalMinor).toList()));
    rentals.flush();
    // A completed rental no longer commits its copies or table; nothing else needs releasing.
    var reservation = visits.complete(rental.getReservationId(), now);
    return new CheckOutResult(reservation, view(List.of(rental)).getFirst());
  }

  /**
   * An active rental shows the rates it will bill; a completed one shows what it billed. Both come
   * from the reservation's locked rates, never from current branch prices.
   */
  private List<Rental> view(List<com.chanakanlabs.theparlor.rentals.internal.domain.Rental> page) {
    if (page.isEmpty()) return List.of();
    Map<UUID, Booked> booked =
        visits.booked(page.stream().map(rental -> rental.getReservationId()).toList());
    Map<UUID, List<RentalCharge>> billed =
        charges.findByRentalIdIn(page.stream().map(rental -> rental.getId()).toList()).stream()
            .collect(Collectors.groupingBy(RentalCharge::getRentalId));
    var result = new ArrayList<Rental>();
    for (var rental : page) {
      var visit = booked.get(rental.getReservationId());
      var lines = new ArrayList<com.chanakanlabs.theparlor.contracts.model.RentalCharge>();
      Integer hours = null;
      if (rental.isActive()) {
        lines.add(charge(RentalChargeType.TABLE, null, 1, visit.tableHourlyRateMinor()));
        for (var game : visit.games())
          lines.add(
              charge(
                  RentalChargeType.GAME,
                  game.gameId(),
                  game.quantity(),
                  game.unitHourlyRateMinor()));
      } else {
        var stored =
            billed.getOrDefault(rental.getId(), List.of()).stream()
                .sorted(
                    Comparator.comparing(RentalCharge::getChargeType)
                        .thenComparing(
                            RentalCharge::getGameId,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (var line : stored) {
          hours = line.getBillableHours();
          lines.add(
              charge(
                      line.getChargeType(),
                      line.getGameId(),
                      line.getQuantity(),
                      line.getUnitHourlyRateMinor())
                  .billableHours(line.getBillableHours())
                  .lineTotal(new Money(line.getLineTotalMinor())));
        }
      }
      var view =
          new Rental(
              rental.getId(),
              rental.getBranchId(),
              rental.getReservationId(),
              visit.tableId(),
              visit.customer(),
              RentalStatus.valueOf(rental.getStatus().name()),
              rental.getStartedAt().atOffset(ZoneOffset.UTC),
              rental.getPlannedEndsAt().atOffset(ZoneOffset.UTC),
              clock.instant().atOffset(ZoneOffset.UTC),
              rental.getCheckedInBy(),
              lines);
      if (!rental.isActive()) {
        view.setEndedAt(rental.getActualEndsAt().atOffset(ZoneOffset.UTC));
        view.setCheckedOutBySubject(rental.getCheckedOutBy());
        view.setBillableHours(hours);
        view.setTotal(new Money(rental.getTotalMinor()));
      }
      result.add(view);
    }
    return result;
  }

  private static com.chanakanlabs.theparlor.contracts.model.RentalCharge charge(
      RentalChargeType type, UUID gameId, int quantity, long rateMinor) {
    var charge =
        new com.chanakanlabs.theparlor.contracts.model.RentalCharge(
            com.chanakanlabs.theparlor.contracts.model.RentalCharge.KindEnum.valueOf(type.name()),
            quantity,
            new Money(rateMinor));
    charge.setGameId(gameId);
    return charge;
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found");
  }
}
