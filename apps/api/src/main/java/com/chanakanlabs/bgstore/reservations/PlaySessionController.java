package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.contract.api.PlaySessionsApi;
import com.chanakanlabs.bgstore.contract.model.ActiveSessionResponse;
import com.chanakanlabs.bgstore.contract.model.CheckOutRequest;
import com.chanakanlabs.bgstore.contract.model.CheckoutReceiptResponse;
import com.chanakanlabs.bgstore.contract.model.PaymentMethod;
import com.chanakanlabs.bgstore.contract.model.ReservationResponse;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceKind;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceRequest;
import com.chanakanlabs.bgstore.contract.model.SessionAssistanceResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Thin transport for the client's active session and the staff check-in/check-out actions. */
@RestController
@RequestMapping("/api/v1")
public class PlaySessionController implements PlaySessionsApi {

  private final PlaySessionService sessions;

  public PlaySessionController(PlaySessionService sessions) {
    this.sessions = sessions;
  }

  @Override
  public ResponseEntity<ActiveSessionResponse> getActiveSession() {
    return ResponseEntity.ok(sessions.activeSession());
  }

  @Override
  public ResponseEntity<ReservationResponse> checkInReservation(String reservationId) {
    return ResponseEntity.ok(ReservationController.toResponse(sessions.checkIn(reservationId)));
  }

  @Override
  public ResponseEntity<CheckoutReceiptResponse> checkOutReservation(
      String reservationId, CheckOutRequest checkOutRequest) {
    Integer finalAmount = checkOutRequest.getFinalAmount();
    PaymentMethod paymentMethod = checkOutRequest.getPaymentMethod();
    if (finalAmount == null || paymentMethod == null) {
      throw badRequest("finalAmount and paymentMethod are required.");
    }
    return ResponseEntity.ok(sessions.checkOut(reservationId, finalAmount, paymentMethod));
  }

  @Override
  public ResponseEntity<SessionAssistanceResponse> requestSessionAssistance(
      String reservationId, SessionAssistanceRequest sessionAssistanceRequest) {
    SessionAssistanceKind kind = sessionAssistanceRequest.getKind();
    UUID requestId = sessionAssistanceRequest.getRequestId();
    if (kind == null || requestId == null) {
      throw badRequest("kind and requestId are required.");
    }
    return ResponseEntity.ok(sessions.requestAssistance(reservationId, requestId, kind));
  }

  private static ResponseStatusException badRequest(@Nullable String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
