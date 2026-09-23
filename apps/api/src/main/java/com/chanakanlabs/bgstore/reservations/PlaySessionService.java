package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.billing.BillingService;
import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.contract.model.ActiveSessionResponse;
import com.chanakanlabs.bgstore.contract.model.CheckoutReceiptResponse;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import com.chanakanlabs.bgstore.contract.model.PaymentRecordResponse;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceKind;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceResponse;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The in-store play session a checked-in reservation represents.
 *
 * <p>Sessions live on the reservation lifecycle rather than in a second aggregate: check-in moves a
 * reservation to {@code CheckedIn}, which is what makes it the client's active session, and
 * check-out closes it once an authorized role confirms the fee. Clients may only ask for staff
 * assistance; they never close a session or stop billing themselves.
 */
@Service
public class PlaySessionService {

  private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
  private static final String RESERVED = "Reserved";
  private static final String CHECKED_IN = "CheckedIn";

  private final JpaReservationRepository reservations;
  private final SessionAssistanceRequestJpaRepository assistanceRequests;
  private final BranchDirectory branches;
  private final BillingService billing;
  private final AccessPolicy accessPolicy;
  private final Clock clock;

  PlaySessionService(
      JpaReservationRepository reservations,
      SessionAssistanceRequestJpaRepository assistanceRequests,
      BranchDirectory branches,
      BillingService billing,
      AccessPolicy accessPolicy,
      Clock clock) {
    this.reservations = reservations;
    this.assistanceRequests = assistanceRequests;
    this.branches = branches;
    this.billing = billing;
    this.accessPolicy = accessPolicy;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ReservationRecordData> sessions() {
    accessPolicy.requireStaffOrManager();
    return reservations
        .findByStatusInOrderByReservationDateAscTimeSlotAsc(List.of(RESERVED, CHECKED_IN))
        .stream()
        // A manager sees every branch; staff see only the ones they are assigned to.
        .filter(reservation -> accessPolicy.canAccessBranch(reservation.branchId()))
        .map(ReservationEntity::toRecord)
        .toList();
  }

  @Transactional(readOnly = true)
  public ActiveSessionResponse activeSession() {
    String subject = accessPolicy.requireClientOnly().subject();
    ReservationEntity reservation =
        reservations
            .findFirstByClientSubjectAndStatus(subject, CHECKED_IN)
            .orElseThrow(() -> notFound("There is no active session."));
    ReservationRecordData record = reservation.toRecord();
    String locationName =
        branches
            .findById(reservation.branchId())
            .map(Branch::name)
            .orElseThrow(
                () -> new IllegalStateException("Reservation " + record.id() + " has no branch."));
    return new ActiveSessionResponse(
        record.id(),
        locationName,
        record.tableName(),
        bangkok(sessionStart(reservation)),
        record.partySize(),
        record.ratePerHour(),
        record.totalPrice(),
        ActiveSessionResponse.CurrencyEnum.THB);
  }

  @Transactional
  public ReservationRecordData checkIn(String reservationId) {
    ReservationEntity reservation = operationalReservation(reservationId);
    if (!RESERVED.equals(reservation.status())) {
      throw badRequest("Only a reserved reservation can be checked in.");
    }
    reservation.checkIn(clock.instant());
    return reservations.save(reservation).toRecord();
  }

  @Transactional
  public CheckoutReceiptResponse checkOut(
      String reservationId, int finalAmount, PaymentMethod paymentMethod) {
    ReservationEntity reservation = operationalReservation(reservationId);
    if (!CHECKED_IN.equals(reservation.status())) {
      throw badRequest("Only a checked-in session can be checked out.");
    }
    boolean waived = paymentMethod == PaymentMethod.WAIVED;
    if (waived && finalAmount != 0) {
      throw badRequest("A waived fee must be recorded as 0.");
    }
    if (!waived && finalAmount <= 0) {
      throw badRequest("A settled fee must be greater than 0.");
    }
    // Charge before closing play: a declined gateway charge aborts the check-out
    // and leaves the session open for a retry instead of closing it uncollected.
    BillingService.Settlement settlement;
    if (waived) {
      settlement = null;
    } else {
      settlement = billing.settle(reservationId, finalAmount, paymentMethod);
    }

    Instant startedAt = sessionStart(reservation);
    Instant endedAt = clock.instant();
    reservation.checkOut(
        endedAt, finalAmount, overtimeMinutes(reservation, endedAt), paymentMethod.getValue());
    ReservationEntity saved = reservations.save(reservation);
    ReservationRecordData record = saved.toRecord();
    long seconds = Duration.between(startedAt, endedAt).toSeconds();
    CheckoutReceiptResponse receipt =
        new CheckoutReceiptResponse(
            record.id(),
            record.tableName(),
            record.partySize(),
            bangkok(startedAt),
            bangkok(endedAt),
            (int) Math.max(1, (seconds + 3599) / 3600),
            record.ratePerHour(),
            record.totalPrice(),
            CheckoutReceiptResponse.CurrencyEnum.THB,
            paymentMethod,
            bangkok(endedAt));
    if (settlement != null) {
      receipt.setPayment(
          new PaymentRecordResponse()
              .gateway(settlement.gateway())
              .reference(settlement.reference())
              .paidAt(bangkok(settlement.paidAt())));
    }
    return receipt;
  }

  @Transactional
  public SessionAssistanceResponse requestAssistance(
      String reservationId, UUID requestId, SessionAssistanceKind kind) {
    String subject = accessPolicy.requireClientOnly().subject();
    Optional<SessionAssistanceRequestEntity> recorded =
        assistanceRequests.findByRequestId(requestId);
    if (recorded.isPresent()) {
      // A retried submission must resolve to the original receipt, not a second request.
      SessionAssistanceRequestEntity existing = recorded.get();
      if (!existing.reservationId().equals(reservationId)
          || !existing.kind().equals(kind.getValue())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "This request id was already used for another request.");
      }
      return new SessionAssistanceResponse(
          existing.requestId(),
          SessionAssistanceKind.fromValue(existing.kind()),
          SessionAssistanceResponse.StatusEnum.RECORDED);
    }

    ReservationEntity reservation =
        reservations
            .findByIdAndClient(reservationId, subject)
            .orElseThrow(() -> notFound("Reservation not found: " + reservationId));
    if (!CHECKED_IN.equals(reservation.status())) {
      throw badRequest("Assistance can only be requested for an active session.");
    }
    SessionAssistanceRequestEntity saved =
        assistanceRequests.save(
            new SessionAssistanceRequestEntity(requestId, reservationId, kind.getValue(), subject));
    return new SessionAssistanceResponse(
        saved.requestId(), kind, SessionAssistanceResponse.StatusEnum.RECORDED);
  }

