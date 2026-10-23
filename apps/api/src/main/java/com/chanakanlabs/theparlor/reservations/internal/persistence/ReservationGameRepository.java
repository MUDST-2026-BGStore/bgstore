package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGame;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationGameId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationGameRepository
    extends JpaRepository<ReservationGame, ReservationGameId> {}
