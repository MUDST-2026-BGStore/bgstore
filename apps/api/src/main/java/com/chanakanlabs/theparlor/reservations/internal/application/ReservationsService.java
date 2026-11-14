package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.account.AccountDirectory;
import com.chanakanlabs.theparlor.account.AccountNotProvisionedException;
import com.chanakanlabs.theparlor.account.CurrentAccount;
import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.branches.BranchFloor;
import com.chanakanlabs.theparlor.contracts.model.CheckInResult;
import com.chanakanlabs.theparlor.contracts.model.CreateReservationRequest;
import com.chanakanlabs.theparlor.contracts.model.CreateStaffReservationRequest;
import com.chanakanlabs.theparlor.contracts.model.CustomerReference;
import com.chanakanlabs.theparlor.contracts.model.GameSelection;
import com.chanakanlabs.theparlor.contracts.model.GuestCustomer;
import com.chanakanlabs.theparlor.contracts.model.RegisteredCustomer;
import com.chanakanlabs.theparlor.contracts.model.RescheduleReservationRequest;
import com.chanakanlabs.theparlor.contracts.model.Reservation;
import com.chanakanlabs.theparlor.contracts.model.ReservationPage;
import com.chanakanlabs.theparlor.contracts.model.ReservationStatus;
import com.chanakanlabs.theparlor.contracts.model.StaffBookingRequest;
import com.chanakanlabs.theparlor.contracts.model.WalkInReservationRequest;
import com.chanakanlabs.theparlor.customers.Customers;
import com.chanakanlabs.theparlor.idempotency.Idempotency;
import com.chanakanlabs.theparlor.idempotency.Idempotency.Scope;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.inventory.RentalLibrary;
import com.chanakanlabs.theparlor.payments.Payments;
import com.chanakanlabs.theparlor.reservations.internal.application.BookingPlanner.Booking;
import com.chanakanlabs.theparlor.reservations.internal.application.BookingPlanner.Selection;
import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation.Party;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationCopyHold;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGame;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationSource;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationCopyHoldRepository;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationGameRepository;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import com.chanakanlabs.theparlor.reservations.spi.RentalDesk;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Table bookings and their lifecycle: CONFIRMED to CHECKED_IN to COMPLETED, or CONFIRMED to
 * CANCELLED or NO_SHOW. A confirmed booking holds its table and specific game copies for its whole
 * window; cancelling or missing it releases them, and check-in hands them to a rental. A customer
 * booking may carry a paid food pre-order, which follows the same lifecycle (see PreOrderDesk).
 */
@Service
@Transactional(readOnly = true)
public class ReservationsService {
  private static final List<String> STAFF_ROLES = List.of("staff", "manager", "admin");

  private final ReservationRepository reservations;
  private final ReservationGameRepository games;
  private final ReservationCopyHoldRepository holds;
  private final BookingPlanner planner;
  private final ReservationViews views;
  private final PreOrderDesk preOrders;
  private final Payments payments;
  private final RentalDesk rentalDesk;
  private final BranchAccess branches;
  private final BranchFloor floor;
  private final RentalLibrary library;
  private final CurrentUser currentUser;
  private final CurrentAccount currentAccount;
  private final AccountDirectory accounts;
  private final Customers customers;
  private final Idempotency idempotency;
  private final Clock clock;

  ReservationsService(
      ReservationRepository reservations,
      ReservationGameRepository games,
      ReservationCopyHoldRepository holds,
      BookingPlanner planner,
      ReservationViews views,
      PreOrderDesk preOrders,
      Payments payments,
      @Lazy RentalDesk rentalDesk,
      BranchAccess branches,
      BranchFloor floor,
      RentalLibrary library,
      CurrentUser currentUser,
      CurrentAccount currentAccount,
      AccountDirectory accounts,
      Customers customers,
      Idempotency idempotency,
      Clock clock) {
    this.reservations = reservations;
    this.games = games;
    this.holds = holds;
    this.planner = planner;
    this.views = views;
    this.preOrders = preOrders;
    this.payments = payments;
    this.rentalDesk = rentalDesk;
    this.branches = branches;
    this.floor = floor;
    this.library = library;
    this.currentUser = currentUser;
    this.currentAccount = currentAccount;
    this.accounts = accounts;
    this.customers = customers;
    this.idempotency = idempotency;
    this.clock = clock;
  }

