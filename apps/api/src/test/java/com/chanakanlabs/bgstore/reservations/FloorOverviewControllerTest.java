package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.TableShape;
import com.chanakanlabs.bgstore.contract.model.TableStatus;
import com.chanakanlabs.bgstore.reservations.FloorOverviewService.FloorOverview;
import com.chanakanlabs.bgstore.reservations.FloorOverviewService.FloorTable;
import com.chanakanlabs.bgstore.tables.TableManagementService.PageResult;
import com.chanakanlabs.bgstore.tables.TableRecordData;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FloorOverviewControllerTest {

  @Mock private FloorOverviewService service;
  @Mock private ReservationService reservationService;
  @Mock private StaffReservationService staffReservations;
  private ReservationController controller;

  @BeforeEach
  void setUp() {
    controller = new ReservationController(reservationService, service, staffReservations);
  }

  @Test
  void answersWithTheCountsAndTheTablePage() {
    var startsAt = OffsetDateTime.parse("2026-09-08T12:00:00+07:00");
    var endsAt = OffsetDateTime.parse("2026-09-08T13:00:00+07:00");
    var table =
        new TableRecordData(
            2L, "Table 2", "Silom", 6, "Square", "Occupied", true, "Main Hall", startsAt);
    when(service.overview("Silom", "Occupied", "2", 1, 5))
        .thenReturn(
            new FloorOverview(
                100,
                10,
                10,
                new PageResult<>(
                    List.of(new FloorTable(table, List.of(new ReservedSlot(startsAt, endsAt)))),
                    42,
                    1,
                    5,
                    9)));

    var response = controller.getFloorOverview("Silom", TableStatus.OCCUPIED, "2", 1, 5);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCounts().getAvailable()).isEqualTo(100);
    assertThat(body.getCounts().getOccupied()).isEqualTo(10);
    assertThat(body.getCounts().getReserved()).isEqualTo(10);
    assertThat(body.getTotal()).isEqualTo(42);
    assertThat(body.getTotalPages()).isEqualTo(9);
    var row = body.getItems().getFirst();
    assertThat(row.getId()).isEqualTo(2L);
    assertThat(row.getName()).isEqualTo("Table 2");
    assertThat(row.getCapacity()).isEqualTo(6);
    assertThat(row.getShape()).isEqualTo(TableShape.SQUARE);
    assertThat(row.getStatus()).isEqualTo(TableStatus.OCCUPIED);
    assertThat(row.getReservedSlots().getFirst().getStartsAt()).isEqualTo(startsAt);
    assertThat(row.getReservedSlots().getFirst().getEndsAt()).isEqualTo(endsAt);
  }

  @Test
  void leavesUnsetFiltersForTheServiceToIgnore() {
    when(service.overview(null, null, null, 1, 20))
        .thenReturn(new FloorOverview(0, 0, 0, new PageResult<>(List.of(), 0, 1, 20, 1)));

    var response = controller.getFloorOverview(null, null, null, 1, 20);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getItems()).isEmpty();
  }
}
