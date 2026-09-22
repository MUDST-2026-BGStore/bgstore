package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceKind;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PlaySessionServiceTest {

  private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String CLIENT = "client-123";

  /** 10:00 in Bangkok. */
  private static final Instant STARTED_AT = Instant.parse("2026-09-22T03:00:00Z");

  /** 13:00 in Bangkok: one hour past the booked 09:00–12:00 window. */
  private static final Instant ENDED_AT = Instant.parse("2026-09-22T06:00:00Z");

  @Mock private JpaReservationRepository reservations;
  @Mock private SessionAssistanceRequestJpaRepository assistanceRequests;
  @Mock private BranchDirectory branches;
  @Mock private AccessPolicy accessPolicy;

  private PlaySessionService service;

  @BeforeEach
  void setUp() {
    service =
        new PlaySessionService(
            reservations,
            assistanceRequests,
            branches,
            accessPolicy,
            Clock.fixed(ENDED_AT, ZoneOffset.UTC));
  }

  @Test
  void activeSessionDescribesTheCheckedInReservation() {
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(reservations.findFirstByClientSubjectAndStatus(CLIENT, "CheckedIn"))
        .thenReturn(Optional.of(checkedIn("res-1")));
    when(branches.findById(BRANCH_ID))
        .thenReturn(Optional.of(new Branch(BRANCH_ID, "Silom", null, null, null)));

    var session = service.activeSession();

    assertThat(session.getReservationId()).isEqualTo("res-1");
    assertThat(session.getLocationName()).isEqualTo("Silom");
    assertThat(session.getTableName()).isEqualTo("Table 5");
    assertThat(session.getPartySize()).isEqualTo(4);
    assertThat(session.getRatePerHour()).isEqualTo(120);
    assertThat(session.getAccruedAmount()).isZero();
    assertThat(session.getCurrency().getValue()).isEqualTo("THB");
    assertThat(session.getStartedAt())
        .isEqualTo(STARTED_AT.atZone(ZoneId.of("Asia/Bangkok")).toOffsetDateTime());
  }

  @Test
  void activeSessionFailsWhenTheClientHasNoCheckedInReservation() {
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(reservations.findFirstByClientSubjectAndStatus(CLIENT, "CheckedIn"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.activeSession())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("There is no active session.");
  }

  @Test
  void checkInStartsPlayForAReservedReservation() {
    ReservationEntity reservation = reserved("res-1");
    when(reservations.findById("res-1")).thenReturn(Optional.of(reservation));
    when(reservations.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var record = service.checkIn("res-1");

    assertThat(record.status()).isEqualTo("CheckedIn");
    assertThat(record.checkInTime()).isEqualTo(ENDED_AT.toString());
  }

  @Test
  void checkInRejectsAReservationThatIsNotReserved() {
    when(reservations.findById("res-1")).thenReturn(Optional.of(checkedIn("res-1")));

    assertThatThrownBy(() -> service.checkIn("res-1"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only a reserved reservation can be checked in.");
  }

  @Test
  void checkOutRecordsTheConfirmedFeeAndPaymentMethod() {
    ReservationEntity reservation = checkedIn("res-1");
    when(reservations.findById("res-1")).thenReturn(Optional.of(reservation));
    when(reservations.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var receipt = service.checkOut("res-1", 240, PaymentMethod.CASH);

    assertThat(receipt.getTotalDue()).isEqualTo(240);
    assertThat(receipt.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(receipt.getHours()).isEqualTo(3);
    assertThat(receipt.getCurrency().getValue()).isEqualTo("THB");
    assertThat(reservation.status()).isEqualTo("Completed");
    assertThat(reservation.toRecord().overtimeMinutes()).isEqualTo(60);
    assertThat(reservation.toRecord().canCancel()).isFalse();
  }

  @Test
  void checkOutWaivesTheFeeOnlyWhenTheAmountIsZero() {
    when(reservations.findById("res-1")).thenReturn(Optional.of(checkedIn("res-1")));

    assertThatThrownBy(() -> service.checkOut("res-1", 100, PaymentMethod.WAIVED))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("A waived fee must be recorded as 0.");

    assertThatThrownBy(() -> service.checkOut("res-1", 0, PaymentMethod.CASH))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("A settled fee must be greater than 0.");
  }

  @Test
  void checkOutRejectsASessionThatIsNotCheckedIn() {
    when(reservations.findById("res-1")).thenReturn(Optional.of(reserved("res-1")));

    assertThatThrownBy(() -> service.checkOut("res-1", 240, PaymentMethod.CASH))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only a checked-in session can be checked out.");
  }

  @Test
  void requestAssistanceRecordsARequestForTheClientsOwnSession() {
    UUID requestId = UUID.randomUUID();
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(assistanceRequests.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(reservations.findByIdAndClient("res-1", CLIENT))
        .thenReturn(Optional.of(checkedIn("res-1")));
    when(assistanceRequests.save(any(SessionAssistanceRequestEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.requestAssistance("res-1", requestId, SessionAssistanceKind.CALL_STAFF);

    assertThat(response.getRequestId()).isEqualTo(requestId);
    assertThat(response.getKind()).isEqualTo(SessionAssistanceKind.CALL_STAFF);
    assertThat(response.getStatus().getValue()).isEqualTo("Recorded");
  }

  @Test
  void requestAssistanceReturnsTheOriginalReceiptForARepeatedRequestId() {
    UUID requestId = UUID.randomUUID();
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(assistanceRequests.findByRequestId(requestId))
        .thenReturn(
            Optional.of(
                new SessionAssistanceRequestEntity(
                    requestId, "res-1", SessionAssistanceKind.END_PLAYING.getValue(), CLIENT)));

    var response = service.requestAssistance("res-1", requestId, SessionAssistanceKind.END_PLAYING);

    assertThat(response.getRequestId()).isEqualTo(requestId);
    assertThat(response.getKind()).isEqualTo(SessionAssistanceKind.END_PLAYING);
    verify(assistanceRequests, never()).save(any(SessionAssistanceRequestEntity.class));
  }

  @Test
  void requestAssistanceRejectsARequestIdReusedForAnotherReservation() {
    UUID requestId = UUID.randomUUID();
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(assistanceRequests.findByRequestId(requestId))
        .thenReturn(
            Optional.of(
                new SessionAssistanceRequestEntity(
                    requestId, "res-2", SessionAssistanceKind.CALL_STAFF.getValue(), CLIENT)));

    assertThatThrownBy(
            () -> service.requestAssistance("res-1", requestId, SessionAssistanceKind.CALL_STAFF))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already used for another request");
  }

  @Test
  void requestAssistanceRejectsASessionThatIsNotActive() {
    UUID requestId = UUID.randomUUID();
    when(accessPolicy.requireClientOnly()).thenReturn(client());
    when(assistanceRequests.findByRequestId(requestId)).thenReturn(Optional.empty());
    when(reservations.findByIdAndClient("res-1", CLIENT))
        .thenReturn(Optional.of(reserved("res-1")));

    assertThatThrownBy(
            () -> service.requestAssistance("res-1", requestId, SessionAssistanceKind.CALL_STAFF))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Assistance can only be requested for an active session.");
  }

  private static AuthenticatedIdentity client() {
    return new AuthenticatedIdentity(
        CLIENT,
        "client@example.test",
        "client@example.test",
        "Client",
        "Test",
        Set.of(ApplicationRole.CLIENT));
  }

  private static ReservationEntity reserved(String id) {
    return reservation(id, "Reserved", "-");
  }

  private static ReservationEntity checkedIn(String id) {
    return reservation(id, "CheckedIn", STARTED_AT.toString());
  }

  private static ReservationEntity reservation(String id, String status, String checkInTime) {
    return new ReservationEntity(
        id,
        BRANCH_ID,
        CLIENT,
        "Table 5",
        "2026-09-22",
        "09:00–12:00",
        4,
        5L,
        "Table 5",
        4,
        120,
        status,
        "John Doe",
        "0123456789",
        checkInTime,
        "-",
        0,
        0,
        true,
        null,
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