  /** A signed-in customer books a future visit for themselves. */
  @Transactional
  public Reservation createMine(
      UUID branchId, UUID idempotencyKey, CreateReservationRequest request) {
    var user = currentUser.require();
    var profile =
        currentAccount.findCurrentProfile().orElseThrow(AccountNotProvisionedException::new);
    branches.requireActive(branchId);
    var scope = new Scope(user.subject(), "POST", "/api/v1/branches/" + branchId + "/reservations");
    UUID id =
        idempotency.once(
            scope,
            idempotencyKey,
            request.toString(),
            () -> {
              UUID booked =
                  book(
                      branchId,
                      new Party(customers.forAccount(profile.getId()), user.subject(), null, null),
                      ReservationSource.CUSTOMER,
                      planner.secure(
                          branchId,
                          request.getTableId(),
                          instant(request.getStartsAt()),
                          instant(request.getEndsAt()),
                          request.getPartySize(),
                          selections(request.getGames()),
                          null,
                          false),
                      request.getNotes(),
                      user.subject());
              var reservation =
                  reservations.findById(booked).orElseThrow(ReservationsService::missing);
              boolean promptPay =
                  request.getPaymentMethod()
                      == CreateReservationRequest.PaymentMethodEnum.PROMPTPAY;
              if (request.getPreOrder() != null)
                preOrders.place(reservation, request.getPreOrder(), clock.instant(), !promptPay);
              if (promptPay) {
                long hourlyMinor = reservation.getTableHourlyRateMinor();
                for (var game : games.findByIdReservationIdIn(List.of(booked)))
                  hourlyMinor =
                      Math.addExact(
                          hourlyMinor,
                          Math.multiplyExact(game.getUnitHourlyRateMinor(), game.getQuantity()));
                preOrders.startPromptPay(
                    reservation, Math.multiplyExact(hourlyMinor, reservation.bookedHours()));
              }
              return booked;
            });
    return views.of(reservations.findById(id).orElseThrow(ReservationsService::missing));
  }

  /** Staff book ahead for a customer, or seat a walk-in starting now. Neither checks in. */
  @Transactional
  public Reservation createByStaff(
      UUID branchId, UUID idempotencyKey, CreateStaffReservationRequest request) {
    String staff = branches.requireOperator(branchId);
    var scope = new Scope(staff, "POST", "/api/v1/branches/" + branchId + "/staff/reservations");
    UUID id =
        idempotency.once(
            scope,
            idempotencyKey,
            request.toString(),
            () -> {
              if (request instanceof StaffBookingRequest booking)
                return book(
                    branchId,
                    party(booking.getCustomer()),
                    ReservationSource.STAFF_BOOKING,
                    planner.secure(
                        branchId,
                        booking.getTableId(),
                        instant(booking.getStartsAt()),
                        instant(booking.getEndsAt()),
                        booking.getPartySize(),
                        selections(booking.getGames()),
                        null,
                        false),
                    booking.getNotes(),
                    staff);
              if (request instanceof WalkInReservationRequest walkIn) {
                UUID seated =
                    book(
                        branchId,
                        party(walkIn.getCustomer()),
                        ReservationSource.WALK_IN,
                        planner.secure(
                            branchId,
                            walkIn.getTableId(),
                            clock.instant(),
                            instant(walkIn.getEndsAt()),
                            walkIn.getPartySize(),
                            selections(walkIn.getGames()),
                            null,
                            true),
                        walkIn.getNotes(),
                        staff);
                // Seating now shares this transaction and idempotency key: if the check-in
                // fails, the walk-in is rolled back too and the table stays as it was.
                if (Boolean.TRUE.equals(walkIn.getCheckIn())) arrive(branchId, seated, staff);
                return seated;
              }
              throw new IllegalArgumentException("Unknown reservation source");
            });
    return views.of(reservations.findById(id).orElseThrow(ReservationsService::missing));
  }

