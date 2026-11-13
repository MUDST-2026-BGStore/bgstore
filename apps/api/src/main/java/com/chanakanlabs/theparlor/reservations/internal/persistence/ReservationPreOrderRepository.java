package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrder;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPreOrderRepository extends JpaRepository<ReservationPreOrder, UUID> {
  List<ReservationPreOrder> findByReservationIdIn(Collection<UUID> reservationIds);
}
