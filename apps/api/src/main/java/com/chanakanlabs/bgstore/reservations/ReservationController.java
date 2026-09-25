package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.contract.api.ReservationsApi;
import com.chanakanlabs.bgstore.contract.model.CreateReservationRequest;
import com.chanakanlabs.bgstore.contract.model.FloorOverviewResponse;
import com.chanakanlabs.bgstore.contract.model.FloorStatusCounts;
import com.chanakanlabs.bgstore.contract.model.FloorTableResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationAvailabilityResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationListResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationStatus;
import com.chanakanlabs.bgstore.contract.model.TableShape;
import com.chanakanlabs.bgstore.contract.model.TableStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReservationController implements ReservationsApi {

  private final ReservationService reservations;
  private final FloorOverviewService floor;
  private final StaffReservationService staffReservations;

  public ReservationController(
      ReservationService reservations,
      FloorOverviewService floor,
      StaffReservationService staffReservations) {
    this.reservations = reservations;
    this.floor = floor;
    this.staffReservations = staffReservations;
  }

  @Override
  public ResponseEntity<ReservationResponse> createReservation(CreateReservationRequest request) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(toResponse(staffReservations.create(request)));
  }

  @Override
  public ResponseEntity<ReservationAvailabilityResponse> getReservationAvailability(
      String branch, LocalDate date, String startTime, String endTime, Integer partySize) {
    return ResponseEntity.ok(
        staffReservations.availability(branch, date, startTime, endTime, partySize));
  }

  @Override
  public ResponseEntity<ReservationListResponse> listReservations(
      @Nullable ReservationStatus status, Integer page, Integer pageSize) {
    String statusString = status != null ? status.getValue() : null;
    int pageNum = page != null ? page : 1;
    int size = pageSize != null ? pageSize : 4;

    var result = reservations.listReservations(statusString, pageNum, size);
    List<ReservationResponse> items =
        result.items().stream().map(ReservationController::toResponse).toList();
    ReservationListResponse response =
        new ReservationListResponse(
            items, result.total(), result.page(), result.pageSize(), result.totalPages());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<ReservationListResponse> listStaffReservations(
      @Nullable ReservationStatus status, @Nullable String branch, Integer page, Integer pageSize) {
    String statusString = status != null ? status.getValue() : null;
    int pageNum = page != null ? page : 1;
    int size = pageSize != null ? pageSize : 10;

    var result = staffReservations.staffReservations(statusString, branch, pageNum, size);
    List<ReservationResponse> items =
        result.items().stream().map(ReservationController::toResponse).toList();
    ReservationListResponse response =
        new ReservationListResponse(
            items, result.total(), result.page(), result.pageSize(), result.totalPages());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<ReservationResponse> getReservation(String reservationId) {
    return ResponseEntity.ok(toResponse(reservations.getReservation(reservationId)));
  }

  @Override
  public ResponseEntity<ReservationResponse> cancelReservation(String reservationId) {
    return ResponseEntity.ok(toResponse(reservations.cancelReservation(reservationId)));
  }

  @Override
  public ResponseEntity<FloorOverviewResponse> getFloorOverview(
      @Nullable String branch,
      @Nullable TableStatus status,
      @Nullable String search,
      Integer page,
      Integer pageSize) {
    var overview =
        floor.overview(branch, status == null ? null : status.getValue(), search, page, pageSize);
    return ResponseEntity.ok(
        new FloorOverviewResponse(
            new FloorStatusCounts(
                Math.toIntExact(overview.available()),
                Math.toIntExact(overview.occupied()),
                Math.toIntExact(overview.reserved())),
            overview.tables().items().stream().map(ReservationController::toFloorResponse).toList(),
            overview.tables().total(),
            overview.tables().page(),
            overview.tables().pageSize(),
            overview.tables().totalPages()));
  }

  static ReservationResponse toResponse(ReservationRecordData data) {
    var res =
        new ReservationResponse(
            data.id(),
            data.title(),
            data.date(),
            data.timeSlot(),
            data.partySize(),
            data.tableId(),
            data.tableName(),
            data.seats(),
            data.ratePerHour(),
            ReservationStatus.fromValue(data.status()),
            data.customerName(),
            data.phoneNumber(),
            data.checkInTime(),
            data.actualCheckOut(),
            data.overtimeMinutes(),
            data.totalPrice(),
            data.canCancel());
    if (data.thumbnailUrl() != null) {
      res.setThumbnailUrl(data.thumbnailUrl());
    }
    if (data.branchName() != null) {
      res.setBranchName(data.branchName());
    }
    return res;
  }

  private static FloorTableResponse toFloorResponse(FloorOverviewService.FloorTable row) {
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
