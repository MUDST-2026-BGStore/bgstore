package com.chanakanlabs.bgstore.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.tables.TableManagementService;
import com.chanakanlabs.bgstore.tables.TableManagementService.PageResult;
import com.chanakanlabs.bgstore.tables.TableRecordData;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FloorOverviewServiceTest {

  /** 10:00 on 8 Sep 2026 in Bangkok. */
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T10:00:00+07:00");

  @Mock private TableManagementService tables;
  @Mock private AccessPolicy accessPolicy;

  private InMemoryReservedSlots slots;
  private FloorOverviewService service;

  @BeforeEach
  void setUp() {
    slots = new InMemoryReservedSlots();
    service =
        new FloorOverviewService(
            tables, slots, accessPolicy, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
  }

  @Test
  void countsTheWholeFloorWhateverTheRowFilters() {
    when(tables.listActiveTables("Silom", "Reserved", "Table", 2, 5))
        .thenReturn(new PageResult<>(List.of(table(6, "Reserved")), 6, 2, 5, 2));
    when(tables.countActiveByStatus("Silom"))
        .thenReturn(Map.of("Available", 4L, "Occupied", 1L, "Reserved", 2L, "Unavailable", 1L));

    var overview = service.overview("Silom", "Reserved", "Table", 2, 5);

    assertThat(overview.available()).isEqualTo(4);
    assertThat(overview.occupied()).isEqualTo(1);
    assertThat(overview.reserved()).isEqualTo(2);
    assertThat(overview.tables().total()).isEqualTo(6);
    assertThat(overview.tables().page()).isEqualTo(2);
    assertThat(overview.tables().pageSize()).isEqualTo(5);
    assertThat(overview.tables().totalPages()).isEqualTo(2);
  }

  @Test
  void countsAStatusNoTableIsInAsZero() {
    when(tables.listActiveTables(null, null, null, 1, 5))
        .thenReturn(new PageResult<>(List.of(), 0, 1, 5, 1));
    when(tables.countActiveByStatus(null)).thenReturn(Map.of("Available", 3L));

    var overview = service.overview(null, null, null, 1, 5);

    assertThat(overview.available()).isEqualTo(3);
    assertThat(overview.occupied()).isZero();
    assertThat(overview.reserved()).isZero();
  }

  @Test
  void attachesEachTablesUpcomingSlots() {
    var lunch = slot("2026-09-08T12:00:00+07:00", "2026-09-08T13:00:00+07:00");
    var afternoon = slot("2026-09-08T14:00:00+07:00", "2026-09-08T16:00:00+07:00");
    slots.add(2L, lunch);
    slots.add(2L, afternoon);
    when(tables.listActiveTables(null, null, null, 1, 5))
        .thenReturn(
            new PageResult<>(List.of(table(1, "Available"), table(2, "Occupied")), 2, 1, 5, 1));
    when(tables.countActiveByStatus(null)).thenReturn(Map.of());

    var rows = service.overview(null, null, null, 1, 5).tables().items();

    assertThat(rows).extracting(row -> row.table().id()).containsExactly(1L, 2L);
    assertThat(rows.get(0).reservedSlots()).isEmpty();
    assertThat(rows.get(1).reservedSlots()).containsExactly(lunch, afternoon);
  }

  @Test
  void leavesOutSlotsThatHaveAlreadyEnded() {
    var breakfast = slot("2026-09-08T08:00:00+07:00", "2026-09-08T09:30:00+07:00");
    var underway = slot("2026-09-08T09:30:00+07:00", "2026-09-08T11:00:00+07:00");
    slots.add(3L, breakfast);
    slots.add(3L, underway);
    when(tables.listActiveTables(null, null, null, 1, 5))
        .thenReturn(new PageResult<>(List.of(table(3, "Occupied")), 1, 1, 5, 1));
    when(tables.countActiveByStatus(null)).thenReturn(Map.of());

    var rows = service.overview(null, null, null, 1, 5).tables().items();

    assertThat(rows.getFirst().reservedSlots()).containsExactly(underway);
  }

  @Test
  void isOnlyForStaff() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"))
        .when(accessPolicy)
        .requireStaffOrManager();

    assertThatThrownBy(() -> service.overview(null, null, null, 1, 5))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(tables);
  }

  private static TableRecordData table(long id, String status) {
    return new TableRecordData(
        id, "Table " + id, "Silom", 4, "Round", status, true, "Main Hall", NOW);
  }

  private static ReservedSlot slot(String startsAt, String endsAt) {
    return new ReservedSlot(OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt));
  }
}
