package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.ReservationStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

  @Mock private ReservationService service;
  @Mock private FloorOverviewService floor;
  @Mock private StaffReservationService staffReservations;

  private ReservationController controller;

  private static final ReservationRecordData SAMPLE_RECORD =
      new ReservationRecordData(
          "res-1",
          "client-123",
          "Catan Evening",
          "13/09/2024",
          "18:00 - 20:00",
          4,
          5L,
          "Table 5",
          4,
          20,
          "Reserved",
          "John Doe",
          "0123456789",
          "-",
          "-",
          0,
          40,
          true,
          "/images/table-sample.png",
          null,
          OffsetDateTime.now(ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    controller = new ReservationController(service, floor, staffReservations);
  }

  @Test
  void listsReservationsForCurrentClient() {
    when(service.listReservations("Reserved", 1, 4))
        .thenReturn(new ReservationService.PageResult<>(List.of(SAMPLE_RECORD), 1, 1, 4, 1));

    var response = controller.listReservations(ReservationStatus.RESERVED, 1, 4);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getItems()).hasSize(1);
    assertThat(response.getBody().getItems().getFirst().getTitle()).isEqualTo("Catan Evening");
    assertThat(response.getBody().getItems().getFirst().getStatus())
        .isEqualTo(ReservationStatus.RESERVED);
  }

  @Test
  void getsReservationById() {
    when(service.getReservation("res-1")).thenReturn(SAMPLE_RECORD);

    var response = controller.getReservation("res-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo("res-1");
  }

  @Test
  void cancelsReservation() {
    var cancelledRecord =
        new ReservationRecordData(
            "res-1",
            "client-123",
            "Catan Evening",
            "13/09/2024",
            "18:00 - 20:00",
            4,
            5L,
            "Table 5",
            4,
            20,
            "Cancelled",
            "John Doe",
            "0123456789",
            "-",
            "-",
            0,
            40,
            false,
            "/images/table-sample.png",
            null,
            OffsetDateTime.now(ZoneOffset.UTC));

    when(service.cancelReservation("res-1")).thenReturn(cancelledRecord);

    var response = controller.cancelReservation("res-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    assertThat(response.getBody().getCanCancel()).isFalse();
    verify(service).cancelReservation("res-1");
  }
}