  /**
   * Replaces the whole plan. The old copy holds are released and the new plan secured in one
   * transaction, so a plan that does not fit rolls back and the original booking stands.
   */
  @Transactional
  public Reservation reschedule(
      UUID branchId, UUID reservationId, RescheduleReservationRequest request) {
    var reservation =
        reservations
            .lockByIdAndBranchId(reservationId, branchId)
            .orElseThrow(ReservationsService::missing);
    Instant now = clock.instant();
    boolean operator = requireOwnerOrOperator(reservation);
    if (!reservation.isConfirmed()) throw conflict("Only a confirmed reservation can be changed");
    if (!operator && !now.isBefore(reservation.getStartsAt()))
      throw conflict("A reservation that has started can only be changed by staff");
    preOrders.requireReschedulable(reservation, instant(request.getStartsAt()));
    holds.deleteOf(reservationId);
    games.deleteOf(reservationId);
    var booking =
        planner.secure(
            branchId,
            request.getTableId(),
            instant(request.getStartsAt()),
            instant(request.getEndsAt()),
            request.getPartySize(),
            selections(request.getGames()),
            reservationId,
            false);
    reservation.reschedule(
        booking.plan(), request.getNotes() == null ? reservation.getNotes() : request.getNotes());
    reservations.flush();
    hold(reservation, booking);
    return views.of(reservation);
  }

  /** Customers cancel their own booking before it starts; operators until check-in. */
  @Transactional
  public Reservation cancel(UUID branchId, UUID reservationId, String reason) {
    var reservation =
        reservations
            .lockByIdAndBranchId(reservationId, branchId)
            .orElseThrow(ReservationsService::missing);
    Instant now = clock.instant();
    boolean operator = requireOwnerOrOperator(reservation);
    if (!operator && reservation.isConfirmed() && !now.isBefore(reservation.getStartsAt()))
      throw conflict("A reservation that has started can only be cancelled by staff");
    boolean released;
    try {
      released = reservation.cancel(currentUser.require().subject(), reason, now);
    } catch (IllegalStateException exception) {
      throw conflict(exception.getMessage());
    }
    if (released) holds.deleteOf(reservationId);
    reservations.flush();
    // The card is voided last, once everything that can still fail has been written.
    if (released) preOrders.release(reservation);
    return views.of(reservation);
  }

  /** Staff release a booking whose party never arrived. Never automatic. */
  @Transactional
  public Reservation markNoShow(UUID branchId, UUID reservationId, String reason) {
    String staff = branches.requireOperator(branchId);
    var reservation =
        reservations
            .lockByIdAndBranchId(reservationId, branchId)
            .orElseThrow(ReservationsService::missing);
    boolean released;
    try {
      released = reservation.markNoShow(staff, reason, clock.instant());
    } catch (IllegalStateException exception) {
      throw conflict(exception.getMessage());
    }
    if (released) holds.deleteOf(reservationId);
    reservations.flush();
    if (released) preOrders.releaseForNoShow(reservation);
    return views.of(reservation);
  }

  /**
   * Staff confirm arrival and start the visit's rental. The table and every held copy are locked
   * and rechecked, because an earlier visit may have overrun. Repeating a check-in returns the
   * reservation and rental as they are now.
   */
  @Transactional
  public CheckInResult checkIn(UUID branchId, UUID reservationId) {
    return arrive(branchId, reservationId, branches.requireOperator(branchId));
  }

