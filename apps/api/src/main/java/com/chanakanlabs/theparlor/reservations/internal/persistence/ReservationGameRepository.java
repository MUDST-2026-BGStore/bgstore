package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGame;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGameId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationGameRepository
    extends JpaRepository<ReservationGame, ReservationGameId> {
  List<ReservationGame> findByIdReservationIdIn(Collection<UUID> reservationIds);

  @Modifying
  @Query("delete from ReservationGame g where g.id.reservationId = :reservationId")
  void deleteOf(@Param("reservationId") UUID reservationId);
}
