package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock private JpaReservationRepository repository;
  @Mock private ReservedSlots reservedSlots;

  private ReservationService service;

  @BeforeEach
  void setUp() {
    service = new ReservationService(repository, reservedSlots);
  }

  @Test
  void listsReservationsWithPaginationAndStatusFilter() {
    ReservationEntity entity = createEntity("res-1", "Reserved", true);
    PageImpl<ReservationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 4), 1);

    when(repository.findByClientAndStatus(eq("client-123"), eq("Reserved"), any(Pageable.class)))
        .thenReturn(page);

    var result = service.listReservations("client-123", "Reserved", 1, 4);

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

    var record = service.getReservation("res-1", "client-123");

    assertThat(record.id()).isEqualTo("res-1");
    assertThat(record.status()).isEqualTo("Reserved");
  }

  @Test
  void throwsNotFoundWhenReservationDoesNotExist() {
    when(repository.findByIdAndClient("res-unknown", "client-123")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getReservation("res-unknown", "client-123"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reservation not found");
  }

  @Test
  void cancelsUpcomingReservationSuccessfully() {
    ReservationEntity entity = createEntity("res-1", "Reserved", true);
    when(repository.findByIdAndClient("res-1", "client-123")).thenReturn(Optional.of(entity));
    when(repository.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var updated = service.cancelReservation("res-1", "client-123");

    assertThat(updated.status()).isEqualTo("Cancelled");
    assertThat(updated.canCancel()).isFalse();
    verify(repository).save(entity);
    verify(reservedSlots).releaseFor("res-1");
  }

  @Test
  void throwsBadRequestWhenCancellingAlreadyCancelledOrCompletedReservation() {
    ReservationEntity completed = createEntity("res-2", "Completed", false);
    when(repository.findByIdAndClient("res-2", "client-123")).thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.cancelReservation("res-2", "client-123"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Reservation cannot be cancelled in status Completed");
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