  /**
   * Check-in for a caller already verified as the branch's operator, in the caller's transaction.
   */
  private CheckInResult arrive(UUID branchId, UUID reservationId, String staff) {
    var reservation =
        reservations
            .lockByIdAndBranchId(reservationId, branchId)
            .orElseThrow(ReservationsService::missing);
    var existing = rentalDesk.ofReservation(reservationId);
    if (existing.isPresent()) return new CheckInResult(views.of(reservation), existing.get());

    Instant now = clock.instant();
    preOrders.requirePaid(reservation);
    var table =
        floor
            .lockTable(branchId, reservation.getTableId())
            .orElseThrow(() -> conflict("The reserved table no longer exists"));
    if (!table.inService()) throw conflict("The reserved table is out of service");
    if (table.seats() < reservation.getPartySize())
      throw conflict("The reserved table is too small for the party");
    if (planner.occupiedTables(branchId, now, now).contains(reservation.getTableId()))
      throw conflict("The reserved table is still occupied by an earlier visit");

    var held = holds.findByIdReservationId(reservationId);
    var copyIds = held.stream().map(hold -> hold.getId().copyId()).toList();
    if (!copyIds.isEmpty()) {
      var gameIds =
          games.findByIdReservationIdIn(List.of(reservationId)).stream()
              .map(game -> game.getId().gameId())
              .collect(Collectors.toSet());
      var usable =
          library.lockShelves(branchId, gameIds).stream()
              .flatMap(shelf -> shelf.usableCopyIds().stream())
              .collect(Collectors.toSet());
      if (!usable.containsAll(copyIds)) throw conflict("A reserved copy is no longer usable");
    }
    try {
      reservation.checkIn(staff, now);
    } catch (IllegalStateException exception) {
      throw conflict(exception.getMessage());
    }
    // The copies now belong to the rental; the hold has done its job.
    holds.deleteOf(reservationId);
    preOrders.serve(reservation);
    reservations.flush();
    var rental =
        rentalDesk.start(
            new RentalDesk.Arrival(
                reservationId, branchId, now, reservation.getEndsAt(), staff, copyIds));
    // A gateway capture cannot be rolled back, so it runs after the last step that can fail.
    preOrders.capture(reservation);
    return new CheckInResult(views.of(reservation), rental);
  }

  @Transactional
  public Reservation get(UUID branchId, UUID reservationId) {
    var reservation =
        reservations
            .findByIdAndBranchId(reservationId, branchId)
            .orElseThrow(ReservationsService::missing);
    requireOwnerOrOperator(reservation);
    var payment = payments.of(Payments.Kind.RESERVATION, List.of(reservationId)).get(reservationId);
    if (payment != null
        && payment.method() == Payments.Method.PROMPTPAY
        && payment.status() == Payments.Status.PENDING)
      payments.refreshPromptPay(Payments.Payable.reservation(reservationId));
    return views.of(reservation);
  }

  public List<UUID> unpaidPastDeadline() {
    return payments.expiredUnpaid(Payments.Kind.RESERVATION, clock.instant());
  }

  @Transactional
  public boolean expireUnpaidReservation(UUID reservationId) {
    var found = reservations.lockById(reservationId);
    if (found.isEmpty() || !found.get().isConfirmed()) return false;
    var reservation = found.get();
    var payment = payments.of(Payments.Kind.RESERVATION, List.of(reservationId)).get(reservationId);
    if (payment == null
        || payment.method() != Payments.Method.PROMPTPAY
        || payment.status() != Payments.Status.PENDING
        || payment.expiresAt() == null
        || payment.expiresAt().isAfter(clock.instant())) return false;
    if (payments.refreshPromptPay(Payments.Payable.reservation(reservationId))) return false;
    reservation.cancel(
        "system:payment-expiry", "PromptPay payment window expired", clock.instant());
    holds.deleteOf(reservationId);
    reservations.flush();
    preOrders.release(reservation);
    return true;
  }

  public ReservationPage listBranch(
      UUID branchId,
      Integer limit,
      @Nullable String cursor,
      @Nullable ReservationStatus status,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime until) {
    branches.requireOperator(branchId);
    if (from != null && until != null && !until.isAfter(from))
      throw new IllegalArgumentException("until must be after from");
    return page(
        "branch-reservations",
        "branchId",
        branchId,
        limit,
        cursor,
        status,
        from == null ? null : from.toInstant(),
        until == null ? null : until.toInstant());
  }

