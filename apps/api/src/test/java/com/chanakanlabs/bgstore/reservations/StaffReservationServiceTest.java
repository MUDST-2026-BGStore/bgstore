package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.contract.model.CreateReservationRequest;
import com.chanakanlabs.bgstore.contract.model.ReservationAvailabilityTable;
import com.chanakanlabs.bgstore.contract.model.ReservationStatus;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import com.chanakanlabs.bgstore.identity.IdentityAccountJpaRepository;
import com.chanakanlabs.bgstore.tables.TableRecordData;
import com.chanakanlabs.bgstore.tables.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StaffReservationServiceTest {
  private static final UUID BRANCH_ID = UUID.randomUUID();
  private static final long TABLE_ID = 6L;
  private static final LocalDate BOOKING_DATE = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1);

  @Mock private TableRepository tables;
  @Mock private TableReservationJpaRepository tableReservations;
  @Mock private JpaReservationRepository reservations;
  @Mock private BranchDirectory branches;
  @Mock private AccessPolicy accessPolicy;
  @Mock private CurrentIdentityProvider identities;
  @Mock private IdentityAccountJpaRepository accounts;

  private StaffReservationService service;

  @BeforeEach
  void setUp() {
    service =
        new StaffReservationService(
            tables, tableReservations, reservations, branches, accessPolicy, identities, accounts);
    lenient()
        .when(branches.findAll())
        .thenReturn(
            List.of(
                new Branch(
                    BRANCH_ID, "Central Rama II", null, LocalTime.of(9, 0), LocalTime.of(19, 0))));
    lenient()
        .when(identities.currentIdentity())
        .thenReturn(
            new AuthenticatedIdentity(
                "staff-123",
                "staff@example.test",
                "staff@example.test",
                "Staff",
                "Test",
                Set.of(ApplicationRole.STAFF)));
    lenient()
        .when(tables.findByIdForUpdate(TABLE_ID))
        .thenReturn(
            Optional.of(
                new TableRecordData(
                    TABLE_ID,
                    "Table 6",
                    "Central Rama II",
                    6,
                    "Round",
                    "Available",
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    BRANCH_ID)));
    lenient()
        .when(tableReservations.existsOverlap(any(Long.class), any(), any()))
        .thenReturn(false);
    lenient()
        .when(reservations.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createsAWalkInReservationForStaff() {
    var request = request(null);

    var result = service.create(request);

    assertThat(result.status()).isEqualTo("Reserved");
    assertThat(result.clientSubject()).isNull();
    verify(reservations).save(any(ReservationEntity.class));
    verify(tableReservations).save(any(TableReservationEntity.class));
  }

  @Test
  void rejectsAnUnknownRegisteredClientBeforeWriting() {
    when(accounts.existsBySubjectAndApplicationRole("missing-client", "CLIENT")).thenReturn(false);

    assertThatThrownBy(() -> service.create(request("missing-client")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("selected client account was not found");
    verify(reservations, never()).save(any(ReservationEntity.class));
    verify(tableReservations, never()).save(any(TableReservationEntity.class));
  }

  @Test
  void rejectsMissingTableIdAsBadRequest() {
    var request = request(null);
    request.setTableId(null);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("tableId is required");
  }

  @Test
  void availabilityReturnsOnlyActiveTablesThatFitAndAreFree() {
    var fitting = table(TABLE_ID, "Table 6", BRANCH_ID, 6);
    var tooSmall = table(7L, "Small", BRANCH_ID, 2);
    var otherBranch = table(8L, "Other", UUID.randomUUID(), 8);
    doReturn(List.of(fitting, tooSmall, otherBranch))
        .when(tables)
        .findAll("Central Rama II", null, null, true);

    var result = service.availability("Central Rama II", BOOKING_DATE, "10:00", "12:00", 4);

    assertThat(result.getTables())
        .extracting(ReservationAvailabilityTable::getId)
        .containsExactly(TABLE_ID);
    assertThat(result.getTables().getFirst().getAvailable()).isTrue();
  }

  @Test
  void rejectsUnknownBranchesAndInvalidIntervals() {
    assertThatThrownBy(() -> service.availability("Unknown", BOOKING_DATE, "10:00", "12:00", 4))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Unknown branch");

    assertThatThrownBy(
            () -> service.availability("Central Rama II", BOOKING_DATE, "12:00", "10:00", 4))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("End time");
  }

  @Test
  void rejectsReservationsOutsideOpeningHoursOrBookingWindow() {
    assertThatThrownBy(
            () -> service.availability("Central Rama II", BOOKING_DATE, "08:00", "10:00", 4))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("opening hours");

    assertThatThrownBy(
            () ->
                service.availability(
                    "Central Rama II", LocalDate.now(ZoneOffset.ofHours(7)), "10:00", "12:00", 4))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("next 60 days");
  }

  @Test
  void rejectsAConflictAndATableThatDoesNotFit() {
    when(tableReservations.existsOverlap(any(Long.class), any(), any())).thenReturn(true);
    assertThatThrownBy(() -> service.create(request(null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("no longer available");

    when(tables.findByIdForUpdate(TABLE_ID))
        .thenReturn(Optional.of(table(TABLE_ID, "Too small", BRANCH_ID, 2)));
    assertThatThrownBy(() -> service.create(request(null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("does not fit");
  }

  @Test
  void createsAReservationForAClientUsingTheirIdentityName() {
    var client =
        new AuthenticatedIdentity(
            "client-123",
            "client@example.test",
            "client@example.test",
            "Jane",
            "Doe",
            Set.of(ApplicationRole.CLIENT));
    when(identities.currentIdentity()).thenReturn(client);

    var result = service.create(request(null).customerName(null));

    assertThat(result.clientSubject()).isEqualTo("client-123");
    assertThat(result.customerName()).isEqualTo("Jane Doe");
    verify(accessPolicy, never()).requireBranch(any());
  }

  @Test
  void staffListUsesOnlyAssignedBranches() {
    UUID assigned = UUID.randomUUID();
    when(accessPolicy.hasManagerAccess()).thenReturn(false);
    when(accessPolicy.assignedBranchIds()).thenReturn(Set.of(assigned));
    when(reservations.findStaffPage(eq(List.of(assigned)), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(entity("res-1", "Reserved", assigned)), PageRequest.of(0, 20), 1));

    var result = service.list(null, null, 1, 20);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().getId()).isEqualTo("res-1");
  }

  @Test
  void staffListPassesDateAndStatusFiltersThroughToTheRepository() {
    UUID assigned = UUID.randomUUID();
    when(accessPolicy.hasManagerAccess()).thenReturn(false);
    when(accessPolicy.assignedBranchIds()).thenReturn(Set.of(assigned));
    LocalDate filterDate = LocalDate.now(ZoneOffset.ofHours(7));
    when(reservations.findStaffPage(
            eq(List.of(assigned)), eq("Cancelled"), eq(filterDate.toString()), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    var result = service.list(ReservationStatus.CANCELLED, filterDate, 1, 20);

    assertThat(result.getItems()).isEmpty();
  }

  @Test
  void managerListCoversEveryBranch() {
    when(accessPolicy.hasManagerAccess()).thenReturn(true);
    when(branches.findById(BRANCH_ID))
        .thenReturn(
            Optional.of(
                new Branch(
                    BRANCH_ID, "Central Rama II", null, LocalTime.of(9, 0), LocalTime.of(19, 0))));
    when(reservations.findStaffPage(
            eq(List.of(BRANCH_ID)), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(entity("res-1", "Reserved", BRANCH_ID)), PageRequest.of(0, 20), 1));

    var result = service.list(null, null, 1, 20);

    assertThat(result.getItems().getFirst().getBranchName()).isEqualTo("Central Rama II");
    assertThat(result.getItems().getFirst().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    assertThat(result.getItems().getFirst().getCheckInTime()).isEqualTo("-");
  }

  @Test
  void staffListRequiresAnOperationalRole() {
    when(accessPolicy.requireStaffOrManager())
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> service.list(null, null, 1, 20))
        .isInstanceOf(ResponseStatusException.class);
  }

  private static ReservationEntity entity(String id, String status, UUID branchId) {
    return new ReservationEntity(
        id,
        branchId,
        null,
        "Table 6",
        BOOKING_DATE.toString(),
        "10:00–12:00",
        4,
        TABLE_ID,
        "Table 6",
        6,
        120,
        status,
        "Jane Doe",
        "088-888-8888",
        "-",
        "-",
        0,
        0,
        true,
        null,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static TableRecordData table(long id, String name, UUID branchId, int capacity) {
    return new TableRecordData(
        id,
        name,
        "Central Rama II",
        capacity,
        "Round",
        "Available",
        true,
        OffsetDateTime.now(ZoneOffset.UTC),
        branchId);
  }

  private static CreateReservationRequest request(String clientSubject) {
    return new CreateReservationRequest(
            "Central Rama II", BOOKING_DATE, "10:00", "12:00", 4, TABLE_ID)
        .clientSubject(clientSubject)
        .customerName("Jane Doe")
        .phoneNumber("088-888-8888");
  }
}
