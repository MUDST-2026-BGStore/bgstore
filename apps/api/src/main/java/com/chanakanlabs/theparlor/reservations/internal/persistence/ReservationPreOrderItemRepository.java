package com.chanakanlabs.theparlor.reservations.internal.persistence;

import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrderItem;
import com.chanakanlabs.theparlor.reservations.internal.domain.ReservationPreOrderItemId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPreOrderItemRepository
    extends JpaRepository<ReservationPreOrderItem, ReservationPreOrderItemId> {
  List<ReservationPreOrderItem> findByIdReservationIdIn(Collection<UUID> reservationIds);
}
