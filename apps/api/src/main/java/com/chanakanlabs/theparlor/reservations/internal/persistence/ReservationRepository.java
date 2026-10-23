package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.Reservation;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

  @Query(
      "select (count(r) > 0) from Reservation r, ReservationGame g where g.id.reservationId = r.id and g.id.gameId = :gameId and r.status = com.chanakanlabs.theparlor.reservations.internal.domain.ReservationStatus.CONFIRMED")
  boolean hasConfirmedGame(@Param("gameId") UUID gameId);

  @Query("select g.id.reservationId from ReservationGame g where g.id.gameId = :gameId")
  Set<UUID> reservationsUsingGame(@Param("gameId") UUID gameId);
}
