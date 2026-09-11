package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.contract.api.ReservationsApi;
import com.chanakanlabs.bgstore.contract.model.ReservationListResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationStatus;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReservationController implements ReservationsApi {

  private final ReservationService reservations;
  private final CurrentIdentityProvider identityProvider;

  public ReservationController(
      ReservationService reservations, CurrentIdentityProvider identityProvider) {
    this.reservations = reservations;
    this.identityProvider = identityProvider;
  }

  @Override
  public ResponseEntity<ReservationListResponse> listReservations(
      @Nullable ReservationStatus status, Integer page, Integer pageSize) {
    String clientSubject = identityProvider.currentIdentity().subject();
    String statusString = status != null ? status.getValue() : null;
    int pageNum = page != null ? page : 1;
    int size = pageSize != null ? pageSize : 4;

    var result = reservations.listReservations(clientSubject, statusString, pageNum, size);
    List<ReservationResponse> items =
        result.items().stream().map(ReservationController::toResponse).toList();
    ReservationListResponse response =
        new ReservationListResponse(
            items, result.total(), result.page(), result.pageSize(), result.totalPages());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<ReservationResponse> getReservation(String reservationId) {
    String clientSubject = identityProvider.currentIdentity().subject();
    return ResponseEntity.ok(toResponse(reservations.getReservation(reservationId, clientSubject)));
  }

  @Override
  public ResponseEntity<ReservationResponse> cancelReservation(String reservationId) {
    String clientSubject = identityProvider.currentIdentity().subject();
    return ResponseEntity.ok(
        toResponse(reservations.cancelReservation(reservationId, clientSubject)));
  }

  private static ReservationResponse toResponse(ReservationRecordData data) {
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
    return res;
  }
}