  public ReservationPage listMine(
      Integer limit, @Nullable String cursor, @Nullable ReservationStatus status) {
    return page(
        "my-reservations",
        "customerSubject",
        currentUser.require().subject(),
        limit,
        cursor,
        status,
        null,
        null);
  }

  private UUID book(
      UUID branchId,
      Party party,
      ReservationSource source,
      Booking booking,
      String notes,
      String createdBySubject) {
    var reservation =
        reservations.saveAndFlush(
            com.chanakanlabs.theparlor.reservations.internal.domain.Reservation.book(
                branchId, party, source, booking.plan(), notes, createdBySubject));
    hold(reservation, booking);
    return reservation.getId();
  }

  private void hold(
      com.chanakanlabs.theparlor.reservations.internal.domain.Reservation reservation,
      Booking booking) {
    var selected = new ArrayList<ReservationGame>();
    var held = new ArrayList<ReservationCopyHold>();
    for (var game : booking.games()) {
      selected.add(
          ReservationGame.select(
              reservation.getId(), game.gameId(), game.quantity(), game.unitHourlyRateMinor()));
      for (UUID copyId : game.copyIds())
        held.add(ReservationCopyHold.of(reservation.getId(), reservation.getBranchId(), copyId));
    }
    games.saveAllAndFlush(selected);
    holds.saveAllAndFlush(held);
  }

  /**
   * Returns whether the caller acts as a branch operator. The customer a booking belongs to may
   * always see it; anyone else must operate the branch. Customers asking about someone else's
   * booking get the same 404 as for one that does not exist.
   */
  private boolean requireOwnerOrOperator(
      com.chanakanlabs.theparlor.reservations.internal.domain.Reservation reservation) {
    var user = currentUser.require();
    boolean staff = user.roles().stream().anyMatch(STAFF_ROLES::contains);
    if (user.subject().equals(reservation.getCustomerSubject()) && !staff) return false;
    if (!staff) throw missing();
    branches.requireOperator(reservation.getBranchId());
    return true;
  }

  private Party party(CustomerReference customer) {
    if (customer instanceof RegisteredCustomer registered) {
      var account =
          accounts
              .findBySubject(registered.getSubject())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
      return new Party(
          customers.forAccount(account.accountId()), registered.getSubject(), null, null);
    }
    if (customer instanceof GuestCustomer guest)
      return new Party(null, null, guest.getDisplayName(), guest.getPhone());
    throw new IllegalArgumentException("Customer is required");
  }

  private ReservationPage page(
      String operation,
      String ownerField,
      Object owner,
      Integer requestedLimit,
      String cursor,
      ReservationStatus status,
      Instant from,
      Instant until) {
    int limit = KeysetCursor.limit(requestedLimit);
    String filters = owner + "|" + (status == null ? "" : status.name()) + "|" + from + "|" + until;
    UUID after = KeysetCursor.afterId(cursor, operation, filters);
    Specification<com.chanakanlabs.theparlor.reservations.internal.domain.Reservation> matching =
        (root, query, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get(ownerField), owner));
          if (status != null)
            predicates.add(
                cb.equal(
                    root.get("status"),
                    com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus
                        .valueOf(status.name())));
          if (from != null) predicates.add(cb.greaterThan(root.get("endsAt"), from));
          if (until != null) predicates.add(cb.lessThan(root.get("startsAt"), until));
          if (after != null) predicates.add(cb.greaterThan(root.get("id"), after));
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var rows =
        reservations.findBy(matching, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var page = rows.stream().limit(limit).toList();
    var result = new ReservationPage(views.of(page));
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next(operation, filters, page.getLast().getId()));
    return result;
  }

  private static List<Selection> selections(List<GameSelection> games) {
    if (games == null || games.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Game selections are required");
    return games.stream().map(game -> new Selection(game.getGameId(), game.getQuantity())).toList();
  }

  private static Instant instant(OffsetDateTime value) {
    if (value == null) throw new IllegalArgumentException("Booking times are required");
    return value.toInstant();
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}
