package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.contracts.model.Reservation;
import com.chanakanlabs.theparlor.reservations.Visits;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class VisitsService implements Visits {
  private final ReservationRepository reservations;
  private final ReservationViews views;

  VisitsService(ReservationRepository reservations, ReservationViews views) {
    this.reservations = reservations;
    this.views = views;
  }

  @Override
  public Map<UUID, Booked> booked(Collection<UUID> reservationIds) {
    if (reservationIds.isEmpty()) return Map.of();
    var found = reservations.findAllById(reservationIds);
    var games = views.gamesOf(found.stream().map(reservation -> reservation.getId()).toList());
    return found.stream()
        .collect(
            Collectors.toMap(
                reservation -> reservation.getId(),
                reservation ->
                    new Booked(
                        reservation.getId(),
                        reservation.getTableId(),
                        ReservationViews.customer(reservation),
                        reservation.getCustomerSubject(),
                        reservation.getTableHourlyRateMinor(),
                        games.getOrDefault(reservation.getId(), List.of()).stream()
                            .map(
                                game ->
                                    new GameLine(
                                        game.getId().gameId(),
                                        game.getQuantity(),
                                        game.getUnitHourlyRateMinor()))
                            .toList())));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Reservation complete(UUID reservationId, Instant at) {
    var reservation = reservations.lockById(reservationId).orElseThrow(VisitsService::missing);
    try {
      reservation.complete(at);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
    reservations.flush();
    return views.of(reservation);
  }

  @Override
  public Reservation get(UUID reservationId) {
    return views.of(reservations.findById(reservationId).orElseThrow(VisitsService::missing));
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
  }
}
