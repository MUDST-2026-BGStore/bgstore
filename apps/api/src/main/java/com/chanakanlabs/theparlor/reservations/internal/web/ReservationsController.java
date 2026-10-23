package com.chanakanlabs.theparlor.reservations.internal.web;

import com.chanakanlabs.theparlor.contracts.api.ReservationsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationsController implements ReservationsApi {
  @Override
  public Reservation cancelReservation(
      UUID branchId, UUID reservationId, ReasonRequest reasonRequest) {
    return null;
  }

  @Override
  public CheckInResult checkInReservation(UUID branchId, UUID reservationId) {
    return null;
  }

  @Override
  public Reservation createMyReservation(
      UUID branchId, UUID idempotencyKey, CreateReservationRequest createReservationRequest) {
    return null;
  }

  @Override
  public Reservation createStaffReservation(
      UUID branchId,
      UUID idempotencyKey,
      CreateStaffReservationRequest createStaffReservationRequest) {
    return null;
  }

  @Override
  public Reservation getReservation(UUID branchId, UUID reservationId) {
    return null;
  }

  @Override
  public ReservationPage listBranchReservations(
      UUID branchId,
      Integer limit,
      @Nullable String cursor,
      @Nullable ReservationStatus status,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime until) {
    return null;
  }

  @Override
  public ReservationPage listMyReservations(
      Integer limit, @Nullable String cursor, @Nullable ReservationStatus status) {
    return null;
  }

  @Override
  public Reservation markReservationNoShow(
      UUID branchId, UUID reservationId, ReasonRequest reasonRequest) {
    return null;
  }

  @Override
  public Reservation rescheduleReservation(
      UUID branchId,
      UUID reservationId,
      RescheduleReservationRequest rescheduleReservationRequest) {
    return null;
  }
}
