package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.contract.api.ReservationsApi;
import com.chanakanlabs.bgstore.contract.model.FloorOverviewResponse;
import com.chanakanlabs.bgstore.contract.model.FloorStatusCounts;
import com.chanakanlabs.bgstore.contract.model.FloorTableResponse;
import com.chanakanlabs.bgstore.contract.model.TableShape;
import com.chanakanlabs.bgstore.contract.model.TableStatus;
import com.chanakanlabs.bgstore.reservations.FloorOverviewService.FloorOverview;
import com.chanakanlabs.bgstore.reservations.FloorOverviewService.FloorTable;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FloorOverviewController implements ReservationsApi {

  private final FloorOverviewService floor;

  public FloorOverviewController(FloorOverviewService floor) {
    this.floor = floor;
  }

  @Override
  public ResponseEntity<FloorOverviewResponse> getFloorOverview(
      @Nullable String branch,
      @Nullable TableStatus status,
      @Nullable String search,
      Integer page,
      Integer pageSize) {
    FloorOverview overview =
        floor.overview(branch, status == null ? null : status.getValue(), search, page, pageSize);
    return ResponseEntity.ok(
        new FloorOverviewResponse(
            new FloorStatusCounts(
                Math.toIntExact(overview.available()),
                Math.toIntExact(overview.occupied()),
                Math.toIntExact(overview.reserved())),
            overview.tables().items().stream().map(FloorOverviewController::toResponse).toList(),
            overview.tables().total(),
            overview.tables().page(),
            overview.tables().pageSize(),
            overview.tables().totalPages()));
  }

  private static FloorTableResponse toResponse(FloorTable row) {
    var table = row.table();
    return new FloorTableResponse(
        Objects.requireNonNull(table.id(), "Table id must not be null"),
        table.name(),
        table.capacity(),
        TableShape.fromValue(table.shape()),
        TableStatus.fromValue(table.status()),
        row.reservedSlots().stream()
            .map(
                slot ->
                    new com.chanakanlabs.bgstore.contract.model.ReservedSlot(
                        slot.startsAt(), slot.endsAt()))
            .toList());
  }
}