  /** Loads a reservation for an operational action and enforces the caller's branch scope. */
  private ReservationEntity operationalReservation(String reservationId) {
    accessPolicy.requireStaffOrManager();
    ReservationEntity reservation =
        reservations
            .findById(reservationId)
            .orElseThrow(() -> notFound("Reservation not found: " + reservationId));
    accessPolicy.requireBranch(reservation.branchId());
    return reservation;
  }

  /** The instant play started; only {@link ReservationEntity#checkIn} writes this column. */
  private static Instant sessionStart(ReservationEntity reservation) {
    return Instant.parse(reservation.checkInTime());
  }

  private static OffsetDateTime bangkok(Instant instant) {
    return instant.atZone(BANGKOK).toOffsetDateTime();
  }

  /**
   * Minutes played past the booked window. The booked slot is display text, so an unparseable value
   * simply means no overtime rather than a failed check-out.
   */
  private static int overtimeMinutes(ReservationEntity reservation, Instant endedAt) {
    String slot = reservation.timeSlot();
    int separator = slot.lastIndexOf('–');
    if (separator < 0) {
      return 0;
    }
    try {
      LocalDate date = LocalDate.parse(reservation.reservationDate());
      LocalTime bookedEnd = LocalTime.parse(slot.substring(separator + 1).trim());
      long minutes =
          Duration.between(date.atTime(bookedEnd).atZone(BANGKOK).toInstant(), endedAt).toMinutes();
      return minutes > 0 ? (int) minutes : 0;
    } catch (DateTimeParseException e) {
      return 0;
    }
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }
}
