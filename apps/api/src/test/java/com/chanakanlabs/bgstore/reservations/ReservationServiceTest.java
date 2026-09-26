package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.ApplicationRole;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock private JpaReservationRepository repository;
  @Mock private ReservedSlots reservedSlots;
  @Mock private AccessPolicy accessPolicy;

  private ReservationService service;

  @BeforeEach
  void setUp() {
    service = new ReservationService(repository, reservedSlots, accessPolicy);
    lenient()
        .when(accessPolicy.requireClientOnly()) // staff-only paths do not use this stub
        .thenReturn(
            new AuthenticatedIdentity(
                "client-123",
                "client@example.test",
                "client@example.test",
                "Client",
                "Test",
                Set.of(ApplicationRole.CLIENT)));
  }

  @Test
  void listsReservationsWithPaginationAndStatusFilter() {
    ReservationEntity entity = createEntity("res-1", "Reserved", true);
    PageImpl<ReservationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 4), 1);

    when(repository.findByClientAndStatus(eq("client-123"), eq("Reserved"), any(Pageable.class)))
        .thenReturn(page);

    var result = service.listReservations("Reserved", 1, 4);

    assertThat(result.items()).hasSize(1);
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(4);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  void getsExistingReservation() {
    ReservationEntity entity = createEntity("res-1", "Reserved", true);
    when(repository.findByIdAndClient("res-1", "client-123")).thenReturn(Optional.of(entity));

    var record = service.getReservation("res-1");

    assertThat(record.id()).isEqualTo("res-1");
    assertThat(record.status()).isEqualTo("Reserved");
  }

  @Test
  void throwsNotFoundWhenReservationDoesNotExist() {
    when(repository.findByIdAndClient("res-unknown", "client-123")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getReservation("res-unknown"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reservation not found");
  }

  @Test
  void cancelsUpcomingReservationSuccessfully() {
    ReservationEntity entity = createEntity("res-1", "Reserved", true);
    when(repository.findByIdAndClient("res-1", "client-123")).thenReturn(Optional.of(entity));
    when(repository.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var updated = service.cancelReservation("res-1");

    assertThat(updated.status()).isEqualTo("Cancelled");
    assertThat(updated.canCancel()).isFalse();
    verify(repository).save(entity);
    verify(reservedSlots).releaseFor("res-1");
  }

  @Test
  void throwsBadRequestWhenCancellingAlreadyCancelledOrCompletedReservation() {
    ReservationEntity completed = createEntity("res-2", "Completed", false);
    when(repository.findByIdAndClient("res-2", "client-123")).thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.cancelReservation("res-2"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reservation cannot be cancelled in status Completed");
  }

  @Test
  void staffListsReservationsForAccessibleBranchesOnly() {
    when(accessPolicy.requireStaffOrManager())
        .thenReturn(
            new AuthenticatedIdentity(
                "staff-1",
                "staff@example.test",
                "staff@example.test",
                "Local",
                "Staff",
                Set.of(ApplicationRole.STAFF)));
    when(accessPolicy.canAccessBranch(BRANCH_A)).thenReturn(true);
    when(accessPolicy.canAccessBranch(BRANCH_B)).thenReturn(false);
    when(repository.findAllForStaff(null))
        .thenReturn(List.of(branchEntity("res-1", BRANCH_A), branchEntity("res-2", BRANCH_B)));

    var result = service.staffListReservations(null, 1, 20);

    assertThat(result.items()).extracting(ReservationRecordData::id).containsExactly("res-1");
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(20);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  void staffListPaginatesTheAccessibleBranches() {
    when(accessPolicy.requireStaffOrManager())
        .thenReturn(
            new AuthenticatedIdentity(
                "manager-1",
                "manager@example.test",
                "manager@example.test",
                "Local",
                "Manager",
                Set.of(ApplicationRole.MANAGER)));
    when(accessPolicy.canAccessBranch(BRANCH_A)).thenReturn(true);
    when(repository.findAllForStaff("Completed"))
        .thenReturn(
            List.of(
                branchEntity("res-1", BRANCH_A),
                branchEntity("res-2", BRANCH_A),
                branchEntity("res-3", BRANCH_A)));

    var result = service.staffListReservations("Completed", 2, 2);

    assertThat(result.items()).extracting(ReservationRecordData::id).containsExactly("res-3");
    assertThat(result.total()).isEqualTo(3);
    assertThat(result.totalPages()).isEqualTo(2);
  }

  @Test
  void staffListRequiresStaffOrManager() {
    when(accessPolicy.requireStaffOrManager())
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff or manager required"));

    assertThatThrownBy(() -> service.staffListReservations(null, 1, 20))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Staff or manager required");
  }

  private static final UUID BRANCH_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BRANCH_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static ReservationEntity branchEntity(String id, UUID branchId) {
    return new ReservationEntity(
        id,
        branchId,
        "client-123",
        "Test Booking",
        "13/09/2024",
        "18:00 - 20:00",
        4,
        5L,
        "Table 5",
        4,
        20,
        "Completed",
        "John Doe",
        "0123456789",
        "-",
        "-",
        0,
        40,
        false,
        "/images/table-sample.png",
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  private static ReservationEntity createEntity(String id, String status, boolean canCancel) {
    return new ReservationEntity(
        id,
        "client-123",
        "Test Booking",
        "13/09/2024",
        "18:00 - 20:00",
        4,
        5L,
        "Table 5",
        4,
        20,
        status,
        "John Doe",
        "0123456789",
        "-",
        "-",
        0,
        40,
        canCancel,
        "/images/table-sample.png",
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
