package com.chanakanlabs.theparlor.reservations.internal.web;

import com.chanakanlabs.theparlor.contracts.api.ReservationsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.reservations.internal.application.ReservationsService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationsController implements ReservationsApi {
  private final ReservationsService reservations;

  public ReservationsController(ReservationsService reservations) {
    this.reservations = reservations;
  }

  @Override
  public Reservation cancelReservation(
      UUID branchId, UUID reservationId, ReasonRequest reasonRequest) {
    return reservations.cancel(branchId, reservationId, reasonRequest.getReason());
  }

  @Override
  public CheckInResult checkInReservation(UUID branchId, UUID reservationId) {
    return reservations.checkIn(branchId, reservationId);
  }

  @Override
  public Reservation createMyReservation(
      UUID branchId, UUID idempotencyKey, CreateReservationRequest createReservationRequest) {
    return reservations.createMine(branchId, idempotencyKey, createReservationRequest);
  }

  @Override
  public Reservation createStaffReservation(
      UUID branchId,
      UUID idempotencyKey,
      CreateStaffReservationRequest createStaffReservationRequest) {
    return reservations.createByStaff(branchId, idempotencyKey, createStaffReservationRequest);
  }

  @Override
  public Reservation getReservation(UUID branchId, UUID reservationId) {
    return reservations.get(branchId, reservationId);
  }

  @Override
  public ReservationPage listBranchReservations(
      UUID branchId,
      Integer limit,
      @Nullable String cursor,
      @Nullable ReservationStatus status,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime until) {
    return reservations.listBranch(branchId, limit, cursor, status, from, until);
  }

  @Override
  public ReservationPage listMyReservations(
      Integer limit, @Nullable String cursor, @Nullable ReservationStatus status) {
    return reservations.listMine(limit, cursor, status);
  }

  @Override
  public Reservation markReservationNoShow(
      UUID branchId, UUID reservationId, ReasonRequest reasonRequest) {
    return reservations.markNoShow(branchId, reservationId, reasonRequest.getReason());
  }

  @Override
  public Reservation rescheduleReservation(
      UUID branchId,
      UUID reservationId,
      RescheduleReservationRequest rescheduleReservationRequest) {
    return reservations.reschedule(branchId, reservationId, rescheduleReservationRequest);
  }
}
